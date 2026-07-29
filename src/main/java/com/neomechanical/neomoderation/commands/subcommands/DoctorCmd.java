package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationAction;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.moderation.CloudRecovery;
import com.neomechanical.neomoderation.moderation.ModerationApiResult;
import com.neomechanical.neomoderation.moderation.NeoMechanicalUsageClient;
import com.neomechanical.neomoderation.moderation.UsageSummary;
import org.bukkit.command.CommandSender;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * One-command setup diagnostics: configuration sanity, action wiring, cloud
 * credentials, connectivity, latency, quota, and circuit state.
 */
public class DoctorCmd implements SubCommand {
    private static final long CHAT_WAIT_CAP_MS = 2500L;

    private final NeoModerationPlugin plugin;
    private final NeoMechanicalUsageClient usageClient = new NeoMechanicalUsageClient();

    public DoctorCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "doctor";
    }

    @Override
    public String getDescription() {
        return "Diagnose configuration, account API, and event health.";
    }

    @Override
    public String getUsage() {
        return "/nmod doctor";
    }

    @Override
    public List<String> getAliases() {
        return List.of("diagnose");
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        ModerationSettings settings = plugin.settings();
        plugin.messages().send(sender, "doctor.title");

        if (settings.enabled()) {
            pass(sender, "Moderation", "enabled");
        } else {
            fail(sender, "Moderation", "disabled - run /nmod on");
        }

        if (settings.mode() == ModerationMode.MONITOR) {
            warn(sender, "Mode", "monitor - detections are observed but never blocked/punished");
        } else {
            pass(sender, "Mode", "enforce");
        }

        if (settings.offline().enabled()) {
            pass(sender, "Local rules", settings.offline().bannedWords().size() + " words, "
                    + settings.offline().bannedUrls().size() + " links, "
                    + (settings.offline().allowedWords().size() + settings.offline().allowedUrls().size())
                    + " exceptions");
        } else if (settings.api().apiKey().isBlank()) {
            fail(sender, "Local rules", "disabled and no API key - nothing is being moderated");
        } else {
            warn(sender, "Local rules", "disabled - only cloud moderation runs");
        }

        if (settings.actions().isEmpty()) {
            warn(sender, "Actions", "none - flagged chat is blocked without punishment");
        } else {
            pass(sender, "Actions", ModerationAction.describe(settings.actions()));
        }

        pass(sender, "Anti-spam", settings.spam().enabled()
                ? settings.spam().messagesPer10s() + " msgs/10s, dup x" + settings.spam().duplicateLimit()
                        + ", caps " + settings.spam().capsPercent() + "%"
                : "off");
        pass(sender, "Strikes", settings.strikes().enabled()
                ? settings.strikes().escalation().size() + " rung(s), decay "
                        + settings.strikes().decayMinutes() + "m"
                : "off");
        pass(sender, "Surfaces", settings.surfaces().enabledCount()
                + " of 4 active (local rules only; cloud never blocks sync events)");
        if (settings.cases().enabled() && !plugin.caseLog().isAvailable()) {
            warn(sender, "Case history", "enabled but SQLite driver missing - not logging");
        } else {
            pass(sender, "Case history", settings.cases().enabled()
                    ? "on (" + (settings.cases().storeContent() ? "with previews" : "metadata only") + ")"
                    : "off");
        }

        pass(sender, "Fail policy", settings.failOpen()
                ? "fail-open (chat passes if the cloud is unreachable)"
                : "fail-closed (chat blocks if the cloud is unreachable)");

        long budget = (long) settings.api().connectTimeoutMs() + settings.api().readTimeoutMs() + 400L;
        if (budget > CHAT_WAIT_CAP_MS) {
            warn(sender, "Timeouts", "connect+read exceed the 2.5s chat wait cap; the cap wins");
        } else {
            pass(sender, "Timeouts", settings.api().connectTimeoutMs() + "ms connect, "
                    + settings.api().readTimeoutMs() + "ms read");
        }

        if (settings.api().apiKey().isBlank()) {
            warn(sender, "Cloud", "no API key - local rules only. Sign up at "
                    + CloudRecovery.SIGNUP_URL + ", create a key, then run /nmod setup <key>");
            return;
        }

        if (!isHttpUri(settings.api().endpoint())) {
            fail(sender, "Endpoint", "not a valid http(s) URL: " + settings.api().endpoint());
            return;
        }
        pass(sender, "Endpoint", settings.api().endpoint());

        if (plugin.coordinator().isRemoteCallAllowed()) {
            pass(sender, "Moderation circuit", "closed (event calls allowed; last result is below)");
        } else {
            warn(sender, "Moderation circuit", "open - event calls paused after errors; /nmod reload resumes early");
        }
        renderModerationEventHealth(sender, plugin.coordinator().lastCloudResultKind());

        plugin.messages().send(sender, "doctor.checking-cloud");
        plugin.runAsync(() -> {
            long start = System.nanoTime();
            try {
                UsageSummary usage = usageClient.fetchUsage(settings.api());
                long ms = (System.nanoTime() - start) / 1_000_000L;
                boolean hasCredits = usage.creditsRemaining() > 0;
                plugin.runSync(() -> {
                    pass(sender, "Account API",
                            "OK in " + ms + "ms - workspace " + usage.workspace()
                                    + ", plan " + usage.tier());
                    if (hasCredits) {
                        pass(sender, "Account credits", usage.creditsRemaining() + " remaining");
                    } else {
                        fail(sender, "Account credits", "0 remaining - add credits at "
                                + CloudRecovery.BILLING_URL + ", then run /nmod test hello");
                    }
                });
            } catch (NeoMechanicalUsageClient.UsageException e) {
                long ms = (System.nanoTime() - start) / 1_000_000L;
                plugin.runSync(() -> fail(sender, failureCheck(e.kind()),
                        failureDetail(e, ms)));
            }
        });
    }

    private void renderModerationEventHealth(CommandSender sender, ModerationApiResult.Kind kind) {
        if (kind == null) {
            warn(sender, "Moderation events", "unverified - run /nmod test hello");
            return;
        }
        switch (kind) {
            case FLAGGED, CLEAR -> pass(sender, "Moderation events", "last request succeeded");
            case CLIENT_AUTH -> fail(sender, "Moderation events", "last request rejected the API key");
            case INSUFFICIENT_CREDITS -> fail(sender, "Moderation events", "last request had no credits");
            case CLIENT_REQUEST -> fail(sender, "Moderation events", "last request was rejected; check the endpoint");
            case TRANSIENT_TRANSPORT -> warn(sender, "Moderation events", "last request hit a network/server error");
        }
    }

    static String failureCheck(ModerationApiResult.Kind kind) {
        return switch (kind) {
            case CLIENT_AUTH -> "Account authentication";
            case INSUFFICIENT_CREDITS -> "Account credits";
            case CLIENT_REQUEST -> "Account request";
            case TRANSIENT_TRANSPORT, FLAGGED, CLEAR -> "Account API";
        };
    }

    static String failureDetail(NeoMechanicalUsageClient.UsageException error, long elapsedMs) {
        String timing = "failed in " + elapsedMs + "ms (" + error.getMessage() + ")";
        return switch (error.kind()) {
            case CLIENT_AUTH -> timing + " - API key rejected; replace it at "
                    + CloudRecovery.API_KEYS_URL + ", then run /nmod setup <key>";
            case INSUFFICIENT_CREDITS -> timing + " - insufficient credits; add credits at "
                    + CloudRecovery.BILLING_URL + ", then run /nmod test hello";
            case CLIENT_REQUEST -> timing
                    + " - request rejected without rejecting the API key; verify moderation.api.endpoint";
            case TRANSIENT_TRANSPORT, FLAGGED, CLEAR -> timing
                    + " - temporary network or server error; local rules remain active";
        };
    }

    private static boolean isHttpUri(String endpoint) {
        try {
            String scheme = URI.create(endpoint).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void pass(CommandSender sender, String check, String detail) {
        plugin.messages().send(sender, "doctor.pass", Map.of("check", check, "detail", detail));
    }

    private void warn(CommandSender sender, String check, String detail) {
        plugin.messages().send(sender, "doctor.warn", Map.of("check", check, "detail", detail));
    }

    private void fail(CommandSender sender, String check, String detail) {
        plugin.messages().send(sender, "doctor.fail", Map.of("check", check, "detail", detail));
    }
}

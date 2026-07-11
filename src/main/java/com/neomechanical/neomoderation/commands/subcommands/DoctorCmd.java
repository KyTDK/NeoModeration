package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.moderation.DetectionNotifier;
import com.neomechanical.neomoderation.moderation.NeoMechanicalUsageClient;
import com.neomechanical.neomoderation.moderation.UsageSummary;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.URI;
import java.util.Collections;
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
        return "Diagnose configuration and cloud connectivity.";
    }

    @Override
    public String getUsage() {
        return "/nmod doctor";
    }

    @Override
    public String getPermission() {
        return "neomoderation.admin";
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
            pass(sender, "Actions", DetectionNotifier.describeActions(settings.actions()));
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
            warn(sender, "Cloud", "no API key - local rules only. Get one at platform.neomechanical.com, then /nmod setup <key>");
            return;
        }

        if (!isHttpUri(settings.api().endpoint())) {
            fail(sender, "Endpoint", "not a valid http(s) URL: " + settings.api().endpoint());
            return;
        }
        pass(sender, "Endpoint", settings.api().endpoint());

        if (plugin.coordinator().isRemoteCallAllowed()) {
            pass(sender, "Circuit", "closed (cloud calls flowing)");
        } else {
            warn(sender, "Circuit", "open - cloud paused after errors; /nmod reload resumes early");
        }

        plugin.messages().send(sender, "doctor.checking-cloud");
        new BukkitRunnable() {
            @Override
            public void run() {
                long start = System.nanoTime();
                try {
                    UsageSummary usage = usageClient.fetchUsage(settings.api());
                    long ms = (System.nanoTime() - start) / 1_000_000L;
                    runSync(() -> pass(sender, "Cloud connectivity",
                            "OK in " + ms + "ms - workspace " + usage.workspace()
                                    + ", plan " + usage.tier()
                                    + ", " + usage.creditsRemaining() + " credits left"));
                } catch (NeoMechanicalUsageClient.UsageException e) {
                    long ms = (System.nanoTime() - start) / 1_000_000L;
                    runSync(() -> fail(sender, "Cloud connectivity",
                            "failed in " + ms + "ms (" + e.getMessage() + ") - check the key and endpoint"));
                }
            }
        }.runTaskAsynchronously(plugin);
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

    private void runSync(Runnable runnable) {
        new BukkitRunnable() {
            @Override
            public void run() {
                runnable.run();
            }
        }.runTask(plugin);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}

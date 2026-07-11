package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationAction;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.moderation.ModerationApiResult;
import com.neomechanical.neomoderation.moderation.OfflineModerationEngine;
import com.neomechanical.neomoderation.moderation.OfflineModerationResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Dry-runs the full moderation pipeline on a sample message and explains every
 * decision. Never executes actions. A cloud check consumes one API request.
 */
public class TestCmd implements SubCommand {
    private static final String CONSOLE_UUID = "00000000-0000-0000-0000-000000000000";

    enum Outcome {
        ALLOWED,
        MONITORED,
        ENFORCED
    }

    private final NeoModerationPlugin plugin;

    public TestCmd(NeoModerationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getDescription() {
        return "Preview how a message would be moderated.";
    }

    @Override
    public String getUsage() {
        return "/nmod test <message>";
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "test.usage", Map.of("label", label));
            return;
        }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        ModerationSettings settings = plugin.settings();

        plugin.messages().send(sender, "test.title", Map.of("message", message));

        if (!settings.enabled()) {
            plugin.messages().send(sender, "test.disabled");
            sendOutcome(sender, settings, false);
            return;
        }

        OfflineModerationResult local = OfflineModerationEngine.evaluate(message, settings.offline());
        if (local.flagged()) {
            plugin.messages().send(sender, "test.local-flagged", Map.of("reason", local.reason()));
        } else {
            plugin.messages().send(sender, "test.local-clear");
        }

        if (!shouldCheckCloud(local.flagged())) {
            plugin.messages().send(sender, "test.cloud-skipped-local");
            sendOutcome(sender, settings, true);
            return;
        }

        if (settings.api().apiKey().isBlank()) {
            plugin.messages().send(sender, "test.cloud-skipped-key");
            sendOutcome(sender, settings, false);
            return;
        }
        if (!plugin.coordinator().isRemoteCallAllowed()) {
            plugin.messages().send(sender, "test.cloud-skipped-circuit");
            sendOutcome(sender, settings, !settings.failOpen());
            return;
        }

        plugin.messages().send(sender, "test.cloud-checking");
        String senderName = sender.getName();
        String senderUuid = sender instanceof Player player ? player.getUniqueId().toString() : CONSOLE_UUID;
        plugin.runAsync(() -> {
            long start = System.nanoTime();
            ModerationApiResult result = plugin.apiClient().moderateText(
                    senderName, senderUuid, message, settings.api(), settings.categories());
            String ms = String.valueOf((System.nanoTime() - start) / 1_000_000L);
            plugin.runSync(() -> {
                switch (result.kind()) {
                    case FLAGGED -> plugin.messages().send(sender, "test.cloud-flagged", Map.of("ms", ms));
                    case CLEAR -> plugin.messages().send(sender, "test.cloud-clear", Map.of("ms", ms));
                    default -> plugin.messages().send(sender, "test.cloud-error", Map.of(
                            "ms", ms,
                            "detail", result.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                    ));
                }
                sendOutcome(sender, settings, cloudDetected(result, settings.failOpen()));
                plugin.messages().send(sender, "test.note");
            });
        });
    }

    private void sendOutcome(CommandSender sender, ModerationSettings settings, boolean detected) {
        switch (outcome(settings.enabled(), detected, settings.mode())) {
            case ALLOWED -> plugin.messages().send(sender, "test.would-allowed");
            case MONITORED -> plugin.messages().send(sender, "test.would-monitor");
            case ENFORCED -> plugin.messages().send(sender, "test.would-enforce", Map.of(
                    "actions", ModerationAction.describe(settings.actions())
            ));
        }
    }

    static boolean shouldCheckCloud(boolean locallyFlagged) {
        return !locallyFlagged;
    }

    static boolean cloudDetected(ModerationApiResult result, boolean failOpen) {
        return result.isFlagged()
                || (result.kind() != ModerationApiResult.Kind.CLEAR && !failOpen);
    }

    static Outcome outcome(boolean enabled, boolean detected, ModerationMode mode) {
        if (!enabled || !detected) {
            return Outcome.ALLOWED;
        }
        return mode == ModerationMode.MONITOR ? Outcome.MONITORED : Outcome.ENFORCED;
    }
}

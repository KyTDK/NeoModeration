package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.SubCommand;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import com.neomechanical.neomoderation.moderation.DetectionNotifier;
import com.neomechanical.neomoderation.moderation.ModerationApiResult;
import com.neomechanical.neomoderation.moderation.OfflineModerationEngine;
import com.neomechanical.neomoderation.moderation.OfflineModerationResult;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dry-runs the full moderation pipeline on a sample message and explains every
 * decision. Never executes actions. A cloud check consumes one API request.
 */
public class TestCmd implements SubCommand {
    private static final String CONSOLE_UUID = "00000000-0000-0000-0000-000000000000";

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
    public String getPermission() {
        return "neomoderation.admin";
    }

    @Override
    public List<String> getAliases() {
        return Collections.emptyList();
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

        OfflineModerationResult local = OfflineModerationEngine.evaluate(message, settings.offline());
        if (local.flagged()) {
            plugin.messages().send(sender, "test.local-flagged", Map.of("reason", local.reason()));
        } else {
            plugin.messages().send(sender, "test.local-clear");
        }

        if (settings.api().apiKey().isBlank()) {
            plugin.messages().send(sender, "test.cloud-skipped-key");
            sendOutcome(sender, settings);
            return;
        }
        if (!plugin.coordinator().isRemoteCallAllowed()) {
            plugin.messages().send(sender, "test.cloud-skipped-circuit");
            sendOutcome(sender, settings);
            return;
        }

        plugin.messages().send(sender, "test.cloud-checking");
        String senderName = sender.getName();
        String senderUuid = sender instanceof Player player ? player.getUniqueId().toString() : CONSOLE_UUID;
        new BukkitRunnable() {
            @Override
            public void run() {
                long start = System.nanoTime();
                ModerationApiResult result = plugin.apiClient().moderateText(
                        senderName, senderUuid, message, settings.api(), settings.categories());
                String ms = String.valueOf((System.nanoTime() - start) / 1_000_000L);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        switch (result.kind()) {
                            case FLAGGED -> plugin.messages().send(sender, "test.cloud-flagged", Map.of("ms", ms));
                            case CLEAR -> plugin.messages().send(sender, "test.cloud-clear", Map.of("ms", ms));
                            default -> plugin.messages().send(sender, "test.cloud-error", Map.of(
                                    "ms", ms,
                                    "detail", result.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                            ));
                        }
                        sendOutcome(sender, settings);
                        plugin.messages().send(sender, "test.note");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void sendOutcome(CommandSender sender, ModerationSettings settings) {
        String outcome = settings.mode() == ModerationMode.MONITOR
                ? "be logged and alerted to staff only (monitor mode)"
                : "be blocked; actions: " + DetectionNotifier.describeActions(settings.actions());
        plugin.messages().send(sender, "test.would", Map.of("would", outcome));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}

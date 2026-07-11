package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.DurationParser;
import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ChatModerationProcessor {
    private final NeoModerationPlugin plugin;
    private final ChatModerationCoordinator coordinator;
    private final ChatModerationActionExecutor actionExecutor;
    private final PlayerMuteService muteService;
    private final MonitorStats monitorStats;
    private final DetectionNotifier notifier;

    public ChatModerationProcessor(
            NeoModerationPlugin plugin,
            ChatModerationCoordinator coordinator,
            ChatModerationActionExecutor actionExecutor,
            PlayerMuteService muteService,
            MonitorStats monitorStats,
            DetectionNotifier notifier
    ) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.actionExecutor = actionExecutor;
        this.muteService = muteService;
        this.monitorStats = monitorStats;
        this.notifier = notifier;
    }

    public boolean handleAsyncChat(Player player, String message) {
        if (player.hasPermission("neomoderation.bypass")) {
            return false;
        }

        if (muteService.isMuted(player.getUniqueId())) {
            int remaining = muteService.remainingSeconds(player.getUniqueId());
            plugin.messages().send(player, "mute.blocked", Map.of(
                    "duration", DurationParser.format(remaining)
            ));
            return true;
        }

        ModerationSettings settings = plugin.settings();
        if (!settings.enabled() || !settings.scanAsyncChat()) {
            return false;
        }

        OfflineModerationResult offlineResult = OfflineModerationEngine.evaluate(message, settings.offline());
        if (offlineResult.flagged()) {
            return handleDetection(player, settings, offlineResult.reason(), message);
        }

        if (settings.api().apiKey().isBlank()) {
            return false;
        }

        boolean flagged = coordinator.isMessageFlagged(player, message, settings);
        if (!flagged) {
            return false;
        }

        return handleDetection(player, settings, "platform", message);
    }

    /** Returns whether the chat event should be cancelled. */
    private boolean handleDetection(Player player, ModerationSettings settings, String reason, String message) {
        monitorStats.record(reason);
        if (settings.mode() == ModerationMode.MONITOR) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                notifier.notifyDetection(player, reason, message, settings);
                plugin.getLogger().info("MONITOR: chat from " + player.getName()
                        + " would be flagged via " + reason
                        + "; no action taken. Run /nmod mode enforce to act on detections.");
            });
            return false;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            actionExecutor.execute(player, settings.actions());
            notifier.notifyDetection(player, reason, message, settings);
            plugin.getLogger().info("Flagged chat from " + player.getName()
                    + " via " + reason
                    + " and executed " + settings.actions().size() + " action(s).");
        });
        return true;
    }
}

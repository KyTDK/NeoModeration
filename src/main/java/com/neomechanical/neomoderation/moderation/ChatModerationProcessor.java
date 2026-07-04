package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.DurationParser;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.entity.Player;

import java.util.Map;

public final class ChatModerationProcessor {
    private final NeoModerationPlugin plugin;
    private final ChatModerationCoordinator coordinator;
    private final ChatModerationActionExecutor actionExecutor;
    private final PlayerMuteService muteService;

    public ChatModerationProcessor(
            NeoModerationPlugin plugin,
            ChatModerationCoordinator coordinator,
            ChatModerationActionExecutor actionExecutor,
            PlayerMuteService muteService
    ) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.actionExecutor = actionExecutor;
        this.muteService = muteService;
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
            executeActions(player, settings, offlineResult.reason());
            return true;
        }

        if (settings.api().apiKey().isBlank()) {
            return false;
        }

        boolean flagged = coordinator.isMessageFlagged(player, message, settings);
        if (!flagged) {
            return false;
        }

        executeActions(player, settings, "platform");
        return true;
    }

    private void executeActions(Player player, ModerationSettings settings, String reason) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            actionExecutor.execute(player, settings.actions());
            plugin.getLogger().info("Flagged chat from " + player.getName()
                    + " via " + reason
                    + " and executed " + settings.actions().size() + " action(s).");
        });
    }
}

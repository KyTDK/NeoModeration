package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.entity.Player;

public final class ChatModerationProcessor {
    private final NeoModerationPlugin plugin;
    private final ChatModerationCoordinator coordinator;
    private final ChatModerationActionExecutor actionExecutor;

    public ChatModerationProcessor(
            NeoModerationPlugin plugin,
            ChatModerationCoordinator coordinator,
            ChatModerationActionExecutor actionExecutor
    ) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.actionExecutor = actionExecutor;
    }

    public boolean handleAsyncChat(Player player, String message) {
        ModerationSettings settings = plugin.settings();
        if (!settings.enabled() || !settings.scanAsyncChat()) {
            return false;
        }
        if (player.hasPermission("neomoderation.bypass")) {
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

package com.neomechanical.neomoderation.listener;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.moderation.ChatModerationProcessor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatModerationListener implements Listener {
    private static final AtomicBoolean SYNC_WARNING_LOGGED = new AtomicBoolean();

    private final NeoModerationPlugin plugin;
    private final ChatModerationProcessor processor;

    public ChatModerationListener(
            NeoModerationPlugin plugin,
            ChatModerationProcessor processor
    ) {
        this.plugin = plugin;
        this.processor = processor;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!event.isAsynchronous()) {
            if (SYNC_WARNING_LOGGED.compareAndSet(false, true)) {
                plugin.getLogger().warning("Skipping synchronous chat moderation to avoid blocking the server thread.");
            }
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(processor.handleAsyncChat(player, event.getMessage()));
    }
}

package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.NeoModerationPlugin;
import com.neomechanical.neomoderation.commands.DurationParser;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;

public final class ChatModerationProcessor {
    private final NeoModerationPlugin plugin;
    private final ChatModerationCoordinator coordinator;
    private final PlayerMuteService muteService;
    private final SpamDetector spamDetector;
    private final DetectionHandler handler;

    public ChatModerationProcessor(
            NeoModerationPlugin plugin,
            ChatModerationCoordinator coordinator,
            PlayerMuteService muteService,
            SpamDetector spamDetector,
            DetectionHandler handler
    ) {
        this.plugin = plugin;
        this.coordinator = coordinator;
        this.muteService = muteService;
        this.spamDetector = spamDetector;
        this.handler = handler;
    }

    public ChatDecision handleAsyncChat(Player player, String message) {
        if (player.hasPermission("neomoderation.bypass")) {
            return ChatDecision.allow();
        }

        if (muteService.isMuted(player.getUniqueId())) {
            int remaining = muteService.remainingSeconds(player.getUniqueId());
            plugin.messages().send(player, "mute.blocked", Map.of(
                    "duration", DurationParser.format(remaining)
            ));
            return ChatDecision.block();
        }

        ModerationSettings settings = plugin.settings();
        if (!settings.enabled() || !settings.scanAsyncChat()) {
            return ChatDecision.allow();
        }

        Optional<String> spamReason = spamDetector.checkMessage(
                player.getUniqueId(), player.getName(), message, settings.spam(), System.currentTimeMillis());
        if (spamReason.isPresent()) {
            return toDecision(
                    handler.handle(player, "chat", spamReason.get(), message, DetectionHandler.Disposition.BLOCK),
                    null);
        }

        OfflineModerationResult offlineResult = OfflineModerationEngine.evaluate(message, settings.offline());
        if (offlineResult.flagged()) {
            String censored = null;
            DetectionHandler.Disposition requested = DetectionHandler.Disposition.BLOCK;
            if (settings.chatCensorLocal()) {
                String masked = OfflineModerationEngine.censor(message, settings.offline());
                if (!masked.equals(message)) {
                    censored = masked;
                    requested = DetectionHandler.Disposition.CENSOR;
                }
            }
            return toDecision(
                    handler.handle(player, "chat", offlineResult.reason(), message, requested),
                    censored);
        }

        if (settings.api().apiKey().isBlank()) {
            return ChatDecision.allow();
        }
        if (!coordinator.isMessageFlagged(player, message, settings)) {
            return ChatDecision.allow();
        }
        return toDecision(
                handler.handle(player, "chat", "platform", message, DetectionHandler.Disposition.BLOCK),
                null);
    }

    private static ChatDecision toDecision(DetectionHandler.Disposition effective, String censored) {
        return switch (effective) {
            case ALLOW -> ChatDecision.allow();
            case BLOCK -> ChatDecision.block();
            case CENSOR -> censored != null ? ChatDecision.censor(censored) : ChatDecision.block();
        };
    }
}

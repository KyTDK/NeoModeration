package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public record ModerationSettings(
        boolean enabled,
        ModerationMode mode,
        ModerationApiSettings api,
        OfflineModerationSettings offline,
        ModerationCategorySettings categories,
        MapArtSettings mapArt,
        List<ModerationAction> actions,
        boolean scanAsyncChat,
        boolean failOpen,
        AlertSettings alerts,
        SpamSettings spam,
        StrikeSettings strikes,
        SurfaceSettings surfaces,
        CaseSettings cases,
        boolean chatCensorLocal
) {
    public record AlertSettings(boolean enabled, boolean includeMessage) {
        public static AlertSettings from(FileConfiguration config) {
            return new AlertSettings(
                    config.getBoolean("moderation.alerts.enabled", true),
                    config.getBoolean("moderation.alerts.includeMessage", true)
            );
        }
    }

    public static ModerationSettings from(FileConfiguration config) {
        return from(config, null);
    }

    public static ModerationSettings from(FileConfiguration config, Logger logger) {
        return new ModerationSettings(
                config.getBoolean("moderation.enabled", false),
                ModerationMode.parse(config.getString("moderation.mode", "enforce")),
                ModerationApiSettings.from(config),
                OfflineModerationSettings.from(config),
                ModerationCategorySettings.from(config),
                MapArtSettings.from(config),
                loadActions(config, logger),
                config.getBoolean("moderation.chat.scanAsyncChat", true),
                config.getBoolean("moderation.chat.failOpen", true),
                AlertSettings.from(config),
                SpamSettings.from(config),
                StrikeSettings.from(config),
                SurfaceSettings.from(config),
                CaseSettings.from(config),
                config.getBoolean("moderation.chat.censorLocalDetections", false)
        );
    }

    private static List<ModerationAction> loadActions(FileConfiguration config, Logger logger) {
        List<ModerationAction> loaded = new ArrayList<>();
        List<Map<?, ?>> rawActions = config.getMapList("moderation.actions");
        for (Map<?, ?> rawAction : rawActions) {
            ModerationAction.tryFrom(rawAction).ifPresentOrElse(
                    loaded::add,
                    () -> {
                        if (logger != null) {
                            logger.warning("Ignoring invalid moderation action in config.yml: " + rawAction);
                        }
                    }
            );
        }
        return List.copyOf(loaded);
    }
}

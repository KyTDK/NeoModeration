package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ModerationSettings(
        boolean enabled,
        ModerationApiSettings api,
        OfflineModerationSettings offline,
        ModerationCategorySettings categories,
        List<ModerationAction> actions,
        boolean scanAsyncChat,
        boolean failOpen
) {
    public static ModerationSettings from(FileConfiguration config) {
        return new ModerationSettings(
                config.getBoolean("moderation.enabled", false),
                ModerationApiSettings.from(config),
                OfflineModerationSettings.from(config),
                ModerationCategorySettings.from(config),
                loadActions(config),
                config.getBoolean("moderation.chat.scanAsyncChat", true),
                config.getBoolean("moderation.chat.failOpen", true)
        );
    }

    private static List<ModerationAction> loadActions(FileConfiguration config) {
        List<ModerationAction> loaded = new ArrayList<>();
        List<Map<?, ?>> rawActions = config.getMapList("moderation.actions");
        for (Map<?, ?> rawAction : rawActions) {
            loaded.add(ModerationAction.from(rawAction));
        }
        return List.copyOf(loaded);
    }
}

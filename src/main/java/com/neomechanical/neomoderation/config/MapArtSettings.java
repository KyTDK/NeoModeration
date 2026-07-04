package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Controls NSFW scanning of filled maps (map art). Scanning requires a cloud API key;
 * without one these settings have no effect.
 */
public record MapArtSettings(
        boolean enabled,
        boolean scanOnHold,
        boolean scanOnFrameInteract,
        boolean confiscate,
        int cacheSize
) {
    private static final int DEFAULT_CACHE_SIZE = 1000;

    public static MapArtSettings from(FileConfiguration config) {
        int cacheSize = config.getInt("moderation.mapArt.cacheSize", DEFAULT_CACHE_SIZE);
        return new MapArtSettings(
                config.getBoolean("moderation.mapArt.enabled", true),
                config.getBoolean("moderation.mapArt.scanOnHold", true),
                config.getBoolean("moderation.mapArt.scanOnFrameInteract", true),
                config.getBoolean("moderation.mapArt.confiscate", true),
                Math.max(16, cacheSize)
        );
    }
}

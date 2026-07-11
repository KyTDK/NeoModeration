package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Local anti-spam thresholds. A value of 0 (or below) disables that individual
 * check. All checks run in-process before any cloud call.
 */
public record SpamSettings(
        boolean enabled,
        int messagesPer10s,
        int duplicateLimit,
        double similarityThreshold,
        int capsPercent,
        int capsMinLength,
        int maxCharRun,
        int commandsPer10s
) {
    public static SpamSettings from(FileConfiguration config) {
        return new SpamSettings(
                config.getBoolean("moderation.spam.enabled", true),
                config.getInt("moderation.spam.messagesPer10s", 5),
                config.getInt("moderation.spam.duplicateLimit", 3),
                config.getDouble("moderation.spam.similarityThreshold", 0.9D),
                config.getInt("moderation.spam.capsPercent", 70),
                config.getInt("moderation.spam.capsMinLength", 8),
                config.getInt("moderation.spam.maxCharRun", 8),
                config.getInt("moderation.spam.commandsPer10s", 8)
        );
    }
}

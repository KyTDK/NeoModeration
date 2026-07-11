package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record OfflineModerationSettings(
        boolean enabled,
        boolean blockAnyUrl,
        boolean normalizeLeetspeak,
        List<String> bannedWords,
        List<String> bannedUrls,
        List<String> allowedWords,
        List<String> allowedUrls
) {
    public static OfflineModerationSettings from(FileConfiguration config) {
        return new OfflineModerationSettings(
                config.getBoolean("moderation.offline.enabled", true),
                config.getBoolean("moderation.offline.blockAnyUrl", false),
                config.getBoolean("moderation.offline.normalizeLeetspeak", true),
                normalizeList(config.getStringList("moderation.offline.bannedWords")),
                normalizeList(config.getStringList("moderation.offline.bannedUrls")),
                normalizeList(config.getStringList("moderation.offline.allowedWords")),
                normalizeList(config.getStringList("moderation.offline.allowedUrls"))
        );
    }

    private static List<String> normalizeList(List<String> rawValues) {
        return rawValues.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}

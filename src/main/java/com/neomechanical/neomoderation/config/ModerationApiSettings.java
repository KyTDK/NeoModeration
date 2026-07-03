package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.FileConfiguration;

public record ModerationApiSettings(
        String endpoint,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutMs
) {
    private static final String DEFAULT_ENDPOINT = "https://api.neomechanical.com/v1/events";
    private static final String LEGACY_DEFAULT_ENDPOINT = "https://api.neomechanical.com/v1/moderation/chat";

    public static ModerationApiSettings from(FileConfiguration config) {
        return new ModerationApiSettings(
                normalizeEndpoint(config.getString("moderation.api.endpoint", DEFAULT_ENDPOINT)),
                config.getString("moderation.api.apiKey", ""),
                config.getInt("moderation.api.connectTimeoutMs", 3000),
                config.getInt("moderation.api.readTimeoutMs", 3000)
        );
    }

    private static String normalizeEndpoint(String configuredEndpoint) {
        if (LEGACY_DEFAULT_ENDPOINT.equals(configuredEndpoint)) {
            return DEFAULT_ENDPOINT;
        }
        return configuredEndpoint == null || configuredEndpoint.isBlank() ? DEFAULT_ENDPOINT : configuredEndpoint.trim();
    }
}

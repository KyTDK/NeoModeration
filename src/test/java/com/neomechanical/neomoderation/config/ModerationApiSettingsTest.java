package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationApiSettingsTest {
    @Test
    void normalizesLegacyEndpointToEventsRoute() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.api.endpoint", "https://api.neomechanical.com/v1/moderation/chat");

        ModerationApiSettings settings = ModerationApiSettings.from(config);

        assertEquals("https://api.neomechanical.com/v1/events", settings.endpoint());
    }

    @Test
    void keepsCustomEndpoint() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.api.endpoint", "https://moderation.example.test/v1/events");

        ModerationApiSettings settings = ModerationApiSettings.from(config);

        assertEquals("https://moderation.example.test/v1/events", settings.endpoint());
    }
}

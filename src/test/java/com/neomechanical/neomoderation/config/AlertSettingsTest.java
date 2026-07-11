package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertSettingsTest {
    @Test
    void alertsDefaultToEnabledWithMessagePreview() {
        ModerationSettings settings = ModerationSettings.from(new YamlConfiguration());

        assertTrue(settings.alerts().enabled());
        assertTrue(settings.alerts().includeMessage());
    }

    @Test
    void alertsCanBeDisabledAndRedacted() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.alerts.enabled", false);
        config.set("moderation.alerts.includeMessage", false);
        ModerationSettings settings = ModerationSettings.from(config);

        assertFalse(settings.alerts().enabled());
        assertFalse(settings.alerts().includeMessage());
    }
}

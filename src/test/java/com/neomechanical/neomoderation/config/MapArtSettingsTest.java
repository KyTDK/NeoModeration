package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapArtSettingsTest {
    @Test
    void appliesProductionSafeDefaults() {
        MapArtSettings settings = MapArtSettings.from(new YamlConfiguration());

        assertTrue(settings.enabled());
        assertTrue(settings.scanOnHold());
        assertTrue(settings.scanOnFrameInteract());
        assertTrue(settings.confiscate());
        assertEquals(1000, settings.cacheSize());
    }

    @Test
    void readsConfiguredValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.mapArt.enabled", false);
        config.set("moderation.mapArt.scanOnHold", false);
        config.set("moderation.mapArt.confiscate", false);
        config.set("moderation.mapArt.cacheSize", 250);

        MapArtSettings settings = MapArtSettings.from(config);

        assertFalse(settings.enabled());
        assertFalse(settings.scanOnHold());
        assertFalse(settings.confiscate());
        assertEquals(250, settings.cacheSize());
    }

    @Test
    void clampsCacheSizeToSafeMinimum() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.mapArt.cacheSize", 1);

        assertEquals(16, MapArtSettings.from(config).cacheSize());
    }
}

package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the enforcement defaults a fresh install actually receives.
 *
 * <p>Before 1.6.0 the shipped config monitored everything, so a new install
 * blocked nothing at all: an admin who installed a chat filter, swore in chat
 * and saw the message go through had no way to tell it from a broken plugin.
 * 99 downloads had produced 4 retained servers. These assertions exist because
 * that default is easy to reintroduce by accident and expensive when it happens.
 */
class ShippedConfigDefaultsTest {

    private static ModerationSettings shipped() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(Files.readString(Path.of("src/main/resources/config.yml")));
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException("shipped config.yml is not valid YAML", e);
        }
        return ModerationSettings.from(new BukkitConfigView(config));
    }

    @Test
    void freshInstallEnforcesTheLocalRulesItShipsWith() throws IOException {
        assertEquals(ModerationMode.ENFORCE, shipped().mode(),
                "a fresh install must protect chat from the first minute, not merely observe it");
    }

    @Test
    void freshInstallOnlyMonitorsCloudDecisions() throws IOException {
        assertEquals(ModerationMode.MONITOR, shipped().cloudMode(),
                "cloud categories are a model's judgement the operator has not seen yet, "
                        + "so they alert until promoted with /nmod cloudmode enforce");
    }

    @Test
    void freshInstallHasModerationSwitchedOn() throws IOException {
        assertTrue(shipped().enabled());
    }

    @Test
    void shippedConfigDocumentsBothModeCommands() throws IOException {
        String raw = Files.readString(Path.of("src/main/resources/config.yml"));
        assertTrue(raw.contains("/nmod mode monitor"),
                "an operator who dislikes enforcement must be able to find the way back");
        assertTrue(raw.contains("/nmod cloudmode enforce"),
                "the config must name the command that promotes cloud decisions");
    }

    @Test
    void updateCheckIsOnByDefault() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(Files.readString(Path.of("src/main/resources/config.yml")));
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException(e);
        }
        assertTrue(config.getBoolean("updateCheck", false),
                "an install that never learns a newer matcher exists quietly misses more over time");
    }
}

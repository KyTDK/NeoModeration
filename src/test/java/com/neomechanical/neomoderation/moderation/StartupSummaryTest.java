package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fresh install starts in monitor mode with no API key, so it deliberately
 * blocks nothing. Startup previously logged one line -- "NeoModeration enabled."
 * -- which leaves an admin with no way to tell working-as-designed from broken.
 * 22 Spigot downloads have produced 1 bStats server, so that distinction matters.
 */
class StartupSummaryTest {

    private static ModerationSettings bundledDefaults() {
        YamlConfiguration config = new YamlConfiguration();
        // Mirrors the shipped config.yml: enabled, trialling in monitor mode.
        // `enabled` must be set explicitly -- it parses as false when absent,
        // unlike `mode`, which deliberately parses as enforce for upgrade safety.
        config.set("moderation.enabled", true);
        config.set("moderation.mode", "monitor");
        return ModerationSettings.from(config);
    }

    @Test
    void saysWhenNothingWillBeBlocked() {
        List<String> lines = StartupSummary.lines(bundledDefaults(), "1.4.0");

        assertTrue(joined(lines).contains("MONITOR"), joined(lines));
        assertTrue(joined(lines).toLowerCase().contains("nothing is blocked"), joined(lines));
    }

    @Test
    void namesTheCommandThatTurnsEnforcementOn() {
        List<String> lines = StartupSummary.lines(bundledDefaults(), "1.4.0");

        assertTrue(joined(lines).contains("/nmod mode enforce"), joined(lines));
    }

    @Test
    void enforceModeDoesNotNagAboutMonitor() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.mode", "enforce");
        List<String> lines = StartupSummary.lines(ModerationSettings.from(config), "1.4.0");

        assertTrue(joined(lines).contains("ENFORCE"));
        assertFalse(joined(lines).contains("/nmod mode enforce"));
    }

    @Test
    void listsProtectionsThatAreActuallyRunning() {
        // Anti-spam and the local word/URL rules need no API key, so they are the
        // honest answer to "is this doing anything yet?".
        List<String> lines = StartupSummary.lines(bundledDefaults(), "1.4.0");

        String text = joined(lines).toLowerCase();
        assertTrue(text.contains("anti-spam"), text);
        assertTrue(text.contains("word"), text);
    }

    @Test
    void saysCloudIsOffAndHowToTurnItOn() {
        List<String> lines = StartupSummary.lines(bundledDefaults(), "1.4.0");

        String text = joined(lines);
        assertTrue(text.toLowerCase().contains("no api key"), text);
        assertTrue(text.contains("/nmod setup"), text);
    }

    @Test
    void doesNotClaimCloudIsOffWhenAKeyIsSet() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.mode", "monitor");
        config.set("moderation.api.apiKey", "nm_live_example");
        List<String> lines = StartupSummary.lines(ModerationSettings.from(config), "1.4.0");

        assertFalse(joined(lines).contains("/nmod setup"), joined(lines));
    }

    @Test
    void reportsWhenModerationIsSwitchedOffEntirely() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", false);
        List<String> lines = StartupSummary.lines(ModerationSettings.from(config), "1.4.0");

        assertTrue(joined(lines).toLowerCase().contains("disabled"), joined(lines));
    }

    @Test
    void alwaysPointsAtStatusForTheFullPicture() {
        List<String> lines = StartupSummary.lines(bundledDefaults(), "1.4.0");

        assertTrue(joined(lines).contains("/nmod status"), joined(lines));
    }

    @Test
    void includesTheVersionSoBugReportsAreActionable() {
        List<String> lines = StartupSummary.lines(bundledDefaults(), "1.4.0");

        assertTrue(joined(lines).contains("1.4.0"), joined(lines));
    }

    @Test
    void staysShortEnoughToReadInAConsole() {
        // A wall of text at startup is ignored exactly like a single line is.
        List<String> lines = StartupSummary.lines(bundledDefaults(), "1.4.0");

        assertTrue(lines.size() <= 6, "too many lines: " + lines.size());
        assertEquals(lines.size(), lines.stream().filter(l -> !l.isBlank()).count());
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }
}

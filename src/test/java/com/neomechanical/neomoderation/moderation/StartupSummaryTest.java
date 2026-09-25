package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.BukkitConfigView;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Startup must describe the settings that actually ship, including the safe
 * block-only default, rather than a fixture that can drift from config.yml.
 */
class StartupSummaryTest {
    private static final String VERSION = "test-version";

    private static ModerationSettings bundledDefaults() throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(Files.readString(Path.of("src/main/resources/config.yml")));
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException("shipped config.yml is not valid YAML", e);
        }
        return ModerationSettings.from(new BukkitConfigView(config));
    }

    private static ModerationSettings monitorSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.mode", "monitor");
        return ModerationSettings.from(new BukkitConfigView(config));
    }

    @Test
    void saysWhenNothingWillBeBlocked() {
        List<String> lines = StartupSummary.lines(monitorSettings(), VERSION);

        assertTrue(joined(lines).contains("MONITOR"), joined(lines));
        assertTrue(joined(lines).contains("local detections alert staff without blocking"), joined(lines));
    }

    @Test
    void namesTheCommandThatTurnsEnforcementOn() {
        List<String> lines = StartupSummary.lines(monitorSettings(), VERSION);

        assertTrue(joined(lines).contains("/nmod mode enforce"), joined(lines));
    }

    @Test
    void enforceModeDoesNotNagAboutMonitor() throws IOException {
        List<String> lines = StartupSummary.lines(bundledDefaults(), VERSION);

        assertTrue(joined(lines).contains("ENFORCE"));
        assertFalse(joined(lines).contains("/nmod mode enforce"));
        assertTrue(joined(lines).contains("no automatic punishment or chat clearing"));
    }

    @Test
    void listsProtectionsThatAreActuallyRunning() throws IOException {
        // Anti-spam and the local word/URL rules need no API key, so they are the
        // honest answer to "is this doing anything yet?".
        List<String> lines = StartupSummary.lines(bundledDefaults(), VERSION);

        String text = joined(lines).toLowerCase();
        assertTrue(text.contains("anti-spam"), text);
        assertTrue(text.contains("word"), text);
    }

    @Test
    void doesNotCallEmptyEnabledRulesActive() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.spam.enabled", false);
        List<String> lines = StartupSummary.lines(ModerationSettings.from(new BukkitConfigView(config)), VERSION);

        assertTrue(joined(lines).contains("nothing - every check is switched off"), joined(lines));
        assertFalse(joined(lines).contains("0 word rules and 0 URL rules"), joined(lines));
    }

    @Test
    void saysCloudIsOffAndHowToTurnItOn() throws IOException {
        List<String> lines = StartupSummary.lines(bundledDefaults(), VERSION);

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
        List<String> lines = StartupSummary.lines(ModerationSettings.from(new BukkitConfigView(config)), VERSION);

        assertFalse(joined(lines).contains("/nmod setup"), joined(lines));
    }

    @Test
    void reportsWhenModerationIsSwitchedOffEntirely() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", false);
        List<String> lines = StartupSummary.lines(ModerationSettings.from(new BukkitConfigView(config)), VERSION);

        assertTrue(joined(lines).toLowerCase().contains("disabled"), joined(lines));
    }

    @Test
    void alwaysPointsAtStatusForTheFullPicture() throws IOException {
        List<String> lines = StartupSummary.lines(bundledDefaults(), VERSION);

        assertTrue(joined(lines).contains("/nmod status"), joined(lines));
    }

    @Test
    void includesTheVersionSoBugReportsAreActionable() throws IOException {
        List<String> lines = StartupSummary.lines(bundledDefaults(), VERSION);

        assertTrue(joined(lines).contains(VERSION), joined(lines));
    }

    @Test
    void givesFreshInstallsAnExactSafeTestPath() throws IOException {
        List<String> lines = StartupSummary.lines(bundledDefaults(), VERSION);

        String text = joined(lines);
        assertTrue(text.contains("/nmod test badword"), text);
        assertTrue(text.contains("never execute actions"), text);
    }

    @Test
    void staysShortEnoughToReadInAConsole() throws IOException {
        // A wall of text at startup is ignored exactly like a single line is.
        List<String> lines = StartupSummary.lines(bundledDefaults(), VERSION);

        assertTrue(lines.size() <= 6, "too many lines: " + lines.size());
        assertEquals(lines.size(), lines.stream().filter(l -> !l.isBlank()).count());
    }

    private static String joined(List<String> lines) {
        return String.join("\n", lines);
    }
}

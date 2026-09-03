package com.neomechanical.neomoderation.platform;

import com.neomechanical.neomoderation.config.BukkitConfigView;
import com.neomechanical.neomoderation.config.ModerationSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The bStats chart values. These are the only way the next release can tell an
 * armed install from an inert one: until 1.6.0 the three registered custom
 * charts had no counterpart on bstats.org, so every value ever sent for them was
 * discarded and no retention theory could be tested against real installs.
 */
class InstallTelemetryTest {

    private static ModerationSettings settings(String... keyValues) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        for (int index = 0; index < keyValues.length; index += 2) {
            String raw = keyValues[index + 1];
            Object value = "true".equals(raw) ? Boolean.TRUE : "false".equals(raw) ? Boolean.FALSE : raw;
            config.set(keyValues[index], value);
        }
        return ModerationSettings.from(new BukkitConfigView(config));
    }

    @Test
    void separatesAnArmedInstallFromAnInertOne() {
        assertEquals("enforce_all",
                InstallTelemetry.protectionState(settings("moderation.mode", "enforce",
                        "moderation.cloudMode", "enforce")));
        assertEquals("monitor_all",
                InstallTelemetry.protectionState(settings("moderation.mode", "monitor",
                        "moderation.cloudMode", "monitor")));
    }

    @Test
    void reportsTheNewDefaultAsItsOwnState() {
        assertEquals("enforce_local_monitor_cloud",
                InstallTelemetry.protectionState(settings("moderation.mode", "enforce",
                        "moderation.cloudMode", "monitor")));
    }

    @Test
    void reportsAServerWhereEverythingIsSwitchedOff() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", false);
        assertEquals("disabled",
                InstallTelemetry.protectionState(ModerationSettings.from(new BukkitConfigView(config))));
    }

    @Test
    void reportsAnInstallWithEveryCheckDisarmed() {
        assertEquals("nothing_armed", InstallTelemetry.protectionState(settings(
                "moderation.offline.enabled", "false",
                "moderation.spam.enabled", "false")));
    }

    @Test
    void distinguishesTheCloudFailuresThatNeedOppositeAnswers() {
        ModerationSettings withKey = settings("moderation.api.apiKey", "k");
        assertEquals("no_key", InstallTelemetry.cloudState(settings(), false, null));
        assertEquals("key_untested", InstallTelemetry.cloudState(withKey, false, null));
        assertEquals("key_working", InstallTelemetry.cloudState(withKey, false, "CLEAR"));
        assertEquals("key_working", InstallTelemetry.cloudState(withKey, false, "FLAGGED"));
        assertEquals("key_rejected", InstallTelemetry.cloudState(withKey, false, "CLIENT_AUTH"));
        assertEquals("no_credits", InstallTelemetry.cloudState(withKey, false, "INSUFFICIENT_CREDITS"));
        assertEquals("key_failing", InstallTelemetry.cloudState(withKey, true, "TRANSIENT_TRANSPORT"));
        assertEquals("transport_flaky", InstallTelemetry.cloudState(withKey, false, "TRANSIENT_TRANSPORT"));
    }

    @Test
    void bucketsDetectionVolumeByOrderOfMagnitude() {
        assertEquals("0", InstallTelemetry.detectionsBucket(0));
        assertEquals("1-9", InstallTelemetry.detectionsBucket(1));
        assertEquals("1-9", InstallTelemetry.detectionsBucket(9));
        assertEquals("10-99", InstallTelemetry.detectionsBucket(10));
        assertEquals("100-999", InstallTelemetry.detectionsBucket(999));
        assertEquals("1000+", InstallTelemetry.detectionsBucket(1000));
    }

    @Test
    void answersWhetherTheInstallHasEverDoneAnything() {
        assertEquals("no", InstallTelemetry.hasEverDetected(0));
        assertEquals("yes", InstallTelemetry.hasEverDetected(1));
    }

    private static ModerationSettings withWords(java.util.List<String> words) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.enabled", true);
        config.set("moderation.offline.bannedWords", words);
        return ModerationSettings.from(new BukkitConfigView(config));
    }

    @Test
    void tellsTheBundledWordListApartFromAnEditedOne() {
        assertEquals("bundled_default",
                InstallTelemetry.wordListState(withWords(java.util.List.of("a", "b")), 2));
        assertEquals("extended",
                InstallTelemetry.wordListState(withWords(java.util.List.of("a", "b", "c")), 2));
        assertEquals("trimmed",
                InstallTelemetry.wordListState(withWords(java.util.List.of("a")), 2));
        assertEquals("empty",
                InstallTelemetry.wordListState(withWords(java.util.List.of()), 2));
        assertEquals("offline_disabled", InstallTelemetry.wordListState(
                settings("moderation.offline.enabled", "false"), 38));
    }

    @Test
    void reportsFoliaSeparatelyFromPaper() {
        assertEquals("Folia", InstallTelemetry.platformFamily("Paper", true));
        assertEquals("Paper", InstallTelemetry.platformFamily("Paper", false));
        assertEquals("unknown", InstallTelemetry.platformFamily("  ", false));
    }

    @Test
    void mapArtScanningReportsWhenItIsOnButUnusable() {
        assertEquals("on_but_no_key", InstallTelemetry.mapArtState(settings()));
        assertEquals("on", InstallTelemetry.mapArtState(settings("moderation.api.apiKey", "k")));
        assertEquals("off", InstallTelemetry.mapArtState(settings("moderation.mapArt.enabled", "false")));
    }
}

package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.OfflineModerationSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineModerationEngineTest {
    @Test
    void flagsBannedWordsCaseInsensitivelyWithoutMatchingInsideSafeWords() {
        OfflineModerationSettings settings = new OfflineModerationSettings(
                true,
                false,
                true,
                List.of("scam"),
                List.of()
        );

        assertTrue(OfflineModerationEngine.evaluate("this is a SCAM", settings).flagged());
        assertFalse(OfflineModerationEngine.evaluate("watch the scampi cook", settings).flagged());
    }

    @Test
    void flagsLeetspeakWhenNormalizationIsEnabled() {
        OfflineModerationSettings settings = new OfflineModerationSettings(
                true,
                false,
                true,
                List.of("scam"),
                List.of()
        );

        assertTrue(OfflineModerationEngine.evaluate("free s c a m", settings).flagged());
        assertTrue(OfflineModerationEngine.evaluate("free $c4m", settings).flagged());
    }

    @Test
    void flagsConfiguredUrlFragmentsAndOptionalAnyUrlMode() {
        OfflineModerationSettings fragmentOnly = new OfflineModerationSettings(
                true,
                false,
                true,
                List.of(),
                List.of("grabify.link", "discord.gg/free")
        );
        OfflineModerationSettings anyUrl = new OfflineModerationSettings(
                true,
                true,
                true,
                List.of(),
                List.of()
        );

        assertTrue(OfflineModerationEngine.evaluate("go to https://grabify.link/a", fragmentOnly).flagged());
        assertTrue(OfflineModerationEngine.evaluate("discord.gg/free-nitro", fragmentOnly).flagged());
        assertFalse(OfflineModerationEngine.evaluate("example.com is fine here", fragmentOnly).flagged());
        assertTrue(OfflineModerationEngine.evaluate("example.com is blocked in any-url mode", anyUrl).flagged());
        assertTrue(OfflineModerationEngine.evaluate("join 1.2.3.4 now", anyUrl).flagged());
    }

    @Test
    void ignoresBlankConfiguredUrlFragments() {
        OfflineModerationSettings settings = new OfflineModerationSettings(
                true,
                false,
                true,
                List.of(),
                List.of("", "   ", "grabify.link")
        );

        assertFalse(OfflineModerationEngine.evaluate("ordinary clean chat", settings).flagged());
        assertTrue(OfflineModerationEngine.evaluate("visit grabify.link/demo", settings).flagged());
    }
}

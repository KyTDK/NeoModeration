package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationCategorySettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModerationResponseParserTest {
    @Test
    void detectsBlockedPlatformDecision() {
        assertTrue(parse("{\"decision\":{\"status\":\"blocked\",\"severity\":\"high\"}}", true));
        assertFalse(parse("{\"decision\":{\"status\":\"review\",\"severity\":\"medium\"}}", true));
        assertFalse(parse("{\"decision\":{\"status\":\"clean\",\"severity\":\"none\"}}", true));
    }

    @Test
    void detectsEnabledCategorySignalsOnly() {
        assertTrue(parse("{\"sexual\":true}", true));
        assertFalse(parse("{\"sexual\":true}", false));
        assertFalse(parse("{\"flagged\":false}", true));
        assertFalse(parse(null, true));
    }

    private static boolean parse(String body, boolean sexual) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.categories.sexual", sexual);
        config.set("moderation.categories.hate", false);
        config.set("moderation.categories.harassment", false);
        config.set("moderation.categories.violence", false);
        config.set("moderation.categories.scam", false);
        config.set("moderation.categories.spam", false);
        config.set("moderation.categories.illicit", false);
        config.set("moderation.categories.selfHarm", false);
        return ChatModerationResponseParser.matchesPositiveSignal(body, ModerationCategorySettings.from(config));
    }
}

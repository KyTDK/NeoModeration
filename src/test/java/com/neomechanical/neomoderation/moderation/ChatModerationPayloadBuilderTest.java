package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationCategorySettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatModerationPayloadBuilderTest {
    @Test
    void escapesJsonControlCharacters() {
        assertEquals(
                "line\\nnext\\ttab\\bback\\fform\\rcarriage\\u0001",
                ModerationPayloadBuilder.escapeJsonString("line\nnext\ttab\bback\fform\rcarriage\u0001")
        );
    }

    @Test
    void buildsCanonicalPlatformEventPayload() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.categories.sexual", true);
        config.set("moderation.categories.hate", false);
        config.set("moderation.categories.harassment", true);
        ModerationCategorySettings categories = ModerationCategorySettings.from(config);

        String json = ModerationPayloadBuilder.buildText("Player\"One", "uuid-123", "hello\\world", categories);

        assertTrue(json.contains("\"mode\":\"sync\""));
        assertTrue(json.contains("\"source\":\"minecraft\""));
        assertTrue(json.contains("\"adapter\":\"neomoderation\""));
        assertTrue(json.contains("\"eventType\":\"chat_message\""));
        assertTrue(json.contains("\"actor\":{\"externalId\":\"uuid-123\",\"username\":\"Player\\\"One\",\"displayName\":\"Player\\\"One\"}"));
        assertTrue(json.contains("\"text\":\"hello\\\\world\""));
        assertTrue(json.contains("\"sexual\":0.7"));
        assertTrue(json.contains("\"hate\":1.0"));
        assertTrue(json.contains("\"harassment\":0.7"));
        assertTrue(json.contains("\"persistence\":\"no_store\""));
    }
}

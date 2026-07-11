package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationCategorySettingsTest {
    @Test
    void booleanValuesKeepPreOneThreeBehavior() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.categories.sexual", true);
        config.set("moderation.categories.hate", false);
        ModerationCategorySettings settings = ModerationCategorySettings.from(config);

        assertEquals(0.7D, settings.threshold("sexual"));
        assertTrue(settings.isEnabled("sexual"));
        assertEquals(1.0D, settings.threshold("hate"));
        assertFalse(settings.isEnabled("hate"));
    }

    @Test
    void missingCategoriesDefaultToEnabledAtDefaultThreshold() {
        ModerationCategorySettings settings = ModerationCategorySettings.from(new YamlConfiguration());

        assertEquals(0.7D, settings.threshold("violence"));
        assertTrue(settings.isEnabled("violence"));
        assertFalse(settings.isEnabled("not-a-category"));
    }

    @Test
    void numericValuesBecomeCustomThresholds() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("moderation.categories.sexual", 0.4D);
        config.set("moderation.categories.hate", "0.85");
        config.set("moderation.categories.spam", 5);
        config.set("moderation.categories.scam", -1);
        ModerationCategorySettings settings = ModerationCategorySettings.from(config);

        assertEquals(0.4D, settings.threshold("sexual"));
        assertTrue(settings.isEnabled("sexual"));
        assertEquals(0.85D, settings.threshold("hate"));
        assertEquals(1.0D, settings.threshold("spam"), "values above 1.0 clamp to disabled");
        assertFalse(settings.isEnabled("spam"));
        assertEquals(0.05D, settings.threshold("scam"), "values below the floor clamp up");
    }
}

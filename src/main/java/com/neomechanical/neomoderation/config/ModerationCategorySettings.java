package com.neomechanical.neomoderation.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public record ModerationCategorySettings(Map<String, Boolean> enabledCategories) {
    private static final String[] CATEGORY_KEYS = {
            "sexual",
            "hate",
            "harassment",
            "violence",
            "scam",
            "spam",
            "illicit",
            "selfHarm"
    };

    public static ModerationCategorySettings from(FileConfiguration config) {
        Map<String, Boolean> categories = new LinkedHashMap<>();
        for (String key : CATEGORY_KEYS) {
            categories.put(key, config.getBoolean("moderation.categories." + key, true));
        }
        return new ModerationCategorySettings(Map.copyOf(categories));
    }

    public boolean isEnabled(String key) {
        return enabledCategories.getOrDefault(key, false);
    }
}

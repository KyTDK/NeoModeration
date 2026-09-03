package com.neomechanical.neomoderation.config;



/**
 * Local case-history settings. {@code storeContent} keeps a short redacted
 * preview of the offending content; when false (default) only metadata is
 * stored, keeping the log privacy-clean.
 */
public record CaseSettings(boolean enabled, boolean storeContent) {
    public static CaseSettings from(ConfigView config) {
        return new CaseSettings(
                config.getBoolean("moderation.cases.enabled", true),
                config.getBoolean("moderation.cases.storeContent", false)
        );
    }
}

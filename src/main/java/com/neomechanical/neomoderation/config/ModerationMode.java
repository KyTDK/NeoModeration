package com.neomechanical.neomoderation.config;

import java.util.Locale;

/**
 * How detections are handled. MONITOR logs, counts, and alerts staff without
 * blocking or punishing; ENFORCE blocks flagged content and runs the configured
 * actions. Anything unrecognized (including a missing key on servers upgraded
 * from pre-1.3.0 configs) parses as ENFORCE so existing behavior never weakens
 * silently.
 */
public enum ModerationMode {
    MONITOR,
    ENFORCE;

    public static ModerationMode parse(String raw) {
        if (raw == null) {
            return ENFORCE;
        }
        return "monitor".equals(raw.trim().toLowerCase(Locale.ROOT)) ? MONITOR : ENFORCE;
    }
}

package com.neomechanical.neomoderation.config;

import java.util.Map;

public record ModerationAction(
        ModerationActionType type,
        String command,
        String role,
        int durationSeconds,
        String reason
) {
    public static ModerationAction from(Map<?, ?> raw) {
        return new ModerationAction(
                ModerationActionType.fromString(stringValue(raw.get("type"))),
                stringValue(raw.get("command")),
                stringValue(raw.get("role")),
                intValue(raw.get("durationSeconds"), 300),
                stringValueOrDefault(raw.get("reason"), "Inappropriate chat message")
        );
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String stringValueOrDefault(Object value, String fallback) {
        String text = stringValue(value);
        return text.isEmpty() ? fallback : text;
    }

    private static int intValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}

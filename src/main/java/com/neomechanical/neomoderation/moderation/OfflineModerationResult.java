package com.neomechanical.neomoderation.moderation;

public record OfflineModerationResult(boolean flagged, String reason) {
    public static OfflineModerationResult flagged(String reason) {
        return new OfflineModerationResult(true, reason);
    }

    public static OfflineModerationResult clear() {
        return new OfflineModerationResult(false, "");
    }
}

package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationCategorySettings;

import java.util.regex.Pattern;

public final class ChatModerationResponseParser {
    private static final Pattern FLAGGED_TRUE = Pattern.compile("\"flagged\"\\s*:\\s*true", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECISION_BLOCKED = Pattern.compile("\"status\"\\s*:\\s*\"blocked\"", Pattern.CASE_INSENSITIVE);

    private ChatModerationResponseParser() {
    }

    public static boolean matchesPositiveSignal(String responseBody, ModerationCategorySettings categorySettings) {
        if (responseBody == null || responseBody.isEmpty()) {
            return false;
        }
        if (FLAGGED_TRUE.matcher(responseBody).find() || DECISION_BLOCKED.matcher(responseBody).find()) {
            return true;
        }
        for (String category : categorySettings.thresholds().keySet()) {
            if (categorySettings.isEnabled(category) && categoryTrue(category, responseBody)) {
                return true;
            }
        }
        return false;
    }

    private static boolean categoryTrue(String category, String responseBody) {
        String platformKey = "selfHarm".equals(category) ? "self-harm" : category;
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(platformKey) + "\"\\s*:\\s*true", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(responseBody).find();
    }
}

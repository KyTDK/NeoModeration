package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.ModerationCategorySettings;

import java.util.regex.Matcher;
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
            if (categorySettings.isEnabled(category)
                    && (categoryTrue(category, responseBody)
                            || categoryScoreAtThreshold(category,
                                    categorySettings.threshold(category), responseBody))) {
                return true;
            }
        }
        return false;
    }

    private static boolean categoryTrue(String category, String responseBody) {
        Pattern pattern = booleanPattern(platformKey(category));
        return pattern.matcher(responseBody).find();
    }

    private static boolean categoryScoreAtThreshold(String category, double threshold, String responseBody) {
        Pattern pattern = numericPattern(platformKey(category));
        Matcher matcher = pattern.matcher(responseBody);
        while (matcher.find()) {
            if (Double.parseDouble(matcher.group(1)) >= threshold) {
                return true;
            }
        }
        return false;
    }

    private static String platformKey(String category) {
        return "selfHarm".equals(category) ? "self-harm" : category;
    }

    private static Pattern booleanPattern(String platformKey) {
        return Pattern.compile("\"" + Pattern.quote(platformKey) + "\"\\s*:\\s*true", Pattern.CASE_INSENSITIVE);
    }

    private static Pattern numericPattern(String platformKey) {
        return Pattern.compile("\"" + Pattern.quote(platformKey) + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)",
                Pattern.CASE_INSENSITIVE);
    }
}

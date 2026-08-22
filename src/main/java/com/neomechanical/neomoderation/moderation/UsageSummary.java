package com.neomechanical.neomoderation.moderation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed view of the NeoMechanical workspace usage endpoint. Parsing is regex-based to
 * avoid bundling a JSON library, matching the rest of the plugin's lightweight approach.
 */
public record UsageSummary(
        String workspace,
        String tier,
        long requestsPerMinuteLimit,
        long creditsRemaining,
        long requestsToday,
        long requestsLast7Days,
        long nsfwImagesRemaining,
        long nsfwImagesCap,
        long nsfwTextUnitsRemaining,
        long nsfwTextUnitsCap,
        long nsfwVideosRemaining,
        long nsfwVideosCap
) {
    public static UsageSummary parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("empty usage response");
        }
        return new UsageSummary(
                string(json, "workspace", "Workspace"),
                string(json, "tier", "free"),
                number(json, "requestsPerMinuteLimit"),
                number(json, "creditsRemaining"),
                number(json, "requestsToday"),
                number(json, "requestsLast7Days"),
                number(json, "nsfwImagesRemaining"),
                number(json, "nsfwImagesCap"),
                number(json, "nsfwTextUnitsRemaining"),
                number(json, "nsfwTextUnitsCap"),
                number(json, "nsfwVideosRemaining"),
                number(json, "nsfwVideosCap")
        );
    }

    private static long number(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : 0L;
    }

    private static String string(String json, String field, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        if (matcher.find() && !matcher.group(1).isBlank()) {
            return matcher.group(1);
        }
        return fallback;
    }
}

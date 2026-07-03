package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.OfflineModerationSettings;

import java.util.Locale;
import java.util.regex.Pattern;

public final class OfflineModerationEngine {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://|www\\.)\\S+|\\b[a-z0-9.-]+\\.[a-z]{2,}(?:/\\S*)?"
    );

    private OfflineModerationEngine() {
    }

    public static OfflineModerationResult evaluate(String message, OfflineModerationSettings settings) {
        if (!settings.enabled()) {
            return OfflineModerationResult.clear();
        }

        String lowerMessage = lower(message);
        for (String bannedUrl : settings.bannedUrls()) {
            String normalizedUrl = lower(bannedUrl);
            if (!normalizedUrl.isBlank() && lowerMessage.contains(normalizedUrl)) {
                return OfflineModerationResult.flagged("blocked_url:" + bannedUrl);
            }
        }

        if (settings.blockAnyUrl() && URL_PATTERN.matcher(message).find()) {
            return OfflineModerationResult.flagged("blocked_url:any");
        }

        String normalizedMessage = normalizeText(message, settings.normalizeLeetspeak());
        for (String bannedWord : settings.bannedWords()) {
            if (matchesBannedWord(normalizedMessage, normalizeWord(bannedWord, settings.normalizeLeetspeak()))) {
                return OfflineModerationResult.flagged("blocked_word:" + bannedWord);
            }
        }

        return OfflineModerationResult.clear();
    }

    private static boolean matchesBannedWord(String normalizedMessage, String normalizedWord) {
        if (normalizedWord.isBlank()) {
            return false;
        }
        String exactTokenPattern = "(^|\\s)" + Pattern.quote(normalizedWord) + "($|\\s)";
        if (Pattern.compile(exactTokenPattern).matcher(normalizedMessage).find()) {
            return true;
        }
        StringBuilder spacedPattern = new StringBuilder("(^|\\s)");
        for (int i = 0; i < normalizedWord.length(); i++) {
            if (i > 0) {
                spacedPattern.append("\\s+");
            }
            spacedPattern.append(Pattern.quote(String.valueOf(normalizedWord.charAt(i))));
        }
        spacedPattern.append("($|\\s)");
        return Pattern.compile(spacedPattern.toString()).matcher(normalizedMessage).find();
    }

    private static String normalizeText(String value, boolean normalizeLeetspeak) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            c = normalizeLeetspeak ? normalizeLeet(c) : c;
            builder.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    private static String normalizeWord(String value, boolean normalizeLeetspeak) {
        String normalized = normalizeText(value, normalizeLeetspeak);
        return normalized.replace(" ", "");
    }

    private static char normalizeLeet(char c) {
        return switch (c) {
            case '@', '4' -> 'a';
            case '3' -> 'e';
            case '1', '!' -> 'i';
            case '0' -> 'o';
            case '$', '5' -> 's';
            case '7' -> 't';
            default -> c;
        };
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

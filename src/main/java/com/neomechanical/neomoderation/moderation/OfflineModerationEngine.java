package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.OfflineModerationSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Local word/URL filtering for the hot chat path.
 *
 * <p>Patterns are compiled once per config load (settings records compare by
 * value, so the single-slot cache invalidates itself on any change) and each
 * word gets a cheap {@code contains} prefilter, so a full regex only runs on
 * candidate hits. This keeps per-message cost in the tens of microseconds even
 * with a 400+ entry word list.</p>
 */
public final class OfflineModerationEngine {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://|www\\.)\\S+|\\b[a-z0-9.-]+\\.[a-z]{2,}(?:/\\S*)?"
    );
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );

    private record CompiledWord(String original, String normalized, Pattern exact, Pattern spaced) {
    }

    private record CompiledRules(
            OfflineModerationSettings source,
            List<CompiledWord> words,
            List<String> lowerUrls
    ) {
    }

    private static volatile CompiledRules cachedRules;

    private OfflineModerationEngine() {
    }

    public static OfflineModerationResult evaluate(String message, OfflineModerationSettings settings) {
        if (!settings.enabled()) {
            return OfflineModerationResult.clear();
        }

        CompiledRules rules = rulesFor(settings);

        String lowerMessage = lower(message);
        List<String> bannedUrls = settings.bannedUrls();
        for (int i = 0; i < rules.lowerUrls().size(); i++) {
            if (lowerMessage.contains(rules.lowerUrls().get(i))) {
                return OfflineModerationResult.flagged("blocked_url:" + bannedUrls.get(i));
            }
        }

        if (settings.blockAnyUrl()
                && (URL_PATTERN.matcher(message).find() || IPV4_PATTERN.matcher(message).find())) {
            return OfflineModerationResult.flagged("blocked_url:any");
        }

        if (rules.words().isEmpty()) {
            return OfflineModerationResult.clear();
        }

        String normalizedMessage = normalizeText(message, settings.normalizeLeetspeak());
        String collapsedMessage = normalizedMessage.replace(" ", "");
        for (CompiledWord word : rules.words()) {
            // Necessary condition for both the exact and spaced-letter matches;
            // skips the regexes entirely for the overwhelmingly common clean case.
            if (!collapsedMessage.contains(word.normalized())) {
                continue;
            }
            if (word.exact().matcher(normalizedMessage).find()
                    || word.spaced().matcher(normalizedMessage).find()) {
                return OfflineModerationResult.flagged("blocked_word:" + word.original());
            }
        }

        return OfflineModerationResult.clear();
    }

    private static CompiledRules rulesFor(OfflineModerationSettings settings) {
        CompiledRules rules = cachedRules;
        if (rules == null || !rules.source().equals(settings)) {
            rules = compile(settings);
            cachedRules = rules;
        }
        return rules;
    }

    private static CompiledRules compile(OfflineModerationSettings settings) {
        List<CompiledWord> words = new ArrayList<>(settings.bannedWords().size());
        for (String bannedWord : settings.bannedWords()) {
            String normalized = normalizeWord(bannedWord, settings.normalizeLeetspeak());
            if (normalized.isBlank()) {
                continue;
            }
            words.add(new CompiledWord(
                    bannedWord,
                    normalized,
                    Pattern.compile("(^|\\s)" + Pattern.quote(normalized) + "($|\\s)"),
                    Pattern.compile(spacedPattern(normalized))
            ));
        }
        List<String> lowerUrls = settings.bannedUrls().stream()
                .map(OfflineModerationEngine::lower)
                .toList();
        return new CompiledRules(settings, List.copyOf(words), lowerUrls);
    }

    private static String spacedPattern(String normalizedWord) {
        StringBuilder pattern = new StringBuilder("(^|\\s)");
        for (int i = 0; i < normalizedWord.length(); i++) {
            if (i > 0) {
                pattern.append("\\s+");
            }
            pattern.append(Pattern.quote(String.valueOf(normalizedWord.charAt(i))));
        }
        return pattern.append("($|\\s)").toString();
    }

    private static String normalizeText(String value, boolean normalizeLeetspeak) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            c = normalizeLeetspeak ? normalizeLeet(c) : c;
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && builder.length() > 0) {
                    builder.append(' ');
                }
                pendingSpace = false;
                builder.append(c);
            } else {
                pendingSpace = true;
            }
        }
        return builder.toString();
    }

    private static String normalizeWord(String value, boolean normalizeLeetspeak) {
        return normalizeText(value, normalizeLeetspeak).replace(" ", "");
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

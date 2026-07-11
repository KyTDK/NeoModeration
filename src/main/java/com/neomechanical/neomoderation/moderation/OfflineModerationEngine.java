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

    private record CompiledUrl(String original, String normalized) {
    }

    private record CompiledAllowedPhrase(Pattern phrase) {
    }

    private record CompiledRules(
            OfflineModerationSettings source,
            List<CompiledWord> words,
            List<CompiledUrl> urls,
            List<CompiledAllowedPhrase> allowedPhrases,
            List<String> allowedUrls
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

        // Allowed URLs are masked out first so neither the banned-URL fragments nor
        // blockAnyUrl can match inside them (explicit allow wins, like bannedUrls
        // these are plain substring matches).
        String lowerMessage = lower(message);
        for (String allowedUrl : rules.allowedUrls()) {
            lowerMessage = lowerMessage.replace(allowedUrl, " ");
        }
        for (CompiledUrl url : rules.urls()) {
            if (lowerMessage.contains(url.normalized())) {
                return OfflineModerationResult.flagged("blocked_url:" + url.original());
            }
        }

        if (settings.blockAnyUrl()
                && (URL_PATTERN.matcher(lowerMessage).find() || IPV4_PATTERN.matcher(lowerMessage).find())) {
            return OfflineModerationResult.flagged("blocked_url:any");
        }

        if (rules.words().isEmpty()) {
            return OfflineModerationResult.clear();
        }

        String normalizedMessage = normalizeText(message, settings.normalizeLeetspeak());
        for (CompiledAllowedPhrase allowed : rules.allowedPhrases()) {
            normalizedMessage = allowed.phrase().matcher(normalizedMessage).replaceAll(" ");
        }
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

    /**
     * Returns the message with every banned-word/URL match replaced by {@code *}
     * while allowed phrases/URLs and the safe remainder stay intact. Cold path:
     * only called after a message has already been flagged, so it can afford the
     * index-mapped normalization that {@link #evaluate} deliberately avoids.
     */
    public static String censor(String message, OfflineModerationSettings settings) {
        if (!settings.enabled() || message.isEmpty()) {
            return message;
        }
        CompiledRules rules = rulesFor(settings);
        char[] raw = message.toCharArray();

        // URL censoring works directly in raw space (matching is substring-based).
        StringBuilder lowerMirror = new StringBuilder(lower(message));
        for (String allowedUrl : rules.allowedUrls()) {
            maskOccurrences(lowerMirror, allowedUrl);
        }
        for (CompiledUrl url : rules.urls()) {
            int from = 0;
            int idx;
            while ((idx = lowerMirror.indexOf(url.normalized(), from)) >= 0) {
                star(raw, idx, idx + url.normalized().length());
                blank(lowerMirror, idx, idx + url.normalized().length());
                from = idx + url.normalized().length();
            }
        }
        if (settings.blockAnyUrl()) {
            starRegexMatches(raw, lowerMirror.toString(), URL_PATTERN);
            starRegexMatches(raw, lowerMirror.toString(), IPV4_PATTERN);
        }

        // Word censoring works in normalized space and maps matches back to raw
        // characters via the index map.
        if (!rules.words().isEmpty()) {
            NormalizedText normalized = normalizeWithMap(new String(raw), settings.normalizeLeetspeak());
            StringBuilder norm = new StringBuilder(normalized.text());
            for (CompiledAllowedPhrase allowed : rules.allowedPhrases()) {
                maskMatches(norm, allowed.phrase());
            }
            String snapshot = norm.toString();
            for (CompiledWord word : rules.words()) {
                starMappedMatches(raw, snapshot, normalized.rawIndex(), word.exact());
                starMappedMatches(raw, snapshot, normalized.rawIndex(), word.spaced());
            }
        }
        return new String(raw);
    }

    private record NormalizedText(String text, int[] rawIndex) {
    }

    /**
     * Same normalization as {@link #normalizeText} but records, per normalized
     * character, the raw index it came from (-1 for inserted separator spaces).
     * Kept separate from the allocation-free hot path used by evaluate().
     */
    private static NormalizedText normalizeWithMap(String value, boolean normalizeLeetspeak) {
        StringBuilder builder = new StringBuilder(value.length());
        int[] map = new int[value.length() * 2 + 1];
        int count = 0;
        boolean pendingSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            c = normalizeLeetspeak ? normalizeLeet(c) : c;
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && builder.length() > 0) {
                    builder.append(' ');
                    map[count++] = -1;
                }
                pendingSpace = false;
                builder.append(c);
                map[count++] = i;
            } else {
                pendingSpace = true;
            }
        }
        return new NormalizedText(builder.toString(), java.util.Arrays.copyOf(map, count));
    }

    private static void star(char[] raw, int start, int end) {
        for (int i = Math.max(0, start); i < Math.min(raw.length, end); i++) {
            if (!Character.isWhitespace(raw[i])) {
                raw[i] = '*';
            }
        }
    }

    /** Length-preserving blanking so later index-based matching stays aligned. */
    private static void blank(StringBuilder text, int start, int end) {
        for (int i = start; i < end && i < text.length(); i++) {
            text.setCharAt(i, ' ');
        }
    }

    private static void maskOccurrences(StringBuilder lowerText, String needle) {
        int from = 0;
        int idx;
        while ((idx = lowerText.indexOf(needle, from)) >= 0) {
            blank(lowerText, idx, idx + needle.length());
            from = idx + needle.length();
        }
    }

    private static void maskMatches(StringBuilder text, Pattern pattern) {
        var matcher = pattern.matcher(text.toString());
        while (matcher.find()) {
            blank(text, matcher.start(), matcher.end());
        }
    }

    private static void starRegexMatches(char[] raw, String haystack, Pattern pattern) {
        var matcher = pattern.matcher(haystack);
        while (matcher.find()) {
            star(raw, matcher.start(), matcher.end());
        }
    }

    private static void starMappedMatches(char[] raw, String normalizedSnapshot, int[] rawIndex, Pattern pattern) {
        var matcher = pattern.matcher(normalizedSnapshot);
        while (matcher.find()) {
            for (int k = matcher.start(); k < matcher.end(); k++) {
                int ri = rawIndex[k];
                if (ri >= 0) {
                    raw[ri] = '*';
                }
            }
        }
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
        List<CompiledUrl> urls = new ArrayList<>(settings.bannedUrls().size());
        for (String bannedUrl : settings.bannedUrls()) {
            String normalized = lower(bannedUrl).trim();
            if (!normalized.isEmpty()) {
                urls.add(new CompiledUrl(bannedUrl, normalized));
            }
        }
        List<CompiledAllowedPhrase> allowedPhrases = new ArrayList<>(settings.allowedWords().size());
        for (String allowedWord : settings.allowedWords()) {
            String normalized = normalizeText(allowedWord, settings.normalizeLeetspeak());
            if (!normalized.isBlank()) {
                allowedPhrases.add(new CompiledAllowedPhrase(Pattern.compile(phrasePattern(normalized))));
            }
        }
        List<String> allowedUrls = new ArrayList<>(settings.allowedUrls().size());
        for (String allowedUrl : settings.allowedUrls()) {
            String normalized = lower(allowedUrl).trim();
            if (!normalized.isEmpty()) {
                allowedUrls.add(normalized);
            }
        }
        return new CompiledRules(
                settings,
                List.copyOf(words),
                List.copyOf(urls),
                List.copyOf(allowedPhrases),
                List.copyOf(allowedUrls)
        );
    }

    /** Whole-phrase pattern over normalized text; word gaps tolerate any whitespace run. */
    private static String phrasePattern(String normalizedPhrase) {
        StringBuilder pattern = new StringBuilder("(^|\\s)");
        String[] tokens = normalizedPhrase.split(" ");
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                pattern.append("\\s+");
            }
            pattern.append(Pattern.quote(tokens[i]));
        }
        return pattern.append("($|\\s)").toString();
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

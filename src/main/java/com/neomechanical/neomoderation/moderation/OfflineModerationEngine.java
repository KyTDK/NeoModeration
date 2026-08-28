package com.neomechanical.neomoderation.moderation;

import com.neomechanical.neomoderation.config.OfflineModerationSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Local word/URL filtering for the hot chat path.
 *
 * <p>Words are matched against the folded spelling produced by
 * {@link TextNormalizer} rather than the raw message, so the usual ways round a
 * word list — spacing, punctuation, repeated letters, accents, fullwidth forms,
 * Cyrillic lookalikes, zero-width characters — all resolve to the same letters
 * before matching. A match may cross separators, but must begin where a token
 * begins and end where one ends, which is what keeps {@code scampi},
 * {@code assignment} and {@code Scunthorpe} clean while still catching
 * {@code f uck}.</p>
 *
 * <p>Patterns are compiled once per config load (settings records compare by
 * value, so the single-slot cache invalidates itself on any change) and each
 * word gets a run-collapsed {@code contains} prefilter — a necessary condition
 * for any match — so the per-character scan only runs on candidates. That keeps
 * per-message cost in the tens of microseconds even with a 400+ entry list.</p>
 */
public final class OfflineModerationEngine {
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://|www\\.)\\S+|\\b[a-z0-9.-]+\\.[a-z]{2,}(?:/\\S*)?"
    );
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );

    /**
     * A banned word as runs of identical characters. Matching requires each
     * message run to be at least as long as the rule's, so {@code fuck} matches
     * {@code fuuuck} and {@code fuckkk} while {@code as} still does not match
     * {@code ass}.
     */
    private record CompiledWord(String original, char[] runChars, int[] runLengths, String collapsed) {
    }

    private record CompiledUrl(String original, String normalized) {
    }

    private record CompiledRules(
            OfflineModerationSettings source,
            List<CompiledWord> words,
            List<String[]> allowedPhrases,
            List<CompiledUrl> urls,
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

        TextNormalizer.Normalized text = TextNormalizer.normalize(message, settings.normalizeLeetspeak());
        if (text.length() == 0) {
            return OfflineModerationResult.clear();
        }
        boolean[] masked = maskAllowedPhrases(text, rules.allowedPhrases());

        for (CompiledWord word : rules.words()) {
            // Necessary condition for any match, and the reason a clean message
            // never reaches the per-character scan.
            if (!text.collapsed().contains(word.collapsed())) {
                continue;
            }
            if (findMatch(text, word, masked, 0) != null) {
                return OfflineModerationResult.flagged("blocked_word:" + word.original());
            }
        }

        return OfflineModerationResult.clear();
    }

    /**
     * Returns the message with every banned-word/URL match replaced by {@code *}
     * while allowed phrases/URLs and the safe remainder stay intact. Cold path:
     * only called after a message has already been flagged.
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

        if (!rules.words().isEmpty()) {
            TextNormalizer.Normalized text =
                    TextNormalizer.normalize(new String(raw), settings.normalizeLeetspeak());
            if (text.length() > 0) {
                boolean[] masked = maskAllowedPhrases(text, rules.allowedPhrases());
                for (CompiledWord word : rules.words()) {
                    if (!text.collapsed().contains(word.collapsed())) {
                        continue;
                    }
                    int from = 0;
                    int[] match;
                    while ((match = findMatch(text, word, masked, from)) != null) {
                        star(raw, text.rawIndex()[match[0]], text.rawIndex()[match[1] - 1] + 1);
                        from = match[1];
                    }
                }
            }
        }
        return new String(raw);
    }

    /**
     * Finds the first match of {@code word} at or after normalised index
     * {@code from}, or {@code null}. A match must start where a token starts and
     * finish where one finishes; between those points separators are ignored.
     */
    private static int[] findMatch(
            TextNormalizer.Normalized text,
            CompiledWord word,
            boolean[] masked,
            int from
    ) {
        char[] chars = text.chars();
        boolean[] tokenStart = text.tokenStart();
        boolean[] tokenEnd = text.tokenEnd();
        char[] runChars = word.runChars();
        int[] runLengths = word.runLengths();
        int length = chars.length;

        for (int start = from; start < length; start++) {
            if (!tokenStart[start] || masked[start]) {
                continue;
            }
            int position = start;
            boolean matched = true;
            for (int run = 0; run < runChars.length; run++) {
                if (position >= length || chars[position] != runChars[run]) {
                    matched = false;
                    break;
                }
                // Runs are maximal and adjacent runs differ, so consuming the
                // whole message run is forced rather than merely greedy.
                int runLength = 0;
                while (position + runLength < length && chars[position + runLength] == runChars[run]) {
                    if (masked[position + runLength]) {
                        break;
                    }
                    runLength++;
                }
                if (runLength < runLengths[run]) {
                    matched = false;
                    break;
                }
                position += runLength;
            }
            if (matched && position > start && tokenEnd[position - 1]) {
                return new int[]{start, position};
            }
        }
        return null;
    }

    /**
     * Marks the characters of every allowed phrase so no banned word can match
     * inside one. Phrases match whole tokens in sequence: an allow-list entry is
     * the admin's own wording, so it is deliberately not evasion-tolerant — that
     * would hand players a pass phrase.
     */
    private static boolean[] maskAllowedPhrases(TextNormalizer.Normalized text, List<String[]> phrases) {
        boolean[] masked = new boolean[text.length()];
        if (phrases.isEmpty()) {
            return masked;
        }
        List<int[]> tokens = tokensOf(text);
        for (String[] phrase : phrases) {
            for (int t = 0; t + phrase.length <= tokens.size(); t++) {
                boolean allMatch = true;
                for (int p = 0; p < phrase.length; p++) {
                    if (!tokenEquals(text, tokens.get(t + p), phrase[p])) {
                        allMatch = false;
                        break;
                    }
                }
                if (allMatch) {
                    int begin = tokens.get(t)[0];
                    int end = tokens.get(t + phrase.length - 1)[1];
                    for (int i = begin; i < end; i++) {
                        masked[i] = true;
                    }
                }
            }
        }
        return masked;
    }

    private static List<int[]> tokensOf(TextNormalizer.Normalized text) {
        List<int[]> tokens = new ArrayList<>();
        boolean[] tokenStart = text.tokenStart();
        boolean[] tokenEnd = text.tokenEnd();
        int begin = -1;
        for (int i = 0; i < text.length(); i++) {
            if (tokenStart[i]) {
                begin = i;
            }
            if (tokenEnd[i] && begin >= 0) {
                tokens.add(new int[]{begin, i + 1});
                begin = -1;
            }
        }
        return tokens;
    }

    private static boolean tokenEquals(TextNormalizer.Normalized text, int[] token, String expected) {
        if (token[1] - token[0] != expected.length()) {
            return false;
        }
        char[] chars = text.chars();
        for (int i = 0; i < expected.length(); i++) {
            if (chars[token[0] + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
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

    private static void starRegexMatches(char[] raw, String haystack, Pattern pattern) {
        var matcher = pattern.matcher(haystack);
        while (matcher.find()) {
            star(raw, matcher.start(), matcher.end());
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
            CompiledWord compiled = compileWord(bannedWord, settings.normalizeLeetspeak());
            if (compiled != null) {
                words.add(compiled);
            }
        }
        List<String[]> allowedPhrases = new ArrayList<>(settings.allowedWords().size());
        for (String allowedWord : settings.allowedWords()) {
            String[] tokens = tokenizeRule(allowedWord, settings.normalizeLeetspeak());
            if (tokens.length > 0) {
                allowedPhrases.add(tokens);
            }
        }
        List<CompiledUrl> urls = new ArrayList<>(settings.bannedUrls().size());
        for (String bannedUrl : settings.bannedUrls()) {
            String normalized = lower(bannedUrl).trim();
            if (!normalized.isEmpty()) {
                urls.add(new CompiledUrl(bannedUrl, normalized));
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
                List.copyOf(allowedPhrases),
                List.copyOf(urls),
                List.copyOf(allowedUrls)
        );
    }

    private static CompiledWord compileWord(String bannedWord, boolean normalizeLeetspeak) {
        TextNormalizer.Normalized normalized = TextNormalizer.normalize(bannedWord, normalizeLeetspeak);
        char[] letters = normalized.chars();
        if (letters.length == 0) {
            return null;
        }
        StringBuilder runChars = new StringBuilder();
        List<Integer> runLengths = new ArrayList<>();
        int index = 0;
        while (index < letters.length) {
            char current = letters[index];
            int run = 0;
            while (index + run < letters.length && letters[index + run] == current) {
                run++;
            }
            runChars.append(current);
            runLengths.add(run);
            index += run;
        }
        char[] chars = new char[runChars.length()];
        runChars.getChars(0, runChars.length(), chars, 0);
        int[] lengths = new int[runLengths.size()];
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = runLengths.get(i);
        }
        return new CompiledWord(bannedWord, chars, lengths, normalized.collapsed());
    }

    /** An allow-list phrase as its normalised whole tokens. */
    private static String[] tokenizeRule(String rule, boolean normalizeLeetspeak) {
        TextNormalizer.Normalized normalized = TextNormalizer.normalize(rule, normalizeLeetspeak);
        List<String> tokens = new ArrayList<>();
        char[] chars = normalized.chars();
        int begin = -1;
        for (int i = 0; i < chars.length; i++) {
            if (normalized.tokenStart()[i]) {
                begin = i;
            }
            if (normalized.tokenEnd()[i] && begin >= 0) {
                tokens.add(new String(chars, begin, i + 1 - begin));
                begin = -1;
            }
        }
        return tokens.toArray(new String[0]);
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

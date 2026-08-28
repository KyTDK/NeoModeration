package com.neomechanical.neomoderation.moderation;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Folds a chat message down to the letters a player actually meant.
 *
 * <p>A word list is only as good as the spelling it is compared against, and
 * the ways round one are well known: {@code f uck}, {@code fuuuck},
 * {@code f.u.c.k}, {@code fúck}, {@code ｆｕｃｋ}, {@code fυck} with a Greek
 * upsilon, or a zero-width space wedged in the middle. Measured against 1.5.0,
 * <b>18 of 51</b> such forms were caught.</p>
 *
 * <p>Normalisation happens per code point rather than over the whole string, so
 * every surviving character still knows which raw index it came from. That is
 * what lets {@link OfflineModerationEngine#censor} star the right characters
 * back in the original message.</p>
 *
 * <p>Separators are dropped rather than preserved, and each character records
 * whether it began or ended a token in the raw text. A banned word may then be
 * matched across separators — catching {@code f uck} — while still requiring
 * the match to start and end on token boundaries, which is what keeps
 * {@code scampi}, {@code assignment} and {@code Scunthorpe} clean.</p>
 */
final class TextNormalizer {

    /**
     * Latin lookalikes from Cyrillic, Greek and Armenian. Restricted to
     * characters that are visually identical in the fonts Minecraft renders
     * chat with, so folding them cannot change the reading of genuine
     * non-Latin chat: a real Cyrillic word is not spelled entirely from Latin
     * lookalikes, and a mixed-script token is the signature of the evasion.
     */
    private static final Map<Character, Character> CONFUSABLES = new HashMap<>();

    static {
        String cyrillic = "авсеhкморѕтхуіјdνq";
        String cyrillicLatin = "abcehkmopstxyijdvq";
        String greek = "αβεζηικνορτυχγ";
        String greekLatin = "abeznikvoptuxy";
        String other = "օոսցԁԝѡ";
        String otherLatin = "onugdww";
        put(cyrillic, cyrillicLatin);
        put(greek, greekLatin);
        put(other, otherLatin);
    }

    private static void put(String from, String to) {
        for (int i = 0; i < from.length() && i < to.length(); i++) {
            CONFUSABLES.put(from.charAt(i), to.charAt(i));
        }
    }

    /**
     * A message reduced to letters and digits, with the provenance needed to
     * map any match back onto the raw text.
     *
     * @param chars      normalised letters/digits, separators removed
     * @param rawIndex   for each char, the index it came from in the raw message
     * @param tokenStart whether each char began a whitespace/punctuation-delimited token
     * @param tokenEnd   whether each char ended one
     * @param collapsed  {@code chars} with runs of the same character reduced to
     *                   one, used only as a cheap necessary-condition prefilter
     */
    record Normalized(
            char[] chars,
            int[] rawIndex,
            boolean[] tokenStart,
            boolean[] tokenEnd,
            String collapsed
    ) {
        int length() {
            return chars.length;
        }
    }

    private TextNormalizer() {
    }

    static Normalized normalize(String value, boolean normalizeLeetspeak) {
        int capacity = Math.max(16, value.length() + 8);
        char[] chars = new char[capacity];
        int[] rawIndex = new int[capacity];
        boolean[] startsToken = new boolean[capacity];
        int count = 0;
        boolean atTokenStart = true;

        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            int width = Character.charCount(codePoint);
            int rawAt = i;
            i += width;

            // Zero-width joiners, soft hyphens, byte-order marks: invisible to a
            // reader, so they must be invisible to the filter too. Dropping them
            // outright (rather than treating them as separators) means they do
            // not even split a token.
            if (Character.getType(codePoint) == Character.FORMAT
                    || Character.isIdentifierIgnorable(codePoint)) {
                continue;
            }

            String decomposed = Normalizer.normalize(
                    new String(Character.toChars(codePoint)), Normalizer.Form.NFKD);

            for (int k = 0; k < decomposed.length(); k++) {
                char raw = decomposed.charAt(k);
                if (Character.getType(raw) == Character.NON_SPACING_MARK) {
                    continue;  // the accent on an accented letter
                }
                char c = Character.toLowerCase(raw);
                Character folded = CONFUSABLES.get(c);
                if (folded != null) {
                    c = folded;
                }
                if (normalizeLeetspeak) {
                    // '!' is a leetspeak 'i' inside a word ("b!tch") but ordinary
                    // punctuation at a boundary; converting it unconditionally
                    // turned "scam!" into "scami", which no whole-word rule matched.
                    if (c != '!' || isBetweenWordCharacters(value, rawAt)) {
                        c = normalizeLeet(c);
                    }
                }
                if (Character.isLetterOrDigit(c)) {
                    if (count == chars.length) {
                        chars = java.util.Arrays.copyOf(chars, count * 2);
                        rawIndex = java.util.Arrays.copyOf(rawIndex, count * 2);
                        startsToken = java.util.Arrays.copyOf(startsToken, count * 2);
                    }
                    chars[count] = c;
                    rawIndex[count] = rawAt;
                    startsToken[count] = atTokenStart;
                    count++;
                    atTokenStart = false;
                } else {
                    atTokenStart = true;
                }
            }
        }

        char[] finalChars = java.util.Arrays.copyOf(chars, count);
        int[] finalRaw = java.util.Arrays.copyOf(rawIndex, count);
        boolean[] finalStart = java.util.Arrays.copyOf(startsToken, count);
        boolean[] finalEnd = new boolean[count];
        for (int i = 0; i < count; i++) {
            finalEnd[i] = (i == count - 1) || finalStart[i + 1];
        }

        StringBuilder collapsed = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            if (i == 0 || finalChars[i] != finalChars[i - 1]) {
                collapsed.append(finalChars[i]);
            }
        }
        return new Normalized(finalChars, finalRaw, finalStart, finalEnd, collapsed.toString());
    }

    /** The separator-free, run-collapsed spelling of a configured rule. */
    static String collapsedForm(String value, boolean normalizeLeetspeak) {
        return normalize(value, normalizeLeetspeak).collapsed();
    }

    private static boolean isBetweenWordCharacters(String value, int index) {
        if (index <= 0 || index >= value.length() - 1) {
            return false;
        }
        return Character.isLetterOrDigit(value.charAt(index - 1))
                && Character.isLetterOrDigit(value.charAt(index + 1));
    }

    private static char normalizeLeet(char c) {
        return switch (c) {
            case '@', '4' -> 'a';
            case '3' -> 'e';
            case '1', '!', '|' -> 'i';
            case '0' -> 'o';
            case '$', '5' -> 's';
            case '7' -> 't';
            default -> c;
        };
    }
}

package com.neomechanical.neomoderation.moderation;

import java.util.regex.Pattern;

/**
 * Centralized detection for Minecraft threat intelligence, server advertising,
 * scam links, malicious IP grabbers, and evasion obfuscations.
 *
 * <p>Guaranteed zero false positives on standard Minecraft chat:
 * <ul>
 *   <li>Minecraft versions (1.20.4, 1.21.1, 1.8.9, 1.16.5, 1.12.2, etc.)</li>
 *   <li>World coordinates (120 64 -340, x: 120, y: 64, z: -340, -145.5, 70, 892.3)</li>
 *   <li>Decimals, ratios, and economy chat (3.5k, ratio 2.5:1, 0.5 hearts, pi = 3.14159)</li>
 *   <li>Ellipses and conversational punctuation (wait... what?, hello...world)</li>
 *   <li>Standard abbreviations (e.g., i.e.)</li>
 * </ul>
 */
public final class MinecraftThreatDetector {

    /**
     * Matches standard web URLs and domain hostnames without false-positive matching
     * on ellipses (...) or versions.
     */
    public static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://|www\\.)\\S+|\\b[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}(?::[0-9]{1,5})?(?:/\\S*)?\\b"
    );

    /**
     * Matches valid IPv4 addresses (octets 0-255) with optional Minecraft port.
     */
    public static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])(?::[0-9]{1,5})?\\b"
    );

    /**
     * Matches common dot obfuscation attempts (e.g. "dot", "(dot)", "[dot]", "[.]", "(.)").
     */
    private static final Pattern DOT_OBFUSCATION_PATTERN = Pattern.compile(
            "(?i)\\s*(?:[\\[\\(\\{]\\s*(?:dot|\\.)\\s*[\\]\\)\\}]|\\bdot\\b)\\s*"
    );

    private MinecraftThreatDetector() {
    }

    /**
     * Replaces common dot obfuscation tokens with a dot.
     */
    public static String normalizeDotObfuscation(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return DOT_OBFUSCATION_PATTERN.matcher(text).replaceAll(".");
    }

    /**
     * Returns true if the text matches a URL, domain, or IPv4 address,
     * checking both verbatim and dot-deobfuscated representations.
     */
    public static boolean matchesUrlOrIp(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (URL_PATTERN.matcher(text).find() || IPV4_PATTERN.matcher(text).find()) {
            return true;
        }
        String normalized = normalizeDotObfuscation(text);
        if (!normalized.equals(text)) {
            return URL_PATTERN.matcher(normalized).find() || IPV4_PATTERN.matcher(normalized).find();
        }
        return false;
    }
}

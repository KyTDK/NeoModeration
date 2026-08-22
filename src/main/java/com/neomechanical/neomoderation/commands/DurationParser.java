package com.neomechanical.neomoderation.commands;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PATTERN = Pattern.compile("^(\\d+)([smhd])?$");

    private DurationParser() {
    }

    public static int parseSeconds(String raw, int defaultSeconds) {
        if (raw == null || raw.isBlank()) {
            return defaultSeconds;
        }
        Matcher matcher = PATTERN.matcher(raw.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid duration: " + raw);
        }
        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        String unit = matcher.group(2);
        long seconds;
        try {
            if (unit == null || unit.equals("s")) {
                seconds = amount;
            } else {
                seconds = switch (unit) {
                    case "m" -> Math.multiplyExact(amount, 60L);
                    case "h" -> Math.multiplyExact(amount, 3600L);
                    case "d" -> Math.multiplyExact(amount, 86400L);
                    default -> throw new IllegalArgumentException("Invalid duration: " + raw);
                };
            }
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Duration too long: " + raw);
        }
        if (seconds > InputLimits.MAX_MUTE_SECONDS) {
            throw new IllegalArgumentException("Duration too long: " + raw);
        }
        return (int) seconds;
    }

    public static String format(int seconds) {
        if (seconds <= 0) {
            return "0s";
        }
        if (seconds % 86400 == 0) {
            return (seconds / 86400) + "d";
        }
        if (seconds % 3600 == 0) {
            return (seconds / 3600) + "h";
        }
        if (seconds % 60 == 0) {
            return (seconds / 60) + "m";
        }
        return seconds + "s";
    }
}

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
        int amount = Integer.parseInt(matcher.group(1));
        if (amount <= 0) {
            throw new IllegalArgumentException("Duration must be positive");
        }
        String unit = matcher.group(2);
        if (unit == null || unit.equals("s")) {
            return amount;
        }
        return switch (unit) {
            case "m" -> amount * 60;
            case "h" -> amount * 3600;
            case "d" -> amount * 86400;
            default -> throw new IllegalArgumentException("Invalid duration: " + raw);
        };
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

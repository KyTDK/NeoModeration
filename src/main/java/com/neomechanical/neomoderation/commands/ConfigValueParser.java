package com.neomechanical.neomoderation.commands;

public final class ConfigValueParser {
    private ConfigValueParser() {
    }

    public static Object parseLikeExisting(Object existingValue, String rawValue) {
        if (existingValue instanceof Boolean) {
            return Boolean.parseBoolean(rawValue);
        }
        if (existingValue instanceof Integer) {
            return Integer.parseInt(rawValue);
        }
        if (existingValue instanceof Long) {
            return Long.parseLong(rawValue);
        }
        if (existingValue instanceof Double) {
            return Double.parseDouble(rawValue);
        }
        return rawValue;
    }

    public static boolean isEditableScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Double;
    }
}

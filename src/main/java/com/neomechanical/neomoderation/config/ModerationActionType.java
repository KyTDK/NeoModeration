package com.neomechanical.neomoderation.config;

import java.util.Locale;
import java.util.Optional;

public enum ModerationActionType {
    CLEAR_CHAT,
    MUTE,
    KICK,
    BAN,
    TIMEOUT,
    GIVE_ROLE,
    TAKE_ROLE,
    TEMP_ROLE,
    COMMAND;

    public static Optional<ModerationActionType> parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ModerationActionType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * @deprecated Prefer {@link #parse(String)} so invalid types are not silently coerced.
     */
    @Deprecated
    public static ModerationActionType fromString(String value) {
        return parse(value).orElse(COMMAND);
    }
}

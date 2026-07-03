package com.neomechanical.neomoderation.config;

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

    public static ModerationActionType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return COMMAND;
        }
        try {
            return ModerationActionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return COMMAND;
        }
    }
}

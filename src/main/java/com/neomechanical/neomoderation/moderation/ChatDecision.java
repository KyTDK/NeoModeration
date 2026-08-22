package com.neomechanical.neomoderation.moderation;

/** What a chat listener should do with the event after moderation. */
public record ChatDecision(Type type, String message) {
    public enum Type {
        ALLOW,
        BLOCK,
        CENSOR
    }

    public static ChatDecision allow() {
        return new ChatDecision(Type.ALLOW, null);
    }

    public static ChatDecision block() {
        return new ChatDecision(Type.BLOCK, null);
    }

    public static ChatDecision censor(String censoredMessage) {
        return new ChatDecision(Type.CENSOR, censoredMessage);
    }
}

package com.neomechanical.neomoderation.moderation;

public record ModerationApiResult(Kind kind) {
    public enum Kind {
        FLAGGED,
        CLEAR,
        TRANSIENT_TRANSPORT,
        CLIENT_AUTH
    }

    public boolean isFlagged() {
        return kind == Kind.FLAGGED;
    }

    public boolean tripsTransientBreaker() {
        return kind == Kind.TRANSIENT_TRANSPORT;
    }

    public static ModerationApiResult flagged() {
        return new ModerationApiResult(Kind.FLAGGED);
    }

    public static ModerationApiResult clear() {
        return new ModerationApiResult(Kind.CLEAR);
    }

    public static ModerationApiResult transientTransport() {
        return new ModerationApiResult(Kind.TRANSIENT_TRANSPORT);
    }

    public static ModerationApiResult clientAuth() {
        return new ModerationApiResult(Kind.CLIENT_AUTH);
    }
}

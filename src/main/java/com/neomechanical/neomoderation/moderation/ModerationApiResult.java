package com.neomechanical.neomoderation.moderation;

public record ModerationApiResult(Kind kind) {
    public enum Kind {
        FLAGGED,
        CLEAR,
        TRANSIENT_TRANSPORT,
        CLIENT_AUTH,
        INSUFFICIENT_CREDITS,
        CLIENT_REQUEST
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

    public static ModerationApiResult insufficientCredits() {
        return new ModerationApiResult(Kind.INSUFFICIENT_CREDITS);
    }

    public static ModerationApiResult clientRequest() {
        return new ModerationApiResult(Kind.CLIENT_REQUEST);
    }

    /**
     * Classifies a non-success HTTP response without conflating payment state,
     * credentials, and malformed requests.
     */
    public static ModerationApiResult fromHttpFailureStatus(int status) {
        if (status == 401 || status == 403) {
            return clientAuth();
        }
        if (status == 402) {
            return insufficientCredits();
        }
        if (status == 408 || status == 429 || status >= 500 || status < 400) {
            return transientTransport();
        }
        return clientRequest();
    }
}

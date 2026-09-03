package com.neomechanical.neomoderation.moderation;

/**
 * The User-Agent every outbound NeoModeration request carries.
 *
 * <p>Until 1.6.0 the API client set no User-Agent at all, so calls arrived as
 * the JDK default and were indistinguishable from any other Java client. A
 * 2026-09-03 audit of every rotated access log could therefore only establish
 * that <em>no</em> Java client had ever called {@code /v1/events} -- it could
 * not have attributed traffic to the plugin if there had been any. Identifying
 * ourselves costs one header and makes the Minecraft channel measurable.
 *
 * <p>Carries no server identity, address, or player data: just the product, its
 * version, and the server platform, which is what a support question needs.
 */
public final class ClientIdentity {

    private static final String PRODUCT = "NeoModeration";

    private static volatile String userAgent = PRODUCT + "/unknown";

    private ClientIdentity() {
    }

    /** Called once at startup, before any request is made. */
    public static void configure(String version, String platform) {
        userAgent = PRODUCT + "/" + safe(version) + " (" + safe(platform) + ")";
    }

    public static String userAgent() {
        return userAgent;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        // Header-safe: keep it to characters a User-Agent may legally contain.
        return value.trim().replaceAll("[^A-Za-z0-9._+-]", "-");
    }
}

package com.neomechanical.neomoderation.moderation;

/**
 * Public recovery destinations shown to server operators after cloud failures.
 *
 * Keep these centralized so command output and console diagnostics send admins to
 * the same place without ever echoing an API key.
 */
public final class CloudRecovery {
    public static final String SIGNUP_URL = "https://neomechanical.com/signup?src=neomoderation";
    public static final String API_KEYS_URL = "https://neomechanical.com/api-keys?src=neomoderation";
    public static final String BILLING_URL = "https://neomechanical.com/billing?src=neomoderation_credits";

    private CloudRecovery() {
    }
}

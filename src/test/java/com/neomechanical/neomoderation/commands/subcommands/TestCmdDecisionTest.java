package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.moderation.CloudRecovery;
import com.neomechanical.neomoderation.moderation.ModerationApiResult;
import com.neomechanical.neomoderation.moderation.NeoMechanicalUsageClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCmdDecisionTest {
    @Test
    void cleanMessagesAreAllowedAndDetectionsRespectTheActiveMode() {
        assertEquals(TestCmd.Outcome.ALLOWED, TestCmd.outcome(true, false, ModerationMode.ENFORCE));
        assertEquals(TestCmd.Outcome.ALLOWED, TestCmd.outcome(true, false, ModerationMode.MONITOR));
        assertEquals(TestCmd.Outcome.ENFORCED, TestCmd.outcome(true, true, ModerationMode.ENFORCE));
        assertEquals(TestCmd.Outcome.MONITORED, TestCmd.outcome(true, true, ModerationMode.MONITOR));
        assertEquals(TestCmd.Outcome.ALLOWED, TestCmd.outcome(false, true, ModerationMode.ENFORCE));
    }

    @Test
    void locallyFlaggedMessagesDoNotProceedToCloud() {
        assertFalse(TestCmd.shouldCheckCloud(true));
        assertTrue(TestCmd.shouldCheckCloud(false));
    }

    @Test
    void cloudErrorsFollowTheConfiguredFailurePolicy() {
        assertFalse(TestCmd.cloudDetected(ModerationApiResult.transientTransport(), true));
        assertTrue(TestCmd.cloudDetected(ModerationApiResult.transientTransport(), false));
        assertFalse(TestCmd.cloudDetected(ModerationApiResult.insufficientCredits(), true));
        assertTrue(TestCmd.cloudDetected(ModerationApiResult.clientAuth(), false));
        assertTrue(TestCmd.cloudDetected(ModerationApiResult.flagged(), true));
        assertFalse(TestCmd.cloudDetected(ModerationApiResult.clear(), false));
    }

    @Test
    void testAndStatusChooseSpecificRecoveryMessages() {
        assertEquals("test.cloud-auth", TestCmd.cloudMessageKey(ModerationApiResult.Kind.CLIENT_AUTH));
        assertEquals("test.cloud-credits",
                TestCmd.cloudMessageKey(ModerationApiResult.Kind.INSUFFICIENT_CREDITS));
        assertEquals("test.cloud-request-error",
                TestCmd.cloudMessageKey(ModerationApiResult.Kind.CLIENT_REQUEST));

        assertEquals("status.cloud-unverified", StatusCmd.cloudStatusKey(null));
        assertEquals("status.cloud-auth", StatusCmd.cloudStatusKey(ModerationApiResult.Kind.CLIENT_AUTH));
        assertEquals("status.cloud-credits",
                StatusCmd.cloudStatusKey(ModerationApiResult.Kind.INSUFFICIENT_CREDITS));
    }

    @Test
    void doctorSendsAuthenticationAndCreditFailuresToDifferentRecoveryPages() {
        NeoMechanicalUsageClient.UsageException auth = new NeoMechanicalUsageClient.UsageException(
                ModerationApiResult.Kind.CLIENT_AUTH, "HTTP 401");
        NeoMechanicalUsageClient.UsageException credits = new NeoMechanicalUsageClient.UsageException(
                ModerationApiResult.Kind.INSUFFICIENT_CREDITS, "HTTP 402");

        String authDetail = DoctorCmd.failureDetail(auth, 10);
        String creditsDetail = DoctorCmd.failureDetail(credits, 10);

        assertEquals("Account authentication", DoctorCmd.failureCheck(auth.kind()));
        assertEquals("Account credits", DoctorCmd.failureCheck(credits.kind()));
        assertTrue(authDetail.contains(CloudRecovery.API_KEYS_URL));
        assertFalse(authDetail.contains(CloudRecovery.BILLING_URL));
        assertTrue(creditsDetail.contains(CloudRecovery.BILLING_URL));
        assertFalse(creditsDetail.contains(CloudRecovery.API_KEYS_URL));
    }

    @Test
    void usageUsesTheSameSpecificFailureClassification() {
        assertEquals("usage.error-auth", UsageCmd.usageErrorKey(ModerationApiResult.Kind.CLIENT_AUTH));
        assertEquals("usage.error-credits",
                UsageCmd.usageErrorKey(ModerationApiResult.Kind.INSUFFICIENT_CREDITS));
        assertEquals("usage.error-request",
                UsageCmd.usageErrorKey(ModerationApiResult.Kind.CLIENT_REQUEST));
    }
}

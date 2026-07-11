package com.neomechanical.neomoderation.commands.subcommands;

import com.neomechanical.neomoderation.config.ModerationMode;
import com.neomechanical.neomoderation.moderation.ModerationApiResult;
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
        assertTrue(TestCmd.cloudDetected(ModerationApiResult.flagged(), true));
        assertFalse(TestCmd.cloudDetected(ModerationApiResult.clear(), false));
    }
}

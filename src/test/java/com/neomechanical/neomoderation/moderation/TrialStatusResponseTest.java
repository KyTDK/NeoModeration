package com.neomechanical.neomoderation.moderation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrialStatusResponseTest {
    @Test
    void missingMalformedAndNestedStatusCannotPretendToBeAStandardWorkspace() {
        for (String body : new String[]{"{}", "not JSON", "{\"metadata\":{\"isTrial\":false}}",
                "{\"isTrial\":\"false\",\"status\":\"standard_workspace\"}"}) {
            assertThrows(TrialClient.TrialException.class, () -> TrialClient.parseStatusSuccess(body));
        }
    }

    @Test
    void recognizesTheExplicitTrialAndStandardWorkspaceContracts() throws Exception {
        assertFalse(TrialClient.parseStatusSuccess(
                "{\"isTrial\":false,\"status\":\"standard_workspace\"}").isTrial());
        var active = TrialClient.parseStatusSuccess(
                "{\"isTrial\":true,\"status\":\"active\",\"daysRemaining\":12,"
                        + "\"expiresAt\":\"2026-10-22T00:00:00Z\",\"upgradeUrl\":\"https://neomechanical.com/billing\"}");
        assertEquals(12, active.daysRemaining());
        assertFalse(active.isExpired());
    }
}

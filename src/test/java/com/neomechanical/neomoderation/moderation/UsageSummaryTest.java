package com.neomechanical.neomoderation.moderation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsageSummaryTest {
    @Test
    void derivesUsageUrlFromEventsEndpoint() {
        assertEquals(
                "https://api.neomechanical.com/v1/workspace/usage",
                NeoMechanicalUsageClient.usageUrl("https://api.neomechanical.com/v1/events")
        );
    }

    @Test
    void derivesUsageUrlFromCustomEndpoint() {
        assertEquals(
                "https://moderation.example.test/v1/workspace/usage",
                NeoMechanicalUsageClient.usageUrl("https://moderation.example.test/v1/moderate")
        );
    }

    @Test
    void parsesFlatUsageFields() {
        String json = "{\"workspace\":\"My Server\",\"tier\":\"free\","
                + "\"requestsPerMinuteLimit\":60,\"creditsRemaining\":12345,"
                + "\"requestsToday\":33,\"requestsLast7Days\":420,"
                + "\"nsfwImagesRemaining\":48,\"nsfwImagesCap\":50,"
                + "\"nsfwTextUnitsRemaining\":90,\"nsfwTextUnitsCap\":100,"
                + "\"nsfwVideosRemaining\":10,\"nsfwVideosCap\":10}";

        UsageSummary summary = UsageSummary.parse(json);

        assertEquals("My Server", summary.workspace());
        assertEquals("free", summary.tier());
        assertEquals(60, summary.requestsPerMinuteLimit());
        assertEquals(12345, summary.creditsRemaining());
        assertEquals(33, summary.requestsToday());
        assertEquals(420, summary.requestsLast7Days());
        assertEquals(48, summary.nsfwImagesRemaining());
        assertEquals(100, summary.nsfwTextUnitsCap());
    }

    @Test
    void toleratesMissingFieldsWithZeroDefaults() {
        UsageSummary summary = UsageSummary.parse("{\"workspace\":\"Solo\"}");

        assertEquals("Solo", summary.workspace());
        assertEquals("free", summary.tier());
        assertEquals(0, summary.creditsRemaining());
    }

    @Test
    void rejectsEmptyBody() {
        assertThrows(IllegalArgumentException.class, () -> UsageSummary.parse(""));
    }
}

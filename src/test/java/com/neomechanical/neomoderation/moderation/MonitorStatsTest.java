package com.neomechanical.neomoderation.moderation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MonitorStatsTest {
    @Test
    void bucketsReasonsByPrefix() {
        MonitorStats stats = new MonitorStats();
        stats.record("blocked_word:scam");
        stats.record("blocked_word:badword");
        stats.record("blocked_url:any");
        stats.record("platform");

        assertEquals(4, stats.total());
        assertEquals(
                Map.of("blocked_word", 2L, "blocked_url", 1L, "platform", 1L),
                stats.byReason()
        );
    }

    @Test
    void handlesNullAndBlankReasons() {
        MonitorStats stats = new MonitorStats();
        stats.record(null);
        stats.record("  ");

        assertEquals(2, stats.total());
        assertEquals(Map.of("other", 2L), stats.byReason());
    }
}

package com.neomechanical.neomoderation.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationActionTest {
    @Test
    void ignoresUnknownActionTypes() {
        assertTrue(ModerationAction.tryFrom(Map.of("type", "NOT_A_REAL_ACTION")).isEmpty());
        assertTrue(ModerationAction.tryFrom(Map.of("type", "COMMAND")).isEmpty());
    }

    @Test
    void loadsValidMuteAction() {
        ModerationAction action = ModerationAction.tryFrom(Map.of(
                "type", "MUTE",
                "durationSeconds", 120,
                "reason", "test"
        )).orElseThrow();
        assertEquals(ModerationActionType.MUTE, action.type());
        assertEquals(120, action.durationSeconds());
    }
}

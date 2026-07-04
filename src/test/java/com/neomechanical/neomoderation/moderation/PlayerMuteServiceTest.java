package com.neomechanical.neomoderation.moderation;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMuteServiceTest {
    @Test
    void mutesAndExpires() throws InterruptedException {
        PlayerMuteService service = new PlayerMuteService();
        UUID playerId = UUID.randomUUID();

        service.mute(playerId, 1);
        assertTrue(service.isMuted(playerId));
        assertTrue(service.remainingSeconds(playerId) >= 1);

        Thread.sleep(1100);
        assertFalse(service.isMuted(playerId));
        assertEqualsZeroRemaining(service, playerId);
    }

    @Test
    void unmuteClearsMute() {
        PlayerMuteService service = new PlayerMuteService();
        UUID playerId = UUID.randomUUID();
        service.mute(playerId, 60);
        service.unmute(playerId);
        assertFalse(service.isMuted(playerId));
    }

    private static void assertEqualsZeroRemaining(PlayerMuteService service, UUID playerId) {
        assertTrue(service.remainingSeconds(playerId) == 0);
    }
}

package com.neomechanical.neomoderation.listener;

import com.neomechanical.neomoderation.moderation.ModerationApiResult;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MapArtListenerTest {
    @Test
    void bypassPermissionSkipsMapChecks() {
        Player player = mock(Player.class);
        when(player.hasPermission("neomoderation.bypass")).thenReturn(true);

        assertTrue(MapArtListener.shouldBypass(player));
    }

    @Test
    void onlyCompletedModerationResultsAreCached() {
        assertTrue(MapArtListener.isCacheableResult(ModerationApiResult.clear()));
        assertTrue(MapArtListener.isCacheableResult(ModerationApiResult.flagged()));
        assertFalse(MapArtListener.isCacheableResult(ModerationApiResult.transientTransport()));
        assertFalse(MapArtListener.isCacheableResult(ModerationApiResult.clientAuth()));
    }
}

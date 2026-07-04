package com.neomechanical.neomoderation.moderation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerMuteService {
    private final ConcurrentHashMap<UUID, Long> mutedUntilMillis = new ConcurrentHashMap<>();

    public void mute(UUID playerId, int durationSeconds) {
        long durationMs = Math.max(1, durationSeconds) * 1000L;
        mutedUntilMillis.put(playerId, System.currentTimeMillis() + durationMs);
    }

    public boolean isMuted(UUID playerId) {
        Long until = mutedUntilMillis.get(playerId);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            mutedUntilMillis.remove(playerId, until);
            return false;
        }
        return true;
    }

    public int remainingSeconds(UUID playerId) {
        Long until = mutedUntilMillis.get(playerId);
        if (until == null) {
            return 0;
        }
        long remainingMs = until - System.currentTimeMillis();
        if (remainingMs <= 0) {
            mutedUntilMillis.remove(playerId, until);
            return 0;
        }
        return (int) Math.ceil(remainingMs / 1000.0);
    }

    public void unmute(UUID playerId) {
        mutedUntilMillis.remove(playerId);
    }
}

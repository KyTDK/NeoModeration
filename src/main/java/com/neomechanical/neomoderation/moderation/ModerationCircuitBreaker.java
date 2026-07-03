package com.neomechanical.neomoderation.moderation;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class ModerationCircuitBreaker {
    private static final int TRANSIENT_TRIP_THRESHOLD = 3;
    private static final long PAUSE_NANOS = TimeUnit.SECONDS.toNanos(60);

    private final Logger logger;
    private final Object lock = new Object();
    private int transientFailures;
    private long pausedUntilNanos;
    private final AtomicBoolean transientPauseLogged = new AtomicBoolean();
    private final AtomicBoolean clientAuthLogged = new AtomicBoolean();

    public ModerationCircuitBreaker(Logger logger) {
        this.logger = logger;
    }

    public void reset() {
        synchronized (lock) {
            transientFailures = 0;
            pausedUntilNanos = 0L;
            transientPauseLogged.set(false);
            clientAuthLogged.set(false);
        }
    }

    public boolean isRemoteCallAllowed() {
        synchronized (lock) {
            long now = System.nanoTime();
            if (pausedUntilNanos != 0L && now >= pausedUntilNanos) {
                pausedUntilNanos = 0L;
                transientFailures = 0;
                transientPauseLogged.set(false);
            }
            return pausedUntilNanos == 0L;
        }
    }

    public void record(ModerationApiResult result) {
        synchronized (lock) {
            if (result.kind() == ModerationApiResult.Kind.CLIENT_AUTH) {
                if (clientAuthLogged.compareAndSet(false, true)) {
                    logger.warning("Moderation API rejected the request (HTTP 4xx). Check moderation.api.endpoint and apiKey.");
                }
                return;
            }

            if (result.tripsTransientBreaker()) {
                transientFailures++;
                if (transientFailures >= TRANSIENT_TRIP_THRESHOLD) {
                    pausedUntilNanos = System.nanoTime() + PAUSE_NANOS;
                    transientFailures = 0;
                    if (transientPauseLogged.compareAndSet(false, true)) {
                        logger.warning("Moderation paused for 60s after repeated platform errors. Messages are not scanned until the pause ends or /neomod reload runs.");
                    }
                }
                return;
            }

            transientFailures = 0;
            pausedUntilNanos = 0L;
            transientPauseLogged.set(false);
            clientAuthLogged.set(false);
        }
    }
}

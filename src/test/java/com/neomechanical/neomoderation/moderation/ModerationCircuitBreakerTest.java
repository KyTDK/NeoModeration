package com.neomechanical.neomoderation.moderation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationCircuitBreakerTest {
    @Test
    void transientFailuresTripAfterThreshold() {
        ModerationCircuitBreaker breaker = newBreaker();
        breaker.record(ModerationApiResult.transientTransport());
        breaker.record(ModerationApiResult.transientTransport());
        assertTrue(breaker.isRemoteCallAllowed());

        breaker.record(ModerationApiResult.transientTransport());
        assertFalse(breaker.isRemoteCallAllowed());
    }

    @Test
    void clientAuthDoesNotTripCircuit() {
        ModerationCircuitBreaker breaker = newBreaker();
        for (int i = 0; i < 5; i++) {
            breaker.record(ModerationApiResult.clientAuth());
        }
        assertTrue(breaker.isRemoteCallAllowed());
    }

    @Test
    void resetClearsPause() {
        ModerationCircuitBreaker breaker = newBreaker();
        tripBreaker(breaker);
        assertFalse(breaker.isRemoteCallAllowed());

        breaker.reset();
        assertTrue(breaker.isRemoteCallAllowed());
    }

    @Test
    void expiredPauseClearsOnNextCheck() throws Exception {
        ModerationCircuitBreaker breaker = newBreaker();
        tripBreaker(breaker);
        setPausedUntilNanos(breaker, System.nanoTime() - 1L);
        assertTrue(breaker.isRemoteCallAllowed());
    }

    private ModerationCircuitBreaker newBreaker() {
        return new ModerationCircuitBreaker(Logger.getLogger("test"));
    }

    private void tripBreaker(ModerationCircuitBreaker breaker) {
        breaker.record(ModerationApiResult.transientTransport());
        breaker.record(ModerationApiResult.transientTransport());
        breaker.record(ModerationApiResult.transientTransport());
    }

    private void setPausedUntilNanos(ModerationCircuitBreaker breaker, long value) throws Exception {
        Field field = ModerationCircuitBreaker.class.getDeclaredField("pausedUntilNanos");
        field.setAccessible(true);
        field.setLong(breaker, value);
    }
}

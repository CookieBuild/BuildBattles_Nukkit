package com.cookiebuild.buildbattles.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cookiebuild.buildbattles.security.RedstoneActivityLimiter.Decision;

class RedstoneActivityLimiterTest {
    @Test
    void tripsBusyCircuitThenRecoversAfterCooldown() {
        RedstoneActivityLimiter limiter = new RedstoneActivityLimiter(2, 5_000L);
        UUID owner = UUID.randomUUID();

        assertEquals(Decision.ALLOWED, limiter.evaluate(owner, 1_000L));
        assertEquals(Decision.ALLOWED, limiter.evaluate(owner, 1_010L));
        assertEquals(Decision.TRIPPED, limiter.evaluate(owner, 1_020L));
        assertEquals(Decision.BLOCKED, limiter.evaluate(owner, 5_000L));
        assertEquals(Decision.ALLOWED, limiter.evaluate(owner, 6_020L));
    }

    @Test
    void isolatesPlotsAndResetsNormalWindows() {
        RedstoneActivityLimiter limiter = new RedstoneActivityLimiter(1, 5_000L);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertEquals(Decision.ALLOWED, limiter.evaluate(alice, 1_000L));
        assertEquals(Decision.TRIPPED, limiter.evaluate(alice, 1_100L));
        assertEquals(Decision.ALLOWED, limiter.evaluate(bob, 1_100L));
        assertEquals(Decision.ALLOWED, limiter.evaluate(bob, 2_200L));
    }
}

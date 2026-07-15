package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaitingStatusTest {
    @Test
    void reportsMissingPlayersBeforeCountdown() {
        WaitingStatus status = WaitingStatus.from(1, 8, 2, 0, 30);

        assertFalse(status.isCountingDown());
        assertEquals(1, status.morePlayersNeeded());
        assertEquals(-1, status.secondsRemaining());
    }

    @Test
    void reportsSameCountdownShapeAsOtherGames() {
        WaitingStatus status = WaitingStatus.from(2, 8, 2, 3, 10);

        assertTrue(status.isCountingDown());
        assertEquals(7, status.secondsRemaining());
        assertEquals(2, status.players());
        assertEquals(8, status.capacity());
    }
}

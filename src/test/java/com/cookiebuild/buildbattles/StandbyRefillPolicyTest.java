package com.cookiebuild.buildbattles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.StandbyGamePool;

class StandbyRefillPolicyTest {
    @Test
    void recoversOneArenaWhenThePoolIsEmptyEvenWithoutAQuietWindow() {
        assertEquals(1, StandbyRefillPolicy.runtimeBatchSize(0, 3, false));
    }

    @Test
    void keepsNonEmergencyRefillsBehindTheQuietWindow() {
        assertEquals(0, StandbyRefillPolicy.runtimeBatchSize(1, 3, false));
        assertEquals(1, StandbyRefillPolicy.runtimeBatchSize(1, 3, true));
        assertEquals(1, StandbyRefillPolicy.runtimeBatchSize(2, 3, true));
        assertEquals(0, StandbyRefillPolicy.runtimeBatchSize(3, 3, true));
    }

    @Test
    void rapidRematchesCannotExhaustTheInitialPool() {
        StandbyGamePool<String> pool = new StandbyGamePool<>(3);
        assertTrue(pool.offer("standby-1"));
        assertTrue(pool.offer("standby-2"));
        assertTrue(pool.offer("standby-3"));

        for (int match = 1; match <= 6; match++) {
            assertNotNull(pool.poll(), "match " + match + " must have a replacement arena");
            int refillBatchSize = StandbyRefillPolicy.runtimeBatchSize(
                    pool.size(), pool.targetSize(), false);
            if (refillBatchSize == 1) {
                assertTrue(pool.offer("recovered-" + match));
            }
        }
    }

    @Test
    void rejectsImpossiblePoolSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(-1, 3, false));
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(4, 3, false));
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(0, 0, false));
    }
}

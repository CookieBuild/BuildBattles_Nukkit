package com.cookiebuild.buildbattles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.game.StandbyGamePool;
import com.cookiebuild.cookiedough.game.StandbyRefillPolicy;

class StandbyRefillPolicyTest {
    @Test
    void recoversOneArenaWhenThePoolIsEmptyEvenWithoutAQuietWindow() {
        assertEquals(1, StandbyRefillPolicy.runtimeBatchSize(0, 1));
    }

    @Test
    void keepsOnlyOnePreparedBuildBattlesArena() {
        assertEquals(0, StandbyRefillPolicy.runtimeBatchSize(1, 1));
    }

    @Test
    void twelveRematchesStayAvailableWithALobbyPlayerOnline() {
        StandbyGamePool<String> pool = new StandbyGamePool<>(1);
        assertTrue(pool.offer("standby-1"));

        int onlineLobbyPlayers = 1;
        assertTrue(onlineLobbyPlayers > 0);
        for (int match = 1; match <= 12; match++) {
            assertNotNull(pool.poll(), "match " + match + " must have a replacement arena");
            int refillBatchSize = StandbyRefillPolicy.runtimeBatchSize(
                    pool.size(), pool.targetSize());
            if (refillBatchSize == 1) {
                assertTrue(pool.offer("recovered-" + match));
            }
        }
    }

    @Test
    void rejectsImpossiblePoolSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(-1, 3));
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(4, 3));
        assertThrows(IllegalArgumentException.class,
                () -> StandbyRefillPolicy.runtimeBatchSize(0, 0));
    }
}

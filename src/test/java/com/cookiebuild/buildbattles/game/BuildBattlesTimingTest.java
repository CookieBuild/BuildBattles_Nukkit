package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildBattlesTimingTest {
    @Test
    void normalStartCountdownIsFifteenSeconds() {
        assertEquals(15, BuildBattlesGame.NORMAL_START_DELAY_SECONDS);
    }
}

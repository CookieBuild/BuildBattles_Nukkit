package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildBattlesReplayTest {
    @Test
    void replayTargetsBuildBattlesInsteadOfGlobalQuickPlay() {
        assertEquals("/buildbattles replay", BuildBattlesGame.replayCommand());
    }
}

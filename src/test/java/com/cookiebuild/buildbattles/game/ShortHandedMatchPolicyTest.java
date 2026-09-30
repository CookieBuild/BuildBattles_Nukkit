package com.cookiebuild.buildbattles.game;

import static com.cookiebuild.buildbattles.game.ShortHandedMatchPolicy.Outcome.CONTINUE;
import static com.cookiebuild.buildbattles.game.ShortHandedMatchPolicy.Outcome.FORFEIT_PARTICIPATION;
import static com.cookiebuild.buildbattles.game.ShortHandedMatchPolicy.Outcome.FORFEIT_VICTORY;
import static com.cookiebuild.buildbattles.game.ShortHandedMatchPolicy.Outcome.SOLO_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ShortHandedMatchPolicyTest {
    @Test
    void soloMatchKeepsBuildingThenSkipsJudging() {
        assertEquals(CONTINUE, ShortHandedMatchPolicy.duringMatch(BuildPhase.BUILDING, 1, 1, 0, 200, 300));
        assertEquals(SOLO_RESULT, ShortHandedMatchPolicy.atBuildEnd(1, 0));
    }

    @Test
    void soloMatchWithLateJoinerStillJudgesNormally() {
        assertEquals(CONTINUE, ShortHandedMatchPolicy.atBuildEnd(2, 0));
        // A late joiner who left again leaves a solo build: no self-vote judging.
        assertEquals(CONTINUE, ShortHandedMatchPolicy.duringMatch(BuildPhase.BUILDING, 1, 1, 0, 250, 300));
        assertEquals(SOLO_RESULT, ShortHandedMatchPolicy.atBuildEnd(1, 0));
    }

    @Test
    void earlyDropToOneEndsWithParticipationOnly() {
        assertEquals(FORFEIT_PARTICIPATION,
                ShortHandedMatchPolicy.duringMatch(BuildPhase.BUILDING, 2, 1, 0, 60, 300));
        assertEquals(FORFEIT_PARTICIPATION,
                ShortHandedMatchPolicy.duringMatch(BuildPhase.BUILDING, 3, 1, 0, 150, 300));
    }

    @Test
    void lateDropToOneIsAForfeitVictory() {
        assertEquals(FORFEIT_VICTORY, ShortHandedMatchPolicy.duringMatch(BuildPhase.BUILDING, 2, 1, 0, 151, 300));
        assertEquals(FORFEIT_VICTORY, ShortHandedMatchPolicy.duringMatch(BuildPhase.JUDGING, 2, 1, 0, 3, 300));
    }

    @Test
    void reconnectGraceAndFullRostersKeepTheNormalFlow() {
        assertEquals(CONTINUE, ShortHandedMatchPolicy.duringMatch(BuildPhase.BUILDING, 2, 1, 1, 200, 300));
        assertEquals(CONTINUE, ShortHandedMatchPolicy.duringMatch(BuildPhase.BUILDING, 2, 2, 0, 200, 300));
        assertEquals(CONTINUE, ShortHandedMatchPolicy.duringMatch(BuildPhase.RESULTS, 2, 1, 0, 1, 300));
        assertEquals(CONTINUE, ShortHandedMatchPolicy.duringMatch(BuildPhase.WAITING, 2, 1, 0, 1, 300));
        assertEquals(CONTINUE, ShortHandedMatchPolicy.atBuildEnd(1, 1));
    }

    @Test
    void progressIsBoundedAndCountsJudgingAsComplete() {
        assertEquals(0.0, ShortHandedMatchPolicy.progress(BuildPhase.WAITING, 10, 300));
        assertEquals(0.5, ShortHandedMatchPolicy.progress(BuildPhase.BUILDING, 150, 300));
        assertEquals(1.0, ShortHandedMatchPolicy.progress(BuildPhase.BUILDING, 400, 300));
        assertEquals(1.0, ShortHandedMatchPolicy.progress(BuildPhase.JUDGING, 0, 300));
    }
}

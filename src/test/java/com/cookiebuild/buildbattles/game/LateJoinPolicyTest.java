package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class LateJoinPolicyTest {
    @Test
    void acceptsBuildersOnlyDuringTheStartOfTheBuildPhase() {
        assertTrue(LateJoinPolicy.isOpen(BuildPhase.BUILDING, 0, 90));
        assertTrue(LateJoinPolicy.isOpen(BuildPhase.BUILDING, 89, 90));
        assertFalse(LateJoinPolicy.isOpen(BuildPhase.BUILDING, 90, 90));
        assertFalse(LateJoinPolicy.isOpen(BuildPhase.WAITING, 0, 90));
        assertFalse(LateJoinPolicy.isOpen(BuildPhase.JUDGING, 0, 90));
        assertEquals(90, LateJoinPolicy.DEFAULT_WINDOW_SECONDS);
    }

    @Test
    void onlyAnEmptyLobbyRedirectsToARunningMatch() {
        assertTrue(LateJoinPolicy.preferRunningMatch(0));
        assertFalse(LateJoinPolicy.preferRunningMatch(1));
    }

    @Test
    void assignsTheFirstNeverUsedPlot() {
        assertEquals(1, LateJoinPolicy.firstFreePlot(8, Set.of(0, 2)));
        assertEquals(0, LateJoinPolicy.firstFreePlot(8, Set.of()));
        assertEquals(-1, LateJoinPolicy.firstFreePlot(2, Set.of(0, 1)));
    }

    @Test
    void soloBuildsThreeMinutesAndTheWindowIsHalfTheBuildPhase() {
        assertEquals(180, LateJoinPolicy.initialBuildSeconds(1, 180, 300));
        assertEquals(300, LateJoinPolicy.initialBuildSeconds(2, 180, 300));
        assertEquals(90, LateJoinPolicy.windowSeconds(180, 0.5));
        assertEquals(150, LateJoinPolicy.windowSeconds(300, 0.5));
        assertEquals(0, LateJoinPolicy.windowSeconds(300, -1));
        assertEquals(300, LateJoinPolicy.windowSeconds(300, 4));
        assertEquals(150, LateJoinPolicy.windowSeconds(300, Double.NaN));
        assertTrue(LateJoinPolicy.isOpen(BuildPhase.BUILDING, 89, LateJoinPolicy.windowSeconds(180, 0.5)));
        assertFalse(LateJoinPolicy.isOpen(BuildPhase.BUILDING, 90, LateJoinPolicy.windowSeconds(180, 0.5)));
    }

    @Test
    void aLateJoinerAlwaysGetsTwoMinutesAndNobodyLosesTime() {
        assertEquals(180, LateJoinPolicy.buildSecondsAfterLateJoin(180, 30, 120));
        assertEquals(180, LateJoinPolicy.buildSecondsAfterLateJoin(180, 60, 120));
        assertEquals(209, LateJoinPolicy.buildSecondsAfterLateJoin(180, 89, 120));
        assertEquals(300, LateJoinPolicy.buildSecondsAfterLateJoin(300, 149, 120));
    }

    @Test
    void aLoneWaiterMovesToAJoinableMatchUnlessInAParty() {
        assertTrue(LateJoinPolicy.routeLoneWaiter(1, false, true));
        assertFalse(LateJoinPolicy.routeLoneWaiter(1, true, true));
        assertFalse(LateJoinPolicy.routeLoneWaiter(2, false, true));
        assertFalse(LateJoinPolicy.routeLoneWaiter(1, false, false));
    }
}

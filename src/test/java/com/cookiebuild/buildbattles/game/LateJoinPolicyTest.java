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
}

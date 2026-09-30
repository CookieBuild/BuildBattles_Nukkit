package com.cookiebuild.buildbattles.game;

import java.util.Set;

/** Pure rules for placing a newly queued player into a running build phase. */
public final class LateJoinPolicy {
    public static final int DEFAULT_WINDOW_SECONDS = 90;

    private LateJoinPolicy() {
    }

    /** A running match accepts builders only during the start of its build phase. */
    public static boolean isOpen(BuildPhase phase, int phaseSeconds, int windowSeconds) {
        return phase == BuildPhase.BUILDING && phaseSeconds >= 0 && phaseSeconds < windowSeconds;
    }

    /**
     * An empty waiting lobby sends the player to a running match; once someone
     * is already waiting, joining that lobby builds a real match instead.
     */
    public static boolean preferRunningMatch(int playersWaitingInOpenGame) {
        return playersWaitingInOpenGame == 0;
    }

    /** First plot never assigned in this match, or -1 when the arena is full. */
    public static int firstFreePlot(int plotCount, Set<Integer> assignedPlots) {
        for (int plot = 0; plot < plotCount; plot++) {
            if (!assignedPlots.contains(plot)) return plot;
        }
        return -1;
    }
}

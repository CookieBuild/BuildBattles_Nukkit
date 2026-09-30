package com.cookiebuild.buildbattles.game;

/**
 * Decides how a match with too few builders ends. A player alone on the map
 * cannot rate his own plot, so judging is skipped instead of running the full
 * voting timers with nobody able to vote.
 */
public final class ShortHandedMatchPolicy {
    public enum Outcome {
        /** Keep the normal build, judging and results flow. */
        CONTINUE,
        /** Only one builder took part: participation reward, no winner. */
        SOLO_RESULT,
        /** Opponents left after more than half of the match: forfeit victory. */
        FORFEIT_VICTORY,
        /** Opponents left early: participation reward only, no winner. */
        FORFEIT_PARTICIPATION
    }

    private ShortHandedMatchPolicy() {
    }

    /**
     * Evaluated every live tick. Only a match that started with at least two
     * builders can end by forfeit; a solo match keeps building until the timer.
     * Players inside their reconnect grace still hold their plot.
     */
    public static Outcome duringMatch(BuildPhase phase, int startingPlayers, int remainingPlayers,
            int pendingReconnects, int phaseSeconds, int buildSeconds) {
        if (phase != BuildPhase.BUILDING && phase != BuildPhase.JUDGING) return Outcome.CONTINUE;
        if (startingPlayers < 2 || remainingPlayers != 1 || pendingReconnects > 0) return Outcome.CONTINUE;
        return progress(phase, phaseSeconds, buildSeconds) > 0.5
                ? Outcome.FORFEIT_VICTORY : Outcome.FORFEIT_PARTICIPATION;
    }

    /** Evaluated when the build timer expires. */
    public static Outcome atBuildEnd(int remainingPlayers, int pendingReconnects) {
        return remainingPlayers == 1 && pendingReconnects == 0 ? Outcome.SOLO_RESULT : Outcome.CONTINUE;
    }

    /** Share of the match already played; the build phase is the only long timer. */
    public static double progress(BuildPhase phase, int phaseSeconds, int buildSeconds) {
        return switch (phase) {
            case WAITING -> 0.0;
            case BUILDING -> buildSeconds <= 0 ? 1.0 : Math.min(1.0, Math.max(0, phaseSeconds) / (double) buildSeconds);
            default -> 1.0;
        };
    }
}

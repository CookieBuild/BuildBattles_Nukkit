package com.cookiebuild.buildbattles.game;

import java.util.Set;

/**
 * Pure rules for build durations and for placing a newly queued player into a
 * running build phase.
 *
 * <p>Rule (release bb-gallery-20261003): a match that starts with one builder
 * builds for {@link #DEFAULT_SOLO_BUILD_SECONDS}, a match with two or more for
 * {@link #DEFAULT_MULTI_BUILD_SECONDS}. A queued player may join during the
 * first {@link #DEFAULT_WINDOW_FRACTION half} of the build duration fixed at
 * start. Whenever someone joins, the build timer becomes
 * {@code max(current end, now + 120 s)}: the players already building never
 * lose time and the newcomer always gets at least two minutes. With the
 * defaults this only ever extends a solo match (a join at second s of a
 * 180 s solo build ends at max(180, s + 120) <= 209 s); multi matches already
 * leave at least 150 s.
 */
public final class LateJoinPolicy {
    /** Legacy fixed window, still honoured when {@code game.late-join-seconds} is set explicitly. */
    public static final int DEFAULT_WINDOW_SECONDS = 90;
    public static final double DEFAULT_WINDOW_FRACTION = 0.5;
    public static final int DEFAULT_SOLO_BUILD_SECONDS = 180;
    public static final int DEFAULT_MULTI_BUILD_SECONDS = 300;
    public static final int DEFAULT_MIN_NEWCOMER_SECONDS = 120;

    private LateJoinPolicy() {
    }

    /** A running match accepts builders only during the start of its build phase. */
    public static boolean isOpen(BuildPhase phase, int phaseSeconds, int windowSeconds) {
        return phase == BuildPhase.BUILDING && phaseSeconds >= 0 && phaseSeconds < windowSeconds;
    }

    /** Build duration fixed when the match starts. */
    public static int initialBuildSeconds(int startingBuilders, int soloSeconds, int multiSeconds) {
        return Math.max(1, startingBuilders <= 1 ? soloSeconds : multiSeconds);
    }

    /** Late-join window as a share of the build duration fixed at start. */
    public static int windowSeconds(int buildSeconds, double fraction) {
        double safe = Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : DEFAULT_WINDOW_FRACTION;
        return (int) Math.floor(Math.max(0, buildSeconds) * safe);
    }

    /** Build duration after a newcomer joins at {@code elapsedSeconds}; never shortens the match. */
    public static int buildSecondsAfterLateJoin(int currentBuildSeconds, int elapsedSeconds, int minNewcomerSeconds) {
        return Math.max(currentBuildSeconds, Math.max(0, elapsedSeconds) + Math.max(0, minNewcomerSeconds));
    }

    /**
     * An empty waiting lobby sends the player to a running match; once someone
     * is already waiting, joining that lobby builds a real match instead.
     */
    public static boolean preferRunningMatch(int playersWaitingInOpenGame) {
        return playersWaitingInOpenGame == 0;
    }

    /**
     * A player waiting alone (before or during the solo-start countdown) moves
     * to a running match that still accepts builders instead of starting yet
     * another solo match. Parties stay together in the lobby.
     */
    public static boolean routeLoneWaiter(int playersWaitingInOpenGame, boolean hasPartyCompanions,
            boolean joinableRunningMatch) {
        return playersWaitingInOpenGame == 1 && !hasPartyCompanions && joinableRunningMatch;
    }

    /** First plot never assigned in this match, or -1 when the arena is full. */
    public static int firstFreePlot(int plotCount, Set<Integer> assignedPlots) {
        for (int plot = 0; plot < plotCount; plot++) {
            if (!assignedPlots.contains(plot)) return plot;
        }
        return -1;
    }
}

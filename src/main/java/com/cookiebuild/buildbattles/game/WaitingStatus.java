package com.cookiebuild.buildbattles.game;

/** Pure waiting/countdown state shared by the action bar and scoreboard. */
public record WaitingStatus(int players, int capacity, int morePlayersNeeded, int secondsRemaining) {
    public WaitingStatus {
        if (players < 0 || capacity < 1 || players > capacity || morePlayersNeeded < 0) {
            throw new IllegalArgumentException("Invalid waiting status");
        }
    }

    public static WaitingStatus from(int players, int capacity, int minimumPlayers,
            int startTimer, int startDelaySeconds) {
        int needed = Math.max(0, minimumPlayers - players);
        int remaining = startTimer > 0 ? Math.max(0, startDelaySeconds - startTimer) : -1;
        return new WaitingStatus(players, capacity, needed, remaining);
    }

    /**
     * Seconds before a lone waiting player may start a solo match, or -1 when
     * the solo start does not apply (more players, disabled, or already due).
     */
    public static int soloStartRemaining(int players, long waitedSeconds, int soloStartAfterSeconds) {
        if (players != 1 || soloStartAfterSeconds < 0) return -1;
        long remaining = soloStartAfterSeconds - Math.max(0L, waitedSeconds);
        return remaining > 0 ? (int) remaining : -1;
    }

    public boolean isCountingDown() {
        return secondsRemaining >= 0;
    }
}

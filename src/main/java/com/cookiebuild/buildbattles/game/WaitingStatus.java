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

    public boolean isCountingDown() {
        return secondsRemaining >= 0;
    }
}

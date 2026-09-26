package com.cookiebuild.buildbattles.game;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VoteLedger {
    public enum Result {
        ACCEPTED,
        INVALID_SCORE,
        SELF_VOTE,
        ALREADY_VOTED
    }

    private final Map<Integer, UUID> owners;
    private final Map<Integer, Map<UUID, Integer>> votes = new HashMap<>();

    public VoteLedger(Map<Integer, UUID> owners) {
        this.owners = Map.copyOf(owners);
    }

    public Result vote(int plot, UUID voter, int score) {
        if (score < 1 || score > 5) {
            return Result.INVALID_SCORE;
        }
        Result eligibility = eligibility(plot, voter);
        if (eligibility != Result.ACCEPTED) return eligibility;
        votes.computeIfAbsent(plot, ignored -> new HashMap<>()).put(voter, score);
        return Result.ACCEPTED;
    }

    /** Read-only eligibility check used before presenting voting controls. */
    public Result eligibility(int plot, UUID voter) {
        if (voter == null || !owners.containsKey(plot)) {
            return Result.INVALID_SCORE;
        }
        if (voter.equals(owners.get(plot))) {
            return Result.SELF_VOTE;
        }
        if (votes.getOrDefault(plot, Map.of()).containsKey(voter)) {
            return Result.ALREADY_VOTED;
        }
        return Result.ACCEPTED;
    }

    public int voteCount(int plot) {
        return votes.getOrDefault(plot, Map.of()).size();
    }

    public int total(int plot) {
        return votes.getOrDefault(plot, Map.of()).values().stream().mapToInt(Integer::intValue).sum();
    }

    public double average(int plot) {
        int count = voteCount(plot);
        return count == 0 ? 0.0 : (double) total(plot) / count;
    }

    public Map<UUID, Integer> votesFor(int plot) {
        return Collections.unmodifiableMap(votes.getOrDefault(plot, Map.of()));
    }
}

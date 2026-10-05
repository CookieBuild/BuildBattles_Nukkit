package com.cookiebuild.buildbattles.game;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Build-duration vote held in the waiting room next to the theme vote.
 *
 * <p>The most voted option wins. A tie picks the shortest tied option, so the
 * players who wanted a short match are never held in a long one by chance.
 * Nobody voting keeps the usual duration (solo or multi) chosen by the caller.
 */
public final class DurationBallot {
    public static final List<Integer> DEFAULT_OPTION_MINUTES = List.of(5, 10, 15);
    static final int MAX_OPTION_MINUTES = 60;

    private final List<Integer> optionMinutes;
    private final Map<UUID, Integer> votes = new HashMap<>();

    public DurationBallot(Collection<Integer> minutes) {
        List<Integer> valid = minutes == null ? List.of() : minutes.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0 && value <= MAX_OPTION_MINUTES)
                .distinct().sorted().toList();
        optionMinutes = valid.isEmpty() ? DEFAULT_OPTION_MINUTES : valid;
    }

    /** Proposed durations in minutes, shortest first. */
    public List<Integer> options() {
        return optionMinutes;
    }

    public boolean isOption(int minutes) {
        return optionMinutes.contains(minutes);
    }

    /** A player owns one active ballot and may change it until the build starts. */
    public boolean vote(UUID playerId, int minutes) {
        if (playerId == null || !isOption(minutes)) return false;
        votes.put(playerId, minutes);
        return true;
    }

    /** Leaving the waiting room also withdraws that player's choice for the next match. */
    public void withdraw(UUID playerId) {
        votes.remove(playerId);
    }

    public long count(int minutes) {
        return votes.values().stream().filter(value -> value == minutes).count();
    }

    /** Build seconds for the match: the winning option, or {@code fallbackSeconds} without any vote. */
    public int winnerSeconds(int fallbackSeconds) {
        if (votes.isEmpty()) return fallbackSeconds;
        long best = optionMinutes.stream().mapToLong(this::count).max().orElse(0L);
        int minutes = optionMinutes.stream().filter(option -> count(option) == best).findFirst()
                .orElse(optionMinutes.get(0));
        return minutes * 60;
    }

    /** Parses {@code "10"}, {@code "10m"} or {@code "10 min"}; -1 when it is not a number of minutes. */
    public static int parseMinutes(String requested) {
        if (requested == null) return -1;
        String digits = requested.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s*(minutes?|mins?|m)$", "");
        try {
            return Integer.parseInt(digits.trim());
        } catch (NumberFormatException error) {
            return -1;
        }
    }
}

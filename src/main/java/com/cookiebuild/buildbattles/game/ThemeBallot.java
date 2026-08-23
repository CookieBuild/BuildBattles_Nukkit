package com.cookiebuild.buildbattles.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class ThemeBallot {
    private final List<String> candidates;
    private final Map<UUID, String> votes = new HashMap<>();

    public ThemeBallot(Collection<String> themes, int candidateCount, RandomGenerator random) {
        if (themes == null || random == null || candidateCount < 1) {
            throw new IllegalArgumentException("Themes, a random generator and at least one candidate are required");
        }
        List<String> pool = new ArrayList<>(new LinkedHashSet<>(themes.stream()
                .filter(theme -> theme != null && !theme.isBlank()).map(String::trim).toList()));
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("At least one non-empty theme is required");
        }
        java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        candidates = List.copyOf(pool.subList(0, Math.min(candidateCount, pool.size())));
    }

    public List<String> candidates() {
        return candidates;
    }

    /** A player owns one active ballot and may change it until the build starts. */
    public boolean vote(UUID playerId, String requestedTheme) {
        if (playerId == null || requestedTheme == null) {
            return false;
        }
        String selected = canonicalCandidate(requestedTheme);
        if (selected == null) {
            return false;
        }
        votes.put(playerId, selected);
        return true;
    }

    public String canonicalCandidate(String requestedTheme) {
        if (requestedTheme == null) return null;
        return candidates.stream().filter(theme -> theme.equalsIgnoreCase(requestedTheme.trim()))
                .findFirst().orElse(null);
    }

    public String winner(RandomGenerator random) {
        if (random == null) {
            throw new IllegalArgumentException("Random generator is required");
        }
        Map<String, Long> counts = new HashMap<>();
        candidates.forEach(theme -> counts.put(theme, 0L));
        votes.values().forEach(theme -> counts.computeIfPresent(theme, (ignored, count) -> count + 1));
        long best = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        List<String> tied = candidates.stream().filter(theme -> counts.get(theme) == best).toList();
        return tied.get(random.nextInt(tied.size()));
    }

    public long count(String theme) {
        return votes.values().stream().filter(value -> value.equals(theme)).count();
    }
}

package com.cookiebuild.buildbattles.game;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class ThemeBallot {
    private final List<String> candidates;
    /** The one seasonal candidate while a seasonal theme is active, else null. */
    private final String seasonalCandidate;
    private final Map<UUID, String> votes = new HashMap<>();

    public ThemeBallot(Collection<String> themes, int candidateCount, RandomGenerator random) {
        this(themes, List.of(), candidateCount, random);
    }

    /**
     * While {@code seasonalThemes} is not empty, exactly one of them joins the vote at a random
     * position; the other candidates come from {@code themes} minus every seasonal theme (compared
     * ignoring case), so the ballot never shows two seasonal themes nor a duplicate. Without seasonal
     * themes the draw is exactly the regular one (same random calls, same candidates).
     */
    public ThemeBallot(Collection<String> themes, Collection<String> seasonalThemes, int candidateCount,
                       RandomGenerator random) {
        if (themes == null || random == null || candidateCount < 1) {
            throw new IllegalArgumentException("Themes, a random generator and at least one candidate are required");
        }
        List<String> pool = normalized(themes);
        List<String> seasonal = seasonalThemes == null ? List.of() : normalized(seasonalThemes);
        if (seasonal.isEmpty()) {
            if (pool.isEmpty()) {
                throw new IllegalArgumentException("At least one non-empty theme is required");
            }
            java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
            candidates = List.copyOf(pool.subList(0, Math.min(candidateCount, pool.size())));
            seasonalCandidate = null;
            return;
        }
        Set<String> seasonalKeys = new HashSet<>();
        List<String> seasonalPool = new ArrayList<>();
        for (String theme : seasonal) {
            if (seasonalKeys.add(theme.toLowerCase(Locale.ROOT))) seasonalPool.add(theme);
        }
        List<String> regular = new ArrayList<>(pool.stream()
                .filter(theme -> !seasonalKeys.contains(theme.toLowerCase(Locale.ROOT))).toList());

        String featured = seasonalPool.get(random.nextInt(seasonalPool.size()));
        java.util.Collections.shuffle(regular, new java.util.Random(random.nextLong()));
        List<String> drawn = new ArrayList<>(regular.subList(0, Math.min(candidateCount - 1, regular.size())));
        drawn.add(random.nextInt(drawn.size() + 1), featured);
        candidates = List.copyOf(drawn);
        seasonalCandidate = featured;
    }

    private static List<String> normalized(Collection<String> themes) {
        return new ArrayList<>(new LinkedHashSet<>(themes.stream()
                .filter(theme -> theme != null && !theme.isBlank()).map(String::trim).toList()));
    }

    public List<String> candidates() {
        return candidates;
    }

    /** The seasonal theme featured in this vote, empty when no seasonal theme was active. */
    public Optional<String> seasonalCandidate() {
        return Optional.ofNullable(seasonalCandidate);
    }

    public boolean isSeasonal(String candidate) {
        return seasonalCandidate != null && seasonalCandidate.equals(candidate);
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

    /** Leaving the waiting room also withdraws that player's choice for the next match. */
    public void withdraw(UUID playerId) {
        votes.remove(playerId);
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

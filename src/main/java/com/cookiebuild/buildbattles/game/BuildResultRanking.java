package com.cookiebuild.buildbattles.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Produces one consistent ranking for display, rewards and persisted placements. */
public final class BuildResultRanking {
    public record Entry(int plot, UUID playerId, int placement, int points, int votes,
            double average, boolean forfeited) {
    }

    public record Result(List<Entry> entries, Set<UUID> winners, Integer winningPlot) {
        public Result {
            entries = List.copyOf(entries);
            winners = Collections.unmodifiableSet(new LinkedHashSet<>(winners));
        }
    }

    private record Score(int plot, UUID playerId, int points, int votes,
            double average, boolean forfeited) {
        private boolean eligible() {
            return !forfeited && votes > 0;
        }
    }

    private BuildResultRanking() {
    }

    public static Result calculate(Map<Integer, UUID> owners, VoteLedger ledger, Set<UUID> forfeitedPlayers) {
        List<Score> scores = owners.entrySet().stream()
                .map(entry -> new Score(entry.getKey(), entry.getValue(), ledger.total(entry.getKey()),
                        ledger.voteCount(entry.getKey()), ledger.average(entry.getKey()),
                        forfeitedPlayers.contains(entry.getValue())))
                .sorted(Comparator.comparing(Score::eligible).reversed()
                        .thenComparing(Comparator.comparingDouble(Score::average).reversed())
                        .thenComparing(Comparator.comparingInt(Score::votes).reversed())
                        .thenComparing(Comparator.comparingInt(Score::points).reversed())
                        .thenComparingInt(Score::plot))
                .toList();

        List<Entry> entries = new ArrayList<>();
        int placement = 0;
        Score previous = null;
        for (int index = 0; index < scores.size(); index++) {
            Score score = scores.get(index);
            if (previous == null || !sameRank(previous, score)) {
                placement = index + 1;
            }
            entries.add(new Entry(score.plot(), score.playerId(), placement, score.points(), score.votes(),
                    score.average(), score.forfeited()));
            previous = score;
        }

        Score leader = scores.stream().filter(Score::eligible).findFirst().orElse(null);
        Set<UUID> winners = new LinkedHashSet<>();
        if (leader != null) {
            scores.stream().filter(Score::eligible).filter(score -> sameRank(leader, score))
                    .map(Score::playerId).forEach(winners::add);
        }
        return new Result(entries, winners, leader == null ? null : leader.plot());
    }

    private static boolean sameRank(Score left, Score right) {
        return left.eligible() == right.eligible()
                && Double.compare(left.average(), right.average()) == 0
                && left.votes() == right.votes()
                && left.points() == right.points();
    }
}

package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class BuildResultRankingTest {
    @Test
    void voteCountBreaksAnAverageTieForRankingAndWinner() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Map<Integer, UUID> owners = owners(first, second, third);
        VoteLedger ledger = new VoteLedger(owners);

        ledger.vote(0, second, 4);
        ledger.vote(0, third, 4);
        ledger.vote(1, first, 4);
        ledger.vote(2, first, 3);

        BuildResultRanking.Result result = BuildResultRanking.calculate(owners, ledger, Set.of());

        assertEquals(Set.of(first), result.winners());
        assertEquals(0, result.winningPlot());
        assertEquals(1, result.entries().get(0).placement());
        assertEquals(8, result.entries().get(0).points());
        assertEquals(2, result.entries().get(0).votes());
        assertEquals(2, result.entries().get(1).placement());
        assertEquals(3, result.entries().get(2).placement());
    }

    @Test
    void exactFirstPlaceTieKeepsBothWinnersAndClearCompetitionPlacements() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        Map<Integer, UUID> owners = owners(first, second, third);
        VoteLedger ledger = new VoteLedger(owners);

        ledger.vote(0, second, 5);
        ledger.vote(1, first, 5);
        ledger.vote(2, first, 3);

        BuildResultRanking.Result result = BuildResultRanking.calculate(owners, ledger, Set.of());

        assertEquals(Set.of(first, second), result.winners());
        assertEquals(1, result.entries().get(0).placement());
        assertEquals(1, result.entries().get(1).placement());
        assertEquals(3, result.entries().get(2).placement());
    }

    private static Map<Integer, UUID> owners(UUID first, UUID second, UUID third) {
        Map<Integer, UUID> owners = new LinkedHashMap<>();
        owners.put(0, first);
        owners.put(1, second);
        owners.put(2, third);
        return owners;
    }
}

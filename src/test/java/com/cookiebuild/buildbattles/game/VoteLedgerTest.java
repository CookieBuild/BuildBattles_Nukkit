package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VoteLedgerTest {
    @Test
    void blocksSelfVotesAndSecondVotesForSamePlot() {
        UUID owner = UUID.randomUUID();
        UUID voter = UUID.randomUUID();
        VoteLedger ledger = new VoteLedger(Map.of(0, owner));

        assertEquals(VoteLedger.Result.SELF_VOTE, ledger.vote(0, owner, 5));
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.vote(0, voter, 4));
        assertEquals(VoteLedger.Result.ALREADY_VOTED, ledger.vote(0, voter, 5));
        assertEquals(1, ledger.voteCount(0));
        assertEquals(4, ledger.total(0));
        assertEquals(4.0, ledger.average(0));
    }

    @Test
    void rejectsScoresOutsideOneToFive() {
        UUID owner = UUID.randomUUID();
        UUID voter = UUID.randomUUID();
        VoteLedger ledger = new VoteLedger(Map.of(0, owner));
        assertEquals(VoteLedger.Result.INVALID_SCORE, ledger.vote(0, voter, 0));
        assertEquals(VoteLedger.Result.INVALID_SCORE, ledger.vote(0, voter, 6));
    }
}

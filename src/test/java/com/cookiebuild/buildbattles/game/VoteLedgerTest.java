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

    @Test
    void votingControlsFollowEligibilityAcrossPlotsAndReconnection() {
        UUID firstBuilder = UUID.randomUUID();
        UUID secondBuilder = UUID.randomUUID();
        UUID returningVoter = UUID.randomUUID();
        VoteLedger ledger = new VoteLedger(Map.of(0, firstBuilder, 1, secondBuilder));

        assertEquals(VoteLedger.Result.SELF_VOTE, ledger.eligibility(0, firstBuilder));
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.eligibility(0, returningVoter));
        assertEquals(0, ledger.voteCount(0), "Opening a menu must not cast a vote");
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.vote(0, returningVoter, 4));
        UUID reconnectedId = UUID.fromString(returningVoter.toString());
        assertEquals(VoteLedger.Result.ALREADY_VOTED, ledger.eligibility(0, reconnectedId));
        assertEquals(4, ledger.votesFor(0).get(reconnectedId));
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.eligibility(1, reconnectedId));
        assertEquals(VoteLedger.Result.ACCEPTED, ledger.eligibility(1, firstBuilder));
        assertEquals(VoteLedger.Result.SELF_VOTE, ledger.eligibility(1, secondBuilder));
    }

    @Test
    void absentPlotOrVoterCannotReceiveVotingControls() {
        UUID owner = UUID.randomUUID();
        VoteLedger ledger = new VoteLedger(Map.of(0, owner));
        assertEquals(VoteLedger.Result.INVALID_SCORE, ledger.eligibility(1, owner));
        assertEquals(VoteLedger.Result.INVALID_SCORE, ledger.eligibility(0, null));
        assertEquals(0, ledger.voteCount(0));
    }
}

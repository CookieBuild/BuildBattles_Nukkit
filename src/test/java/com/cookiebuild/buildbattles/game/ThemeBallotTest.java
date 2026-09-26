package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ThemeBallotTest {
    @Test
    void exposesBoundedUniqueCandidatesAndLetsPlayerChangeBallot() {
        ThemeBallot ballot = new ThemeBallot(List.of("Farm", "Farm", "Pizza", "Rocket", " "), 3, new Random(4));
        assertEquals(3, ballot.candidates().size());
        assertEquals(3, ballot.candidates().stream().distinct().count());

        UUID player = UUID.randomUUID();
        assertTrue(ballot.vote(player, ballot.candidates().get(0)));
        assertTrue(ballot.vote(player, ballot.candidates().get(1).toLowerCase()));
        assertEquals(1, ballot.count(ballot.candidates().get(1)));
        assertEquals(ballot.candidates().get(1),
                ballot.canonicalCandidate(ballot.candidates().get(1).toLowerCase()));
        assertFalse(ballot.vote(player, "Not proposed"));
    }

    @Test
    void choosesOnlyFromCandidatesWhenNoVotesExist() {
        ThemeBallot ballot = new ThemeBallot(List.of("Farm", "Pizza", "Rocket"), 2, new Random(2));
        assertTrue(ballot.candidates().contains(ballot.winner(new Random(3))));
    }

    @Test
    void leavingBeforeStartRemovesTheDepartedPlayersInfluence() {
        ThemeBallot ballot = new ThemeBallot(List.of("Farm", "Pizza"), 2, new Random(2));
        UUID remaining = UUID.randomUUID();
        UUID departed = UUID.randomUUID();
        UUID anotherDeparture = UUID.randomUUID();
        ballot.vote(remaining, "Farm");
        ballot.vote(departed, "Pizza");
        ballot.vote(anotherDeparture, "Pizza");
        assertEquals("Pizza", ballot.winner(new Random(3)));

        ballot.withdraw(departed);
        ballot.withdraw(anotherDeparture);
        assertEquals(0, ballot.count("Pizza"));
        assertEquals("Farm", ballot.winner(new Random(3)));
    }

    @Test
    void departureIsIdempotentAndReturningPlayerCanChooseAgain() {
        ThemeBallot ballot = new ThemeBallot(List.of("Farm", "Pizza"), 2, new Random(2));
        UUID player = UUID.randomUUID();
        ballot.vote(player, "Pizza");
        ballot.withdraw(player);
        ballot.withdraw(player);
        assertEquals(0, ballot.count("Pizza"));

        assertTrue(ballot.vote(player, "Farm"));
        assertEquals(1, ballot.count("Farm"));
        assertEquals("Farm", ballot.winner(new Random(3)));
    }
}

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
}

package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DurationBallotTest {
    @Test
    void proposesSortedValidOptionsAndFallsBackToDefaults() {
        assertEquals(List.of(5, 10, 15), new DurationBallot(List.of(15, 5, 10, 10)).options());
        assertEquals(List.of(3, 8), new DurationBallot(Arrays.asList(8, null, 0, -2, 3, 600)).options());
        assertEquals(DurationBallot.DEFAULT_OPTION_MINUTES, new DurationBallot(List.of()).options());
        assertEquals(DurationBallot.DEFAULT_OPTION_MINUTES, new DurationBallot(null).options());
    }

    @Test
    void withoutAnyVoteTheUsualSoloOrMultiDurationIsKept() {
        DurationBallot ballot = new DurationBallot(List.of(5, 10, 15));
        assertEquals(180, ballot.winnerSeconds(180));
        assertEquals(300, ballot.winnerSeconds(300));
    }

    @Test
    void mostVotedOptionWinsAndPlayersMayChangeTheirBallot() {
        DurationBallot ballot = new DurationBallot(List.of(5, 10, 15));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        assertTrue(ballot.vote(first, 15));
        assertTrue(ballot.vote(second, 15));
        assertTrue(ballot.vote(third, 5));
        assertEquals(900, ballot.winnerSeconds(300));

        assertTrue(ballot.vote(second, 10));
        assertEquals(1, ballot.count(15));
        assertFalse(ballot.vote(first, 7));
        assertFalse(ballot.vote(null, 5));
    }

    @Test
    void aTiePicksTheShortestTiedOption() {
        DurationBallot ballot = new DurationBallot(List.of(5, 10, 15));
        ballot.vote(UUID.randomUUID(), 15);
        ballot.vote(UUID.randomUUID(), 10);
        assertEquals(600, ballot.winnerSeconds(300));
    }

    @Test
    void aLonePlayerVoteOverridesTheSoloDefault() {
        DurationBallot ballot = new DurationBallot(List.of(5, 10, 15));
        ballot.vote(UUID.randomUUID(), 10);
        assertEquals(600, ballot.winnerSeconds(180));
    }

    @Test
    void leavingBeforeStartRemovesTheDepartedPlayersInfluence() {
        DurationBallot ballot = new DurationBallot(List.of(5, 10, 15));
        UUID departed = UUID.randomUUID();
        ballot.vote(departed, 15);
        ballot.withdraw(departed);
        assertEquals(300, ballot.winnerSeconds(300));
    }

    @Test
    void commandArgumentsAcceptPlainOrSuffixedMinutes() {
        assertEquals(10, DurationBallot.parseMinutes("10"));
        assertEquals(10, DurationBallot.parseMinutes(" 10m "));
        assertEquals(15, DurationBallot.parseMinutes("15 min"));
        assertEquals(5, DurationBallot.parseMinutes("5 minutes"));
        assertEquals(-1, DurationBallot.parseMinutes("ten"));
        assertEquals(-1, DurationBallot.parseMinutes(null));
    }

    @Test
    void theVotedDurationIsFixedAtStartAndAnnounced() throws Exception {
        Path path = Path.of("src/main/java/com/cookiebuild/buildbattles/game/BuildBattlesGame.java");
        if (!Files.exists(path)) path = Path.of("BuildBattles").resolve(path);
        String source = Files.readString(path);
        assertTrue(source.contains("buildDurationSeconds = durationBallot.winnerSeconds(LateJoinPolicy.initialBuildSeconds("));
        assertTrue(source.contains("\"bb.duration.chosen\""));
        assertTrue(source.contains("durationBallot.withdraw(id)"));
        // Slot 8 belongs to CookieDough's leave-queue compass, reinstalled after the waiting setup.
        assertEquals(4, BuildBattlesGame.DURATION_FIRST_SLOT);
        assertTrue(source.contains("DURATION_FIRST_SLOT + index < 8"));
    }
}

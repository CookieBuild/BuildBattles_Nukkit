package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

class ThemeBallotTest {
    /** Halloween list delivered by CookieDough's ThemeService (release halloween-20261012 contract). */
    private static final List<String> HALLOWEEN = List.of("Haunted House", "Witch Cauldron", "Graveyard",
            "Pumpkin", "Ghost", "Vampire", "Giant Spider", "Candy", "Halloween", "Skeleton");
    /** Production config.yml themes (Candy, Halloween and Skeleton are also seasonal). */
    private static final List<String> REGULAR = List.of("Farm", "Summer", "Camping", "Volcano", "House",
            "Candle", "Rabbit", "Pizza", "Car", "Emoji", "Smartphone", "Candy", "Birds", "Skyscraper", "Desk",
            "Fast Food", "Dragon", "Snowman", "Restaurant", "Tank", "Nintendo", "Mario", "Clock", "Basketball",
            "Ice Cream", "Shop", "Shoes", "Maze", "Magic", "Winter", "Soccer", "Airplane", "Pokemon", "Minions",
            "Lighthouse", "Console", "Wizard", "Halloween", "Skeleton", "Free Style", "Pixel Art", "Science",
            "Animals", "Alien", "Rocket", "Pyramid", "Upside Down", "Train", "Ruins");

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

    @Test
    void withoutSeasonalThemesTheDrawIsExactlyTheRegularOne() {
        for (long seed = 0; seed < 500; seed++) {
            List<String> expected = legacyDraw(REGULAR, 3, new Random(seed));
            assertEquals(expected, new ThemeBallot(REGULAR, 3, new Random(seed)).candidates());
            assertEquals(expected, new ThemeBallot(REGULAR, List.of(), 3, new Random(seed)).candidates());
            assertEquals(expected, new ThemeBallot(REGULAR, null, 3, new Random(seed)).candidates());
            assertEquals(expected, new ThemeBallot(REGULAR, List.of(" ", ""), 3, new Random(seed)).candidates());
            assertTrue(new ThemeBallot(REGULAR, List.of(), 3, new Random(seed)).seasonalCandidate().isEmpty());
        }
        // The regular draw consumes the shared generator exactly as before.
        Random legacy = new Random(9);
        legacyDraw(REGULAR, 3, legacy);
        Random current = new Random(9);
        new ThemeBallot(REGULAR, List.of(), 3, current);
        assertEquals(legacy.nextLong(), current.nextLong());
    }

    @Test
    void seasonalBallotAlwaysFeaturesExactlyOneSeasonalTheme() {
        Set<String> seasonalKeys = lowerCase(HALLOWEEN);
        Set<String> featured = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (long seed = 0; seed < 2_000; seed++) {
            ThemeBallot ballot = new ThemeBallot(REGULAR, HALLOWEEN, 3, new Random(seed));
            List<String> candidates = ballot.candidates();
            assertEquals(3, candidates.size(), "seed " + seed);
            assertEquals(3, lowerCase(candidates).size(), "duplicate in " + candidates);
            assertTrue(REGULAR.containsAll(candidates.stream().filter(c -> !ballot.isSeasonal(c)).toList()));
            assertEquals(1, candidates.stream().filter(c -> seasonalKeys.contains(c.toLowerCase(Locale.ROOT)))
                    .count(), "seed " + seed + " " + candidates);
            String seasonal = ballot.seasonalCandidate().orElseThrow();
            assertTrue(HALLOWEEN.contains(seasonal));
            assertEquals(1, candidates.stream().filter(ballot::isSeasonal).count());
            featured.add(seasonal);
            positions.add(candidates.indexOf(seasonal));
        }
        assertEquals(Set.copyOf(HALLOWEEN), featured);
        assertEquals(Set.of(0, 1, 2), positions);
    }

    @Test
    void seasonalThemeAlsoInTheRegularPoolIsNeverProposedTwice() {
        for (long seed = 0; seed < 300; seed++) {
            ThemeBallot ballot = new ThemeBallot(List.of("Farm", "Halloween", "Pizza", "Rocket"),
                    List.of("halloween"), 3, new Random(seed));
            List<String> candidates = ballot.candidates();
            assertEquals(3, candidates.size());
            assertEquals(1, candidates.stream().filter(c -> c.equalsIgnoreCase("Halloween")).count(),
                    candidates.toString());
            assertEquals("halloween", ballot.seasonalCandidate().orElseThrow());
        }
    }

    @Test
    void smallRegularPoolKeepsOneSeasonalThemeWithinTheCandidateCount() {
        ThemeBallot onlyOneRegular = new ThemeBallot(List.of("Farm", "Skeleton"), HALLOWEEN, 3, new Random(1));
        assertEquals(2, onlyOneRegular.candidates().size());
        assertTrue(onlyOneRegular.candidates().contains("Farm"));
        assertTrue(onlyOneRegular.seasonalCandidate().isPresent());

        ThemeBallot onlySeasonal = new ThemeBallot(List.of("Halloween", "Skeleton"), HALLOWEEN, 3, new Random(2));
        assertEquals(List.of(onlySeasonal.seasonalCandidate().orElseThrow()), onlySeasonal.candidates());

        ThemeBallot emptyRegular = new ThemeBallot(List.of(), List.of("Pumpkin"), 3, new Random(3));
        assertEquals(List.of("Pumpkin"), emptyRegular.candidates());

        ThemeBallot single = new ThemeBallot(REGULAR, HALLOWEEN, 1, new Random(4));
        assertEquals(1, single.candidates().size());
        assertTrue(single.isSeasonal(single.candidates().get(0)));

        for (int count = 1; count <= 6; count++) {
            assertEquals(count, new ThemeBallot(REGULAR, HALLOWEEN, count, new Random(count)).candidates().size());
        }
    }

    @Test
    void seasonalCandidateIsAnOrdinaryVoteChoice() {
        ThemeBallot ballot = new ThemeBallot(REGULAR, HALLOWEEN, 3, new Random(7));
        String seasonal = ballot.seasonalCandidate().orElseThrow();
        UUID player = UUID.randomUUID();
        assertTrue(ballot.vote(player, seasonal.toUpperCase(Locale.ROOT)));
        assertEquals(1, ballot.count(seasonal));
        assertEquals(seasonal, ballot.winner(new Random(3)));
        assertFalse(ballot.isSeasonal(null));
        assertTrue(HALLOWEEN.stream().filter(theme -> !theme.equals(seasonal))
                .noneMatch(theme -> ballot.canonicalCandidate(theme) != null));
    }

    /** Production draw before the seasonal ballot (a3eb6bc), kept to prove it is unchanged. */
    private static List<String> legacyDraw(List<String> themes, int candidateCount, RandomGenerator random) {
        List<String> pool = new ArrayList<>(new LinkedHashSet<>(themes.stream()
                .filter(theme -> theme != null && !theme.isBlank()).map(String::trim).toList()));
        java.util.Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        return List.copyOf(pool.subList(0, Math.min(candidateCount, pool.size())));
    }

    private static Set<String> lowerCase(List<String> themes) {
        Set<String> result = new HashSet<>();
        themes.forEach(theme -> result.add(theme.toLowerCase(Locale.ROOT)));
        return result;
    }
}

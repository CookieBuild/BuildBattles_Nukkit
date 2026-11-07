package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.theme.ThemeService;
import com.cookiebuild.cookiedough.theme.ThemeServiceFixtures;

class SeasonalThemesTest {
    @Test
    void missingCoreOrInactiveThemeMeansNoSeasonalTheme() {
        // No CookieDough plugin runs in unit tests: the ballot must still be drawn, without a season.
        assertEquals(SeasonalThemes.NONE, SeasonalThemes.current());
        assertEquals(SeasonalThemes.NONE, SeasonalThemes.of(null));
        assertEquals(SeasonalThemes.NONE, SeasonalThemes.of(new ThemeService()));
        assertTrue(SeasonalThemes.NONE.themes().isEmpty());
        assertNull(SeasonalThemes.NONE.seasonId());
    }

    @Test
    void seasonIdIsOnlyKeptWithThemesToFeature() {
        SeasonalThemes halloween = new SeasonalThemes("halloween", Arrays.asList("Pumpkin", null, "Ghost"));
        assertEquals("halloween", halloween.seasonId());
        assertEquals(List.of("Pumpkin", "Ghost"), halloween.themes());

        SeasonalThemes empty = new SeasonalThemes("halloween", List.of());
        assertNull(empty.seasonId());
        assertEquals(SeasonalThemes.NONE, new SeasonalThemes("halloween", null));
    }

    @Test
    void activeChristmasThemeFeedsExactlyOneChristmasThemeIntoEveryBallot() {
        ThemeService service = ThemeServiceFixtures.active("christmas", ThemeBallotTest.CHRISTMAS);
        SeasonalThemes christmas = SeasonalThemes.of(service);
        assertEquals("christmas", christmas.seasonId());
        assertEquals(ThemeBallotTest.CHRISTMAS, christmas.themes());

        Set<String> featured = new HashSet<>();
        for (long seed = 0; seed < 1_000; seed++) {
            ThemeBallot ballot = new ThemeBallot(ThemeBallotTest.REGULAR, christmas.themes(), 3, new Random(seed));
            List<String> candidates = ballot.candidates();
            assertEquals(3, candidates.size());
            assertEquals(1, candidates.stream().filter(ThemeBallotTest.CHRISTMAS::contains).count(),
                    "seed " + seed + " " + candidates);
            featured.add(ballot.seasonalCandidate().orElseThrow());
        }
        assertEquals(Set.copyOf(ThemeBallotTest.CHRISTMAS), featured);
    }
}

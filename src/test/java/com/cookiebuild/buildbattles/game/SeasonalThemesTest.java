package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.theme.ThemeService;

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
}

package com.cookiebuild.buildbattles.game;

import java.util.List;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.theme.ThemeService;

/**
 * Seasonal Build Battle themes read from CookieDough's {@link ThemeService} when a ballot is drawn.
 * Any missing piece (core not running, no service, older core, failure) means "no seasonal theme",
 * so drawing a ballot never fails because of the season.
 */
record SeasonalThemes(String seasonId, List<String> themes) {
    static final SeasonalThemes NONE = new SeasonalThemes(null, List.of());

    SeasonalThemes {
        themes = themes == null ? List.of() : themes.stream().filter(theme -> theme != null).toList();
        if (themes.isEmpty()) seasonId = null;
    }

    static SeasonalThemes current() {
        try {
            CookieDough core = CookieDough.getInstance();
            return core == null ? NONE : of(core.getThemeService());
        } catch (RuntimeException | LinkageError failure) {
            BuildBattles plugin = BuildBattles.getInstance();
            if (plugin != null) plugin.getLogger().warning("Seasonal themes unavailable: " + failure);
            return NONE;
        }
    }

    static SeasonalThemes of(ThemeService service) {
        if (service == null) return NONE;
        List<String> themes = service.buildBattleThemes();
        if (themes == null || themes.isEmpty()) return NONE;
        return new SeasonalThemes(service.activeThemeId().orElse(null), themes);
    }
}

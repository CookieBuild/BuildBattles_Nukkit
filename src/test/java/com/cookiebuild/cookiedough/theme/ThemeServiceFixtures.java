package com.cookiebuild.cookiedough.theme;

import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Test-only access to CookieDough's {@link ThemeService}: an active theme built through the public
 * {@link ThemeDefinition#parse} entry point, as the theme controller does from {@code theme.yml}.
 */
public final class ThemeServiceFixtures {
    private ThemeServiceFixtures() {
    }

    /** A service whose active theme {@code id} features {@code buildBattleThemes} (Paris window of the id). */
    public static ThemeService active(String id, List<String> buildBattleThemes) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", id);
        yaml.set("window.start", "2026-12-01T00:00");
        yaml.set("window.end", "2027-01-05T00:00");
        yaml.set("build-battle-themes", buildBattleThemes);
        ThemeService service = new ThemeService();
        service.setActive(ThemeDefinition.parse(yaml));
        return service;
    }
}

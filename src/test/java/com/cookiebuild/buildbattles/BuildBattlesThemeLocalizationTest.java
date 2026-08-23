package com.cookiebuild.buildbattles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

class BuildBattlesThemeLocalizationTest {
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    @Test
    void everyConfiguredThemeHasALocalizedDisplayNameInCompleteLocales() throws IOException {
        Path config = Path.of("src/main/resources/config.yml");
        if (!Files.exists(config)) config = Path.of("BuildBattles").resolve(config);
        List<String> lines = Files.readAllLines(config);
        int themesLine = lines.indexOf("themes:");
        List<String> themes = lines.subList(themesLine + 1, lines.size()).stream()
                .filter(line -> line.startsWith("  - "))
                .map(line -> line.substring(4).trim())
                .toList();
        assertEquals(49, themes.size());

        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"),
                Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("buildbattles_messages", locale,
                    BuildBattlesThemeLocalizationTest.class.getClassLoader(), NO_FALLBACK);
            for (String theme : themes) {
                String key = BuildBattles.themeMessageKey(theme);
                assertTrue(bundle.containsKey(key), locale + " missing " + key);
                assertFalse(bundle.getString(key).isBlank(), locale + " blank " + key);
            }
        }
    }

    @Test
    void displayLocalizationNeverChangesStableThemeValues() {
        assertEquals("bb.theme.name.fast_food", BuildBattles.themeMessageKey("Fast Food"));
        assertEquals("Restauration rapide", BuildBattles.themeNameForLocale(Locale.FRENCH, "Fast Food"));
        assertEquals("Бързо хранене", BuildBattles.themeNameForLocale(Locale.of("bg"), "Fast Food"));
        assertEquals("Custom Event Theme", BuildBattles.themeNameForLocale(
                Locale.of("hi"), "Custom Event Theme"));
    }

    @Test
    void partialAndUnknownLocalesUseReadableEnglishInsteadOfTechnicalKeys() {
        assertEquals("Blocks: §a12", BuildBattles.messageForLocale(
                Locale.GERMAN, "bb.scoreboard.blocks", 12));
        assertEquals("Close", BuildBattles.messageForLocale(
                Locale.JAPANESE, "bb.form.close"));
        assertFalse(BuildBattles.messageForLocale(Locale.GERMAN, "bb.unknown.technical.key")
                .contains("bb.unknown"));
    }
}

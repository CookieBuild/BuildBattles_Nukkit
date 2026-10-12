package com.cookiebuild.buildbattles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

import com.cookiebuild.buildbattles.gallery.GalleryCapturePolicy;

class BuildBattlesThemeLocalizationTest {
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    /** Every shipped bundle, the partial Punjabi one included. */
    private static final List<Locale> ALL_LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"),
            Locale.of("pt", "BR"), Locale.of("bg"), Locale.of("hi"), Locale.GERMAN, Locale.ITALIAN, Locale.of("pa"));
    /** Latin-script bundles: their names go to Bedrock forms and must stay Latin-1. */
    private static final List<Locale> LATIN_LOCALES = List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"),
            Locale.of("pt", "BR"), Locale.GERMAN, Locale.ITALIAN);
    /** Halloween list delivered by CookieDough's ThemeService (release halloween-20261012 contract). */
    private static final List<String> HALLOWEEN = List.of("Haunted House", "Witch Cauldron", "Graveyard",
            "Pumpkin", "Ghost", "Vampire", "Giant Spider", "Candy", "Halloween", "Skeleton");

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

        for (Locale locale : ALL_LOCALES) {
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
        // Punjabi is shipped partially: missing keys fall back to readable English.
        assertEquals("Blocks: §a12", BuildBattles.messageForLocale(
                Locale.of("pa"), "bb.scoreboard.blocks", 12));
        assertEquals("Blöcke: §a12", BuildBattles.messageForLocale(
                Locale.GERMAN, "bb.scoreboard.blocks", 12));
        assertEquals("Close", BuildBattles.messageForLocale(
                Locale.JAPANESE, "bb.form.close"));
        assertFalse(BuildBattles.messageForLocale(Locale.GERMAN, "bb.unknown.technical.key")
                .contains("bb.unknown"));
    }

    @Test
    void everySeasonalThemeHasALocalizedNameInEveryLocale() {
        for (Locale locale : ALL_LOCALES) {
            ResourceBundle bundle = bundle(locale);
            for (String theme : HALLOWEEN) {
                String key = BuildBattles.themeMessageKey(theme);
                assertTrue(bundle.containsKey(key), locale + " missing " + key);
                String name = bundle.getString(key);
                assertFalse(name.isBlank(), locale + " blank " + key);
                assertTrue(name.length() <= 24, locale + " too long for the scoreboard: " + name);
                assertTrue(name.codePoints().noneMatch(BuildBattlesThemeLocalizationTest::isEmojiLike),
                        locale + " emoji in " + name);
                if (LATIN_LOCALES.contains(locale)) {
                    assertTrue(StandardCharsets.ISO_8859_1.newEncoder().canEncode(name),
                            locale + " not Latin-1: " + name);
                }
            }
            String season = bundle.getString("bb.theme.season.halloween");
            assertFalse(season.isBlank(), locale + " blank season");
            assertTrue(season.codePoints().noneMatch(BuildBattlesThemeLocalizationTest::isEmojiLike));
            assertTrue(bundle.getString("bb.theme.seasonal_candidate").contains("{0}"));
            assertTrue(bundle.getString("bb.theme.seasonal_candidate").contains("{1}"));
        }
        assertEquals("Maison hantée", BuildBattles.themeNameForLocale(Locale.FRENCH, "Haunted House"));
        assertEquals("Chaudron de sorcière", BuildBattles.themeNameForLocale(Locale.FRENCH, "Witch Cauldron"));
        assertEquals("Araignée géante", BuildBattles.themeNameForLocale(Locale.FRENCH, "Giant Spider"));
    }

    @Test
    void seasonalThemesStayWithinTheGalleryColumns() {
        for (String theme : HALLOWEEN) {
            String key = GalleryCapturePolicy.themeKey(theme);
            assertTrue(key.matches("[a-z0-9_]{1,64}"), key);
            String englishName = BuildBattles.englishThemeName(theme);
            assertFalse(englishName.isBlank());
            assertTrue(englishName.length() <= 80, englishName);
            assertFalse(englishName.startsWith("bb."), englishName);
        }
        assertEquals("haunted_house", GalleryCapturePolicy.themeKey("Haunted House"));
        assertEquals("witch_cauldron", GalleryCapturePolicy.themeKey("Witch Cauldron"));
        assertEquals("giant_spider", GalleryCapturePolicy.themeKey("Giant Spider"));
        assertEquals("Witch's Cauldron", BuildBattles.englishThemeName("Witch Cauldron"));
    }

    @Test
    void onlyTheSeasonalCandidateIsLabelledWithItsSeason() {
        assertEquals("Halloween : Maison hantée",
                BuildBattles.ballotLabelForLocale(Locale.FRENCH, "Haunted House", "halloween"));
        assertEquals("Halloween: Candy", BuildBattles.ballotLabelForLocale(Locale.ENGLISH, "Candy", "halloween"));
        assertEquals("Хелоуин: Тиква", BuildBattles.ballotLabelForLocale(Locale.of("bg"), "Pumpkin", "halloween"));
        // The Halloween theme itself is not labelled "Halloween : Halloween".
        assertEquals("Halloween", BuildBattles.ballotLabelForLocale(Locale.FRENCH, "Halloween", "halloween"));
        assertNull(BuildBattles.seasonTagForLocale(Locale.of("hi"), "Halloween", "halloween"));
        assertEquals("Halloween", BuildBattles.seasonTagForLocale(Locale.GERMAN, "Ghost", "halloween"));
        // Regular candidates, no season or an unnamed season: the plain localized name.
        assertEquals("Ferme", BuildBattles.ballotLabelForLocale(Locale.FRENCH, "Farm", null));
        assertEquals("Ferme", BuildBattles.ballotLabelForLocale(Locale.FRENCH, "Farm", " "));
        assertEquals("Ferme", BuildBattles.ballotLabelForLocale(Locale.FRENCH, "Farm", "unnamed_season"));
        assertNull(BuildBattles.seasonTagForLocale(Locale.FRENCH, "Farm", "unnamed_season"));
        // Unknown locales fall back to English.
        assertEquals("Halloween: Haunted House",
                BuildBattles.ballotLabelForLocale(Locale.JAPANESE, "Haunted House", "halloween"));
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("buildbattles_messages", locale,
                BuildBattlesThemeLocalizationTest.class.getClassLoader(), NO_FALLBACK);
    }

    /** Emoji and pictographs Bedrock forms and nameplates cannot render. */
    private static boolean isEmojiLike(int codePoint) {
        return Character.isSupplementaryCodePoint(codePoint) || Character.getType(codePoint) == Character.OTHER_SYMBOL
                || (codePoint >= 0x2600 && codePoint <= 0x27BF) || codePoint == 0xFE0F;
    }
}

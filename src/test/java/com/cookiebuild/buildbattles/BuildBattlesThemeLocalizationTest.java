package com.cookiebuild.buildbattles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import org.bukkit.configuration.file.YamlConfiguration;
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
    /** Christmas list of the christmas-20261107 contract (Snowman is also a regular theme). */
    private static final List<String> CHRISTMAS = List.of("Snowman", "Christmas Tree", "Santa Claus", "Reindeer",
            "Gingerbread House", "Gift", "Sleigh", "Igloo", "Penguin", "Fireplace");
    private static final Map<String, List<String>> SEASONS = Map.of("halloween", HALLOWEEN, "christmas", CHRISTMAS);

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
            SEASONS.forEach((seasonId, themes) -> assertSeasonNamed(locale, bundle, seasonId, themes));
            assertTrue(bundle.getString("bb.theme.seasonal_candidate").contains("{0}"));
            assertTrue(bundle.getString("bb.theme.seasonal_candidate").contains("{1}"));
        }
        assertEquals("Maison hantée", BuildBattles.themeNameForLocale(Locale.FRENCH, "Haunted House"));
        assertEquals("Chaudron de sorcière", BuildBattles.themeNameForLocale(Locale.FRENCH, "Witch Cauldron"));
        assertEquals("Araignée géante", BuildBattles.themeNameForLocale(Locale.FRENCH, "Giant Spider"));
    }

    @Test
    void christmasThemesHaveShortNaturalNames() {
        assertEquals("Sapin de Noël", BuildBattles.themeNameForLocale(Locale.FRENCH, "Christmas Tree"));
        assertEquals("Père Noël", BuildBattles.themeNameForLocale(Locale.FRENCH, "Santa Claus"));
        assertEquals("Maison en pain d'épices", BuildBattles.themeNameForLocale(Locale.FRENCH, "Gingerbread House"));
        assertEquals("Traîneau", BuildBattles.themeNameForLocale(Locale.FRENCH, "Sleigh"));
        assertEquals("Weihnachtsmann", BuildBattles.themeNameForLocale(Locale.GERMAN, "Santa Claus"));
        assertEquals("Papai Noel", BuildBattles.themeNameForLocale(Locale.of("pt", "BR"), "Santa Claus"));
        assertEquals("Fireplace", BuildBattles.englishThemeName("Fireplace"));
        // Snowman was already a regular theme: Christmas reuses its existing key and names.
        assertEquals("bb.theme.name.snowman", BuildBattles.themeMessageKey("Snowman"));
        assertEquals("Bonhomme de neige", BuildBattles.themeNameForLocale(Locale.FRENCH, "Snowman"));
        // Each Christmas theme has its own key: no two themes share a display key.
        assertEquals(CHRISTMAS.size(), CHRISTMAS.stream().map(BuildBattles::themeMessageKey).distinct().count());
        assertEquals("Noël : Sapin de Noël",
                BuildBattles.ballotLabelForLocale(Locale.FRENCH, "Christmas Tree", "christmas"));
        assertEquals("Noël : Bonhomme de neige",
                BuildBattles.ballotLabelForLocale(Locale.FRENCH, "Snowman", "christmas"));
        assertEquals("Christmas: Reindeer", BuildBattles.ballotLabelForLocale(Locale.ENGLISH, "Reindeer", "christmas"));
        assertEquals("Коледа: Шейна", BuildBattles.ballotLabelForLocale(Locale.of("bg"), "Sleigh", "christmas"));
        assertEquals("Christmas: Penguin", BuildBattles.ballotLabelForLocale(Locale.JAPANESE, "Penguin", "christmas"));
    }

    /**
     * Every Build Battle theme listed by a seasonal theme bundled in CookieDough
     * ({@code themes/index.yml} then {@code themes/<id>/theme.yml}) is translated in every bundle,
     * so a season added to the core cannot show a raw English name or an unnamed season.
     */
    @Test
    void everyBundledSeasonalThemeIsTranslated() throws IOException {
        ClassLoader loader = BuildBattlesThemeLocalizationTest.class.getClassLoader();
        List<String> ids = yamlList(loader, "themes/index.yml", "themes");
        assertTrue(ids.contains("halloween"), ids.toString());
        for (String id : ids) {
            List<String> themes = yamlList(loader, "themes/" + id + "/theme.yml", "build-battle-themes");
            if (themes.isEmpty()) continue;
            if (SEASONS.containsKey(id)) assertEquals(Set.copyOf(SEASONS.get(id)), Set.copyOf(themes), id);
            for (Locale locale : ALL_LOCALES) assertSeasonNamed(locale, bundle(locale), id, themes);
        }
    }

    private static List<String> yamlList(ClassLoader loader, String resource, String key) throws IOException {
        try (InputStream in = loader.getResourceAsStream(resource)) {
            assertNotNull(in, "missing " + resource);
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml.getStringList(key);
        }
    }

    private static void assertSeasonNamed(Locale locale, ResourceBundle bundle, String seasonId, List<String> themes) {
        for (String theme : themes) {
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
        String seasonKey = "bb.theme.season." + seasonId;
        assertTrue(bundle.containsKey(seasonKey), locale + " missing " + seasonKey);
        String season = bundle.getString(seasonKey);
        assertFalse(season.isBlank(), locale + " blank season");
        assertTrue(season.codePoints().noneMatch(BuildBattlesThemeLocalizationTest::isEmojiLike));
    }

    @Test
    void seasonalThemesStayWithinTheGalleryColumns() {
        for (String theme : SEASONS.values().stream().flatMap(List::stream).toList()) {
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
        assertEquals("christmas_tree", GalleryCapturePolicy.themeKey("Christmas Tree"));
        assertEquals("santa_claus", GalleryCapturePolicy.themeKey("Santa Claus"));
        assertEquals("gingerbread_house", GalleryCapturePolicy.themeKey("Gingerbread House"));
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

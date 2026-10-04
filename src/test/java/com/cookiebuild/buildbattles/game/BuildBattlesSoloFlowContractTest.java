package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

/** Solo Build Battle (start after 15 s, 180 s build, late join, gallery) must read as such. */
class BuildBattlesSoloFlowContractTest {
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final List<Locale> SHIPPED = List.of(Locale.ENGLISH, Locale.FRENCH, Locale.GERMAN,
            Locale.ITALIAN, Locale.of("bg"), Locale.of("es"), Locale.of("hi"), Locale.of("pt", "BR"));

    private static String source() throws Exception {
        Path path = Path.of("src/main/java/com/cookiebuild/buildbattles/game/BuildBattlesGame.java");
        if (!Files.exists(path)) path = Path.of("BuildBattles").resolve(path);
        return Files.readString(path);
    }

    private static ResourceBundle bundle(Locale locale) {
        return ResourceBundle.getBundle("buildbattles_messages", locale,
                BuildBattlesSoloFlowContractTest.class.getClassLoader(), NO_FALLBACK);
    }

    private static String section(String source, String start) {
        int from = source.indexOf(start);
        assertTrue(from >= 0, start);
        int next = source.indexOf("\n    }\n", from);
        return source.substring(from, next);
    }

    @Test
    void everyShippedLocaleHasItsOwnBundleAndNeverShowsTheInternalModeId() {
        for (Locale locale : SHIPPED) {
            ResourceBundle bundle = bundle(locale);
            assertEquals(locale.getLanguage(), bundle.getLocale().getLanguage(), "own bundle for " + locale);
            for (String key : bundle.keySet()) {
                assertFalse(bundle.getString(key).contains("BuildBattles"), locale + " " + key);
            }
        }
    }

    @Test
    void theWaitingPhaseNoLongerSaysItWaitsForPlayers() {
        assertEquals("Avant le départ", bundle(Locale.FRENCH).getString("bb.phase.waiting"));
        assertEquals("Getting ready", bundle(Locale.ENGLISH).getString("bb.phase.waiting"));
        for (Locale locale : SHIPPED) {
            String subtitle = bundle(locale).getString("bb.waiting.subtitle");
            assertFalse(subtitle.contains("players join") || subtitle.contains("l’arrivée des joueurs"),
                    locale + ": " + subtitle);
        }
    }

    @Test
    void theLoneWaitersActionBarCountsToTheRealStart() throws Exception {
        String display = section(source(), "private void updateDisplay()");
        assertTrue(display.contains("getSoloStartEtaSeconds()"), "solo ETA must come from the core countdown model");
        assertFalse(source().contains("waitingSince"), "no second, drifting waiting clock");
    }

    @Test
    void aLateJoinerIntoASoloBuildTurnsRatingsBackOnForTheLoneBuilder() throws Exception {
        String admission = section(source(), "private synchronized boolean admitLateJoiner");
        assertTrue(admission.indexOf("int buildersBefore = remainingBuilders();")
                < admission.indexOf("playerByPlot.put(plot, id);"));
        assertTrue(admission.contains("buildersBefore == 1"));
        assertTrue(admission.contains("\"bb.late_join.rating_on\""));
    }

    @Test
    void theSoloEndScreenOnlyPromisesTheGalleryForPublishedBuilds() throws Exception {
        String source = source();
        String ending = section(source, "private void finishWithoutJudging");
        assertTrue(ending.contains("!galleryOptedOut.contains(player.getUniqueId())"));
        assertFalse(ending.contains("bb.solo.gallery_message"), "the publish notice waits for the stored row");
        String announce = section(source, "private void announceGalleryBuild");
        assertTrue(announce.indexOf("saved.published()") < announce.indexOf("bb.solo.gallery_message"));
    }

    @Test
    void theEndScreenSidebarHasNoFloorHintAndTheTitleIsReadable() throws Exception {
        String footer = section(source(), "private String scoreboardFooter");
        assertTrue(footer.contains("case RESULTS, FINISHED -> \" \";"));
        assertEquals("Build Battle", BuildBattlesScoreboard.TITLE);
    }
}

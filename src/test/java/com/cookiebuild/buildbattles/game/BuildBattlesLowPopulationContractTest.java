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

class BuildBattlesLowPopulationContractTest {
    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private static String source() throws Exception {
        Path path = Path.of("src/main/java/com/cookiebuild/buildbattles/game/BuildBattlesGame.java");
        if (!Files.exists(path)) path = Path.of("BuildBattles").resolve(path);
        return Files.readString(path);
    }

    @Test
    void soloStartHookIsFifteenSecondsAndStartAcceptsOnePlayer() throws Exception {
        String source = source();
        assertEquals(15, BuildBattlesGame.SOLO_START_AFTER_SECONDS);
        assertTrue(source.contains("protected int soloStartAfterSeconds()"));
        assertTrue(source.contains("if (starters.isEmpty() || starters.size() > map.template().capacity()) return;"));
        assertFalse(source.contains("starters.size() < 2"));
    }

    @Test
    void queueEntryTriesARunningMatchBeforeTheWaitingLobby() throws Exception {
        String source = source();
        String entry = source.substring(source.indexOf("public boolean addPlayerToAvailableTeam"));
        assertTrue(entry.indexOf("tryLateJoinRunningMatch(player)") < entry.indexOf("return addPlayer(player)"));
        String admission = source.substring(source.indexOf("private synchronized boolean admitLateJoiner"));
        assertTrue(admission.indexOf("tryTeleportPlayerSafely") < admission.indexOf("restorePlayerAfterReconnect"));
        assertTrue(admission.indexOf("restorePlayerAfterReconnect") < admission.indexOf("plotByPlayer.put"));
    }

    @Test
    void shortHandedChecksRunBeforeTimersAndSoloSkipsJudging() throws Exception {
        String source = source();
        String tick = source.substring(source.indexOf("public void tick()"));
        assertTrue(tick.indexOf("stopAbandonedMatch()") < tick.indexOf("finishShortHandedMatch()"));
        assertTrue(tick.indexOf("finishShortHandedMatch()") < tick.indexOf("phaseSeconds++"));
        assertTrue(tick.indexOf("Outcome.SOLO_RESULT") < tick.indexOf("startJudging()"));
    }

    @Test
    void lowPopulationCopyExistsInEveryShippedLocale() {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRENCH, Locale.of("es"), Locale.of("pt", "BR"),
                Locale.of("bg"), Locale.of("hi"), Locale.GERMAN, Locale.of("pa"))) {
            ResourceBundle bundle = ResourceBundle.getBundle("buildbattles_messages", locale,
                    BuildBattlesLowPopulationContractTest.class.getClassLoader(), NO_FALLBACK);
            for (String key : List.of("bb.waiting.solo", "bb.howto", "bb.howto.solo", "bb.late_join.subtitle",
                    "bb.late_join.announce", "bb.solo.title", "bb.solo.subtitle", "bb.forfeit.victory.title",
                    "bb.forfeit.victory.subtitle", "bb.forfeit.finished.title", "bb.forfeit.finished.subtitle")) {
                assertTrue(bundle.containsKey(key) && !bundle.getString(key).isBlank(), locale + " " + key);
            }
        }
        ResourceBundle french = ResourceBundle.getBundle("buildbattles_messages", Locale.FRENCH,
                BuildBattlesLowPopulationContractTest.class.getClassLoader(), NO_FALLBACK);
        assertEquals("Ta construction est enregistrée !", french.getString("bb.solo.title"));
    }
}

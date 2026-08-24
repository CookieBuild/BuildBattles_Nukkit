package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BuildBattlesContinuityContractTest {
    @Test
    void spectatorQuitBypassesParticipantReconnectAndCleanupEjectsBeforeUnload() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/buildbattles/game/BuildBattlesGame.java"));
        String removal = source.substring(source.indexOf("public synchronized void removePlayer"));
        assertTrue(source.contains("implements ReconnectableGame"));
        assertTrue(source.contains("public boolean supportsSpectating()"));
        assertTrue(source.contains("protected Location spectatorDestination"));
        assertTrue(removal.indexOf("getSpectators().stream()")
                < removal.indexOf("if (phase == BuildPhase.WAITING)"));
        assertTrue(source.indexOf("if (!ejectOwnedPlayersToLobby())")
                < source.indexOf("MapManager.unload(getGameId())"));
    }
}

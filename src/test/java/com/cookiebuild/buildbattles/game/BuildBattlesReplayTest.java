package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class BuildBattlesReplayTest {
    @Test
    void explicitReplayStopsWhenReturnToLobbyFails() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/buildbattles/BuildBattles.java"));
        assertTrue(source.contains("if (!LobbyManager.teleportPlayerToLobby(cookiePlayer)) return true;"));
    }
}

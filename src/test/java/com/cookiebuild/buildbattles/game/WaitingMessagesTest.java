package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

class WaitingMessagesTest {
    @Test
    void keepsTheSharedEnglishWaitingVocabularyAndTemporaryAreaLabel() {
        ResourceBundle bundle = ResourceBundle.getBundle("buildbattles_messages", Locale.ENGLISH);

        assertEquals("WAITING LOBBY", bundle.getString("bb.waiting.title"));
        assertEquals("Waiting for players", bundle.getString("bb.phase.waiting"));
        assertTrue(bundle.getString("bb.waiting.players").startsWith("Waiting for players"));
        assertTrue(bundle.getString("bb.waiting.starting").startsWith("Starting in {0}s"));
        assertTrue(bundle.getString("bb.waiting.area").contains("temporary waiting area"));
    }
}

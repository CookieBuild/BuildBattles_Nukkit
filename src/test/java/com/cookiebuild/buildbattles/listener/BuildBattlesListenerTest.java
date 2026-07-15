package com.cookiebuild.buildbattles.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

class BuildBattlesListenerTest {
    @Test
    void acceptsRightClickInAirEvenWhenPaperPredictedNoVanillaAction() throws Exception {
        EventHandler handler = BuildBattlesListener.class
                .getMethod("onInteract", PlayerInteractEvent.class)
                .getAnnotation(EventHandler.class);

        assertFalse(handler.ignoreCancelled(),
                "Paper pre-cancels no-op air interactions; vote items must still receive them");
        assertTrue(BuildBattlesListener.isVoteUseAction(Action.RIGHT_CLICK_AIR));
        assertTrue(BuildBattlesListener.isVoteUseAction(Action.RIGHT_CLICK_BLOCK));
        assertFalse(BuildBattlesListener.isVoteUseAction(Action.LEFT_CLICK_AIR));
        assertFalse(BuildBattlesListener.isVoteUseAction(Action.LEFT_CLICK_BLOCK));
    }
}

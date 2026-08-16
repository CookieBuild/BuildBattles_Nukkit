package com.cookiebuild.buildbattles.listener;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import com.cookiebuild.buildbattles.game.BuildPhase;

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

    @Test
    void paletteShortcutUsesEitherRightClickButNeverAPlaceOrBreakClick() {
        assertTrue(BuildBattlesListener.isPaletteShortcutUse(Action.RIGHT_CLICK_AIR));
        assertTrue(BuildBattlesListener.isPaletteShortcutUse(Action.RIGHT_CLICK_BLOCK));
        assertFalse(BuildBattlesListener.isPaletteShortcutUse(Action.LEFT_CLICK_AIR));
        assertFalse(BuildBattlesListener.isPaletteShortcutUse(Action.LEFT_CLICK_BLOCK));
    }

    @Test
    void exposesDedicatedFailClosedEntityHandlers() throws Exception {
        assertTrue(BuildBattlesListener.class.getMethod("onEntityPlace", EntityPlaceEvent.class)
                .isAnnotationPresent(EventHandler.class));
        assertTrue(BuildBattlesListener.class.getMethod("onHangingPlace", HangingPlaceEvent.class)
                .isAnnotationPresent(EventHandler.class));
        assertTrue(BuildBattlesListener.class.getMethod("onProjectileLaunch", ProjectileLaunchEvent.class)
                .isAnnotationPresent(EventHandler.class));
    }

    @Test
    void onlyAllowsManualBlockInteractionWhileBuildingInsideOwnPlot() {
        assertTrue(BuildBattlesListener.canInteractWithBuildBlock(BuildPhase.BUILDING, true));
        assertFalse(BuildBattlesListener.canInteractWithBuildBlock(BuildPhase.BUILDING, false));
        assertFalse(BuildBattlesListener.canInteractWithBuildBlock(BuildPhase.JUDGING, true));
        assertFalse(BuildBattlesListener.canInteractWithBuildBlock(BuildPhase.RESULTS, true));
    }

    @Test
    void keepsTrackedEntitiesReservedAcrossChunkUnload() {
        assertFalse(BuildBattlesListener.shouldReleaseEntity(EntityRemoveEvent.Cause.UNLOAD));
        assertTrue(BuildBattlesListener.shouldReleaseEntity(EntityRemoveEvent.Cause.PLUGIN));
        assertTrue(BuildBattlesListener.shouldReleaseEntity(EntityRemoveEvent.Cause.HIT));
    }
}

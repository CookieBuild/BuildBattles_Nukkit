package com.cookiebuild.buildbattles.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class BuildSafetyPolicyTest {
    @Test
    void allowsDecorativeBlocksAndBlocksGriefOrLagItems() {
        assertTrue(BuildSafetyPolicy.isSafeBuildingBlock(Material.BRICKS));
        assertTrue(BuildSafetyPolicy.isSafeBuildingBlock(Material.BLUE_WOOL));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.TNT));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.COMMAND_BLOCK));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.LAVA_BUCKET));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.ZOMBIE_SPAWN_EGG));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.HOPPER));
        assertTrue(BuildSafetyPolicy.isSafeBuildingBlock(Material.REDSTONE_WIRE));
        assertTrue(BuildSafetyPolicy.isSafeBuildingBlock(Material.REPEATER));
        assertTrue(BuildSafetyPolicy.isSafeBuildingBlock(Material.COMPARATOR));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.PISTON));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.OBSERVER));
        assertFalse(BuildSafetyPolicy.isSafeBuildingBlock(Material.NOTE_BLOCK));
    }

    @Test
    void onlyAllowsExplicitPassiveSpawnEggsAndDecorations() {
        assertTrue(BuildSafetyPolicy.isDecorationItem(Material.ITEM_FRAME));
        assertTrue(BuildSafetyPolicy.isDecorationItem(Material.GLOW_ITEM_FRAME));
        assertTrue(BuildSafetyPolicy.isDecorationItem(Material.ARMOR_STAND));
        assertTrue(BuildSafetyPolicy.isSafeBuildItem(Material.VILLAGER_SPAWN_EGG));
        assertTrue(BuildSafetyPolicy.isSafeBuildItem(Material.COW_SPAWN_EGG));
        assertFalse(BuildSafetyPolicy.isSafeBuildItem(Material.ZOMBIE_SPAWN_EGG));
        assertFalse(BuildSafetyPolicy.isSafeBuildItem(Material.MINECART));
        assertFalse(BuildSafetyPolicy.isSafeBuildItem(Material.END_CRYSTAL));
        assertTrue(BuildSafetyPolicy.isAllowedPassiveEntityName("VILLAGER"));
        assertFalse(BuildSafetyPolicy.isAllowedPassiveEntityName("ZOMBIE"));
    }

    @Test
    void excludesRedstoneFromBulkFloorChanges() {
        assertTrue(BuildSafetyPolicy.isSafeFloorBlock(Material.BRICKS));
        assertFalse(BuildSafetyPolicy.isSafeFloorBlock(Material.REDSTONE_BLOCK));
        assertFalse(BuildSafetyPolicy.isSafeFloorBlock(Material.REDSTONE_WIRE));
    }

    @Test
    void onlyAllowsLavaAsAPlotBucket() {
        assertTrue(BuildSafetyPolicy.isAllowedPlotBucket(Material.LAVA_BUCKET));
        assertFalse(BuildSafetyPolicy.isAllowedPlotBucket(Material.WATER_BUCKET));
        assertFalse(BuildSafetyPolicy.isAllowedPlotBucket(Material.POWDER_SNOW_BUCKET));
        assertFalse(BuildSafetyPolicy.isAllowedPlotBucket(Material.BUCKET));
    }
}

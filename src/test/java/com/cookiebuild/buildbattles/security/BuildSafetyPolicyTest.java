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
    }

    @Test
    void onlyAllowsLavaAsAPlotBucket() {
        assertTrue(BuildSafetyPolicy.isAllowedPlotBucket(Material.LAVA_BUCKET));
        assertFalse(BuildSafetyPolicy.isAllowedPlotBucket(Material.WATER_BUCKET));
        assertFalse(BuildSafetyPolicy.isAllowedPlotBucket(Material.POWDER_SNOW_BUCKET));
        assertFalse(BuildSafetyPolicy.isAllowedPlotBucket(Material.BUCKET));
    }
}

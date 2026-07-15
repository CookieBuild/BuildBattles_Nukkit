package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class FloorMaterialResolverTest {
    @Test
    void explicitArgumentOverridesEmptyHandAndAcceptsNamespacedBlocks() {
        assertEquals(Material.SMOOTH_STONE,
                FloorMaterialResolver.resolve(new String[] {"smooth_stone"}, Material.AIR));
        assertEquals(Material.BLUE_WOOL,
                FloorMaterialResolver.resolve(new String[] {"minecraft:blue_wool"}, Material.AIR));
        assertEquals(Material.BRICKS, FloorMaterialResolver.resolve(new String[0], Material.BRICKS));
        assertNull(FloorMaterialResolver.resolve(new String[] {"not_a_real_block"}, Material.STONE));
    }

    @Test
    void completionOnlyOffersSafePlaceableBlocks() {
        assertTrue(FloorMaterialResolver.suggestions("smooth_st", material -> material == Material.SMOOTH_STONE)
                .contains("smooth_stone"));
        assertFalse(FloorMaterialResolver.suggestions("tnt", material -> true).contains("tnt"));
        assertFalse(FloorMaterialResolver.suggestions("lava", material -> true).contains("lava"));
    }
}

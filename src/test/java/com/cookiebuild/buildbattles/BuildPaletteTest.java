package com.cookiebuild.buildbattles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;

class BuildPaletteTest {
    @Test
    void exposesACompactDistinctPaletteMadeOnlyOfAllowedItems() {
        assertTrue(BuildPalette.items().size() <= 27);
        assertEquals(BuildPalette.items().size(), new HashSet<>(BuildPalette.items()).size());
        assertTrue(BuildPalette.items().stream().allMatch(BuildSafetyPolicy::isSafeBuildItem));
    }

    @Test
    void coversDecorationRedstoneLavaAndPassiveMobs() {
        assertTrue(BuildPalette.items().contains(Material.PAINTING));
        assertTrue(BuildPalette.items().contains(Material.REDSTONE));
        assertTrue(BuildPalette.items().contains(Material.LAVA_BUCKET));
        assertTrue(BuildPalette.items().contains(Material.AXOLOTL_SPAWN_EGG));
    }
}

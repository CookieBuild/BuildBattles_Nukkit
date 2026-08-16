package com.cookiebuild.buildbattles;

import java.util.List;

import org.bukkit.Material;

/**
 * A compact palette for controls that do not expose the full creative inventory
 * reliably, especially Bedrock clients connected through Geyser.
 */
final class BuildPalette {
    private static final List<Material> ITEMS = List.of(
            Material.ITEM_FRAME,
            Material.GLOW_ITEM_FRAME,
            Material.PAINTING,
            Material.ARMOR_STAND,
            Material.REDSTONE,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.REDSTONE_TORCH,
            Material.REDSTONE_LAMP,
            Material.LEVER,
            Material.TARGET,
            Material.LIGHTNING_ROD,
            Material.LAVA_BUCKET,
            Material.VILLAGER_SPAWN_EGG,
            Material.COW_SPAWN_EGG,
            Material.SHEEP_SPAWN_EGG,
            Material.CAT_SPAWN_EGG,
            Material.WOLF_SPAWN_EGG,
            Material.BEE_SPAWN_EGG,
            Material.FROG_SPAWN_EGG,
            Material.AXOLOTL_SPAWN_EGG);

    private BuildPalette() {
    }

    static List<Material> items() {
        return ITEMS;
    }
}

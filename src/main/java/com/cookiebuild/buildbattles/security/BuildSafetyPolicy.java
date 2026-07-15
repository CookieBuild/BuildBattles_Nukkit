package com.cookiebuild.buildbattles.security;

import java.util.Set;

import org.bukkit.Material;

public final class BuildSafetyPolicy {
    private static final Set<Material> BLOCKED = Set.of(
            Material.BEDROCK,
            Material.BARRIER,
            Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID,
            Material.JIGSAW,
            Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,
            Material.END_PORTAL_FRAME,
            Material.NETHER_PORTAL,
            Material.END_PORTAL,
            Material.END_GATEWAY,
            Material.RESPAWN_ANCHOR,
            Material.TNT,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.LAVA,
            Material.WATER,
            Material.PISTON,
            Material.STICKY_PISTON,
            Material.OBSERVER,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER,
            Material.REDSTONE_WIRE,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.SCULK_SENSOR,
            Material.CALIBRATED_SCULK_SENSOR);

    private BuildSafetyPolicy() {
    }

    public static boolean isSafeBuildingBlock(Material material) {
        return material != null && material != Material.AIR && !BLOCKED.contains(material)
                && !material.name().endsWith("_SPAWN_EGG")
                && !material.name().endsWith("_BUCKET");
    }

    /**
     * Lava is useful for themes such as volcano, but other bucket contents remain
     * disabled. Placement bounds and fluid propagation are enforced separately by
     * the game listener.
     */
    public static boolean isAllowedPlotBucket(Material material) {
        return material == Material.LAVA_BUCKET;
    }
}

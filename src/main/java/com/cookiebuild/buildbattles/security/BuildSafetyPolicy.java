package com.cookiebuild.buildbattles.security;

import java.util.Map;
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
            Material.DAYLIGHT_DETECTOR,
            Material.NOTE_BLOCK,
            Material.SCULK_SENSOR,
            Material.CALIBRATED_SCULK_SENSOR);

    private static final Set<Material> REDSTONE_COMPONENTS = Set.of(
            Material.REDSTONE_WIRE,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.REDSTONE_TORCH,
            Material.REDSTONE_WALL_TORCH,
            Material.REDSTONE_BLOCK,
            Material.REDSTONE_LAMP,
            Material.LEVER,
            Material.TARGET,
            Material.TRIPWIRE_HOOK,
            Material.LIGHTNING_ROD);

    private static final Set<Material> DECORATION_ITEMS = Set.of(
            Material.ITEM_FRAME,
            Material.GLOW_ITEM_FRAME,
            Material.PAINTING,
            Material.ARMOR_STAND);

    private static final Set<Material> UNSAFE_USE_ITEMS = Set.of(
            Material.END_CRYSTAL,
            Material.FLINT_AND_STEEL,
            Material.FIRE_CHARGE,
            Material.ENDER_PEARL,
            Material.SNOWBALL,
            Material.EGG,
            Material.EXPERIENCE_BOTTLE,
            Material.FIREWORK_ROCKET,
            Material.WIND_CHARGE,
            Material.FISHING_ROD,
            Material.TRIDENT);

    // EntityType constants require a live Paper registry in 1.21.11+, so the
    // policy stores stable enum names and resolves them only inside the server.
    private static final Map<Material, String> PASSIVE_SPAWN_EGGS = Map.ofEntries(
            Map.entry(Material.VILLAGER_SPAWN_EGG, "VILLAGER"),
            Map.entry(Material.COW_SPAWN_EGG, "COW"),
            Map.entry(Material.SHEEP_SPAWN_EGG, "SHEEP"),
            Map.entry(Material.PIG_SPAWN_EGG, "PIG"),
            Map.entry(Material.CHICKEN_SPAWN_EGG, "CHICKEN"),
            Map.entry(Material.RABBIT_SPAWN_EGG, "RABBIT"),
            Map.entry(Material.HORSE_SPAWN_EGG, "HORSE"),
            Map.entry(Material.DONKEY_SPAWN_EGG, "DONKEY"),
            Map.entry(Material.LLAMA_SPAWN_EGG, "LLAMA"),
            Map.entry(Material.CAMEL_SPAWN_EGG, "CAMEL"),
            Map.entry(Material.CAT_SPAWN_EGG, "CAT"),
            Map.entry(Material.WOLF_SPAWN_EGG, "WOLF"),
            Map.entry(Material.PARROT_SPAWN_EGG, "PARROT"),
            Map.entry(Material.BEE_SPAWN_EGG, "BEE"),
            Map.entry(Material.GOAT_SPAWN_EGG, "GOAT"),
            Map.entry(Material.FROG_SPAWN_EGG, "FROG"),
            Map.entry(Material.TURTLE_SPAWN_EGG, "TURTLE"),
            Map.entry(Material.AXOLOTL_SPAWN_EGG, "AXOLOTL"),
            Map.entry(Material.MOOSHROOM_SPAWN_EGG, "MOOSHROOM"));

    private BuildSafetyPolicy() {
    }

    public static boolean isSafeBuildingBlock(Material material) {
        return material != null && material != Material.AIR && !BLOCKED.contains(material)
                && !material.name().endsWith("_SPAWN_EGG")
                && !material.name().endsWith("_BUCKET");
    }

    public static boolean isSafeFloorBlock(Material material) {
        return isSafeBuildingBlock(material) && !isRedstoneComponent(material);
    }

    public static boolean isRedstoneComponent(Material material) {
        if (material == null) return false;
        String name = material.name();
        return REDSTONE_COMPONENTS.contains(material)
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE");
    }

    public static boolean isDecorationItem(Material material) {
        return DECORATION_ITEMS.contains(material);
    }

    public static String passiveEntityNameForEgg(Material material) {
        return PASSIVE_SPAWN_EGGS.get(material);
    }

    public static boolean isAllowedPassiveEntityName(String typeName) {
        return typeName != null && PASSIVE_SPAWN_EGGS.containsValue(typeName);
    }

    /** Defense-in-depth for entity, vehicle and projectile items used in an arena. */
    public static boolean isSafeBuildItem(Material material) {
        if (material == null) return false;
        String name = material.name();
        if (isDecorationItem(material) || passiveEntityNameForEgg(material) != null
                || isAllowedPlotBucket(material)) {
            return true;
        }
        return isSafeBuildingBlock(material) && !UNSAFE_USE_ITEMS.contains(material)
                && material != Material.MINECART
                && !name.endsWith("_MINECART")
                && !name.endsWith("_BOAT")
                && !name.endsWith("_RAFT");
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

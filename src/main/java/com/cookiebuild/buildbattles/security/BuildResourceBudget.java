package com.cookiebuild.buildbattles.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Per-player resource accounting for one ephemeral BuildBattles arena.
 *
 * The tracker is deliberately independent from Bukkit entities so quota edge
 * cases can be unit-tested without starting a server.
 */
public final class BuildResourceBudget {
    public enum EntityCategory {
        DECORATION,
        ARMOR_STAND,
        LIVING
    }

    public enum AddResult {
        ADDED,
        ALREADY_TRACKED,
        TOTAL_LIMIT,
        CATEGORY_LIMIT,
        VILLAGER_LIMIT;

        public boolean accepted() {
            return this == ADDED || this == ALREADY_TRACKED;
        }
    }

    public record BlockKey(UUID worldId, int x, int y, int z) {
    }

    private record EntityEntry(UUID owner, EntityCategory category, boolean villager) {
    }

    private final int maxEntities;
    private final int maxDecorations;
    private final int maxArmorStands;
    private final int maxLiving;
    private final int maxVillagers;
    private final int maxRedstone;
    private final int maxLavaSources;
    private final Map<UUID, EntityEntry> entities = new HashMap<>();
    private final Map<BlockKey, UUID> redstone = new HashMap<>();
    private final Map<BlockKey, UUID> lavaSources = new HashMap<>();

    public BuildResourceBudget(int maxEntities, int maxDecorations, int maxArmorStands,
            int maxLiving, int maxVillagers, int maxRedstone, int maxLavaSources) {
        if (maxEntities < 1 || maxDecorations < 0 || maxArmorStands < 0
                || maxLiving < 0 || maxVillagers < 0 || maxRedstone < 1 || maxLavaSources < 0) {
            throw new IllegalArgumentException("Invalid BuildBattles resource limits");
        }
        this.maxEntities = maxEntities;
        this.maxDecorations = maxDecorations;
        this.maxArmorStands = maxArmorStands;
        this.maxLiving = maxLiving;
        this.maxVillagers = maxVillagers;
        this.maxRedstone = maxRedstone;
        this.maxLavaSources = maxLavaSources;
    }

    public AddResult tryAddEntity(UUID owner, UUID entityId, EntityCategory category, boolean villager) {
        if (owner == null || entityId == null || category == null) {
            throw new IllegalArgumentException("Owner, entity and category are required");
        }
        if (entities.containsKey(entityId)) return AddResult.ALREADY_TRACKED;
        if (entityCount(owner) >= maxEntities) return AddResult.TOTAL_LIMIT;
        if (categoryCount(owner, category) >= categoryLimit(category)) return AddResult.CATEGORY_LIMIT;
        if (villager && villagerCount(owner) >= maxVillagers) return AddResult.VILLAGER_LIMIT;
        entities.put(entityId, new EntityEntry(owner, category, villager));
        return AddResult.ADDED;
    }

    public UUID removeEntity(UUID entityId) {
        EntityEntry removed = entities.remove(entityId);
        return removed == null ? null : removed.owner();
    }

    public UUID entityOwner(UUID entityId) {
        EntityEntry entry = entities.get(entityId);
        return entry == null ? null : entry.owner();
    }

    public EntityCategory entityCategory(UUID entityId) {
        EntityEntry entry = entities.get(entityId);
        return entry == null ? null : entry.category();
    }

    public Set<UUID> trackedEntityIds() {
        return Set.copyOf(entities.keySet());
    }

    public int entityCount(UUID owner) {
        return (int) entities.values().stream().filter(entry -> entry.owner().equals(owner)).count();
    }

    public int categoryCount(UUID owner, EntityCategory category) {
        return (int) entities.values().stream()
                .filter(entry -> entry.owner().equals(owner) && entry.category() == category).count();
    }

    public int villagerCount(UUID owner) {
        return (int) entities.values().stream()
                .filter(entry -> entry.owner().equals(owner) && entry.villager()).count();
    }

    public boolean tryAddRedstone(UUID owner, BlockKey block) {
        UUID existing = redstone.get(block);
        if (owner.equals(existing)) return true;
        if (existing != null || redstoneCount(owner) >= maxRedstone) return false;
        redstone.put(block, owner);
        return true;
    }

    public UUID removeRedstone(BlockKey block) {
        return redstone.remove(block);
    }

    public int redstoneCount(UUID owner) {
        return (int) redstone.values().stream().filter(owner::equals).count();
    }

    public void pruneRedstone(Predicate<BlockKey> stillPresent) {
        redstone.keySet().removeIf(block -> !stillPresent.test(block));
    }

    public boolean tryAddLavaSource(UUID owner, BlockKey block) {
        UUID existing = lavaSources.get(block);
        if (owner.equals(existing)) return true;
        if (existing != null || lavaSourceCount(owner) >= maxLavaSources) return false;
        lavaSources.put(block, owner);
        return true;
    }

    public UUID removeLavaSource(BlockKey block) {
        return lavaSources.remove(block);
    }

    public int lavaSourceCount(UUID owner) {
        return (int) lavaSources.values().stream().filter(owner::equals).count();
    }

    public void pruneLavaSources(Predicate<BlockKey> stillPresent) {
        lavaSources.keySet().removeIf(block -> !stillPresent.test(block));
    }

    public void clear() {
        entities.clear();
        redstone.clear();
        lavaSources.clear();
    }

    private int categoryLimit(EntityCategory category) {
        return switch (category) {
            case DECORATION -> maxDecorations;
            case ARMOR_STAND -> maxArmorStands;
            case LIVING -> maxLiving;
        };
    }
}

package com.cookiebuild.buildbattles.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.cookiebuild.buildbattles.security.BuildResourceBudget.AddResult;
import com.cookiebuild.buildbattles.security.BuildResourceBudget.BlockKey;
import com.cookiebuild.buildbattles.security.BuildResourceBudget.EntityCategory;

class BuildResourceBudgetTest {
    @Test
    void appliesCategoryAndTotalLimitsPerPlayer() {
        BuildResourceBudget budget = new BuildResourceBudget(4, 2, 1, 2, 1, 2, 1);
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        assertEquals(AddResult.ADDED, budget.tryAddEntity(alice, UUID.randomUUID(), EntityCategory.LIVING, true));
        assertEquals(AddResult.VILLAGER_LIMIT,
                budget.tryAddEntity(alice, UUID.randomUUID(), EntityCategory.LIVING, true));
        assertEquals(AddResult.ADDED, budget.tryAddEntity(alice, UUID.randomUUID(), EntityCategory.LIVING, false));
        assertEquals(AddResult.CATEGORY_LIMIT,
                budget.tryAddEntity(alice, UUID.randomUUID(), EntityCategory.LIVING, false));
        assertEquals(AddResult.ADDED,
                budget.tryAddEntity(bob, UUID.randomUUID(), EntityCategory.LIVING, true));
    }

    @Test
    void trackingIsIdempotentAndRemovalReleasesQuota() {
        BuildResourceBudget budget = new BuildResourceBudget(1, 1, 1, 1, 1, 1, 1);
        UUID owner = UUID.randomUUID();
        UUID first = UUID.randomUUID();

        assertEquals(AddResult.ADDED, budget.tryAddEntity(owner, first, EntityCategory.DECORATION, false));
        assertEquals(AddResult.ALREADY_TRACKED,
                budget.tryAddEntity(owner, first, EntityCategory.DECORATION, false));
        assertEquals(AddResult.TOTAL_LIMIT,
                budget.tryAddEntity(owner, UUID.randomUUID(), EntityCategory.ARMOR_STAND, false));
        assertEquals(owner, budget.removeEntity(first));
        assertEquals(AddResult.ADDED,
                budget.tryAddEntity(owner, UUID.randomUUID(), EntityCategory.ARMOR_STAND, false));
    }

    @Test
    void countsUniqueRedstonePositionsAndReleasesThem() {
        BuildResourceBudget budget = new BuildResourceBudget(1, 1, 1, 1, 1, 2, 1);
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        BlockKey first = new BlockKey(world, 1, 2, 3);
        BlockKey second = new BlockKey(world, 2, 2, 3);
        BlockKey third = new BlockKey(world, 3, 2, 3);

        assertTrue(budget.tryAddRedstone(owner, first));
        assertTrue(budget.tryAddRedstone(owner, first));
        assertTrue(budget.tryAddRedstone(owner, second));
        assertFalse(budget.tryAddRedstone(owner, third));
        assertEquals(owner, budget.removeRedstone(first));
        assertTrue(budget.tryAddRedstone(owner, third));
    }

    @Test
    void limitsAndReleasesLavaSources() {
        BuildResourceBudget budget = new BuildResourceBudget(1, 1, 1, 1, 1, 1, 1);
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        BlockKey first = new BlockKey(world, 1, 2, 3);
        BlockKey second = new BlockKey(world, 2, 2, 3);

        assertTrue(budget.tryAddLavaSource(owner, first));
        assertFalse(budget.tryAddLavaSource(owner, second));
        assertEquals(owner, budget.removeLavaSource(first));
        assertTrue(budget.tryAddLavaSource(owner, second));
    }

    @Test
    void prunesBlocksThatWereReplacedByPhysicsOrFloorChanges() {
        BuildResourceBudget budget = new BuildResourceBudget(1, 1, 1, 1, 1, 1, 1);
        UUID owner = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        BlockKey redstone = new BlockKey(world, 1, 2, 3);
        BlockKey lava = new BlockKey(world, 2, 2, 3);

        assertTrue(budget.tryAddRedstone(owner, redstone));
        assertTrue(budget.tryAddLavaSource(owner, lava));
        budget.pruneRedstone(Set.<BlockKey>of()::contains);
        budget.pruneLavaSources(Set.<BlockKey>of()::contains);

        assertEquals(0, budget.redstoneCount(owner));
        assertEquals(0, budget.lavaSourceCount(owner));
    }
}

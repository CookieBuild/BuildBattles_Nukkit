package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class BuildStatsTest {
    @Test
    void recordsContributionCounters() {
        BuildStats stats = new BuildStats();
        UUID player = UUID.randomUUID();
        stats.placed(player);
        stats.placed(player);
        stats.broken(player);
        stats.floorChanged(player);
        assertEquals(new BuildStats.Snapshot(2, 1, 1), stats.snapshot(player));
    }
}

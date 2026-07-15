package com.cookiebuild.buildbattles.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class MapTemplateTest {
    @Test
    void rejectsRepositoryDefaultOverlapAndAcceptsRecoveredProductionGeometry() {
        List<List<Double>> plots = List.of(
                List.of(185.5, 5.5, 155.5, 135.0),
                List.of(185.5, 5.5, 112.5, 90.0));
        assertThrows(IllegalArgumentException.class, () -> new MapTemplate(
                "legacy", "legacy.zip", List.of(185.5, 5.5, 155.5, 135.0), plots, 6.0, 25, 20, 1));

        MapTemplate recovered = new MapTemplate(
                "legacy", "legacy.zip", List.of(185.5, 5.5, 155.5, 135.0), plots, 6.0, 13, 20, 2);
        PlotBounds bounds = recovered.bounds(0);

        assertEquals(2, recovered.capacity());
        assertEquals(4, bounds.floorY());
        assertEquals(173, bounds.minBlockX());
        assertEquals(198, bounds.maxBlockX());
        assertTrue(bounds.contains(185.5, 5.5, 155.5));
        assertFalse(bounds.contains(172.5, 5.5, 155.5));
    }

    @Test
    void rejectsArchiveTraversal() {
        assertThrows(IllegalArgumentException.class, () -> new MapTemplate(
                "legacy", "../legacy.zip", List.of(0.5, 30.0, 0.5, 0.0),
                List.of(List.of(0.5, 5.5, 0.5, 0.0), List.of(41.5, 5.5, 0.5, 0.0)),
                6.0, 13, 20, 2));
    }
}

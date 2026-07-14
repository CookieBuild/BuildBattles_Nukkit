package com.cookiebuild.buildbattles.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class MapTemplateTest {
    @Test
    void rejectsOverlappingLegacyRadiusAndAcceptsSafePlaceholder() {
        List<List<Double>> plots = List.of(
                List.of(0.5, 5.5, 0.5, 0.0),
                List.of(41.5, 5.5, 0.5, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new MapTemplate(
                "legacy", "legacy.zip", List.of(0.5, 30.0, 0.5, 0.0), plots, 6.0, 25, 20, 1));
        MapTemplate safe = new MapTemplate(
                "legacy", "legacy.zip", List.of(0.5, 30.0, 0.5, 0.0), plots, 6.0, 19, 20, 1);
        assertEquals(2, safe.capacity());
    }

    @Test
    void rejectsArchiveTraversal() {
        assertThrows(IllegalArgumentException.class, () -> new MapTemplate(
                "legacy", "../legacy.zip", List.of(0.5, 30.0, 0.5, 0.0),
                List.of(List.of(0.5, 5.5, 0.5, 0.0), List.of(41.5, 5.5, 0.5, 0.0)),
                6.0, 19, 20, 1));
    }
}

package com.cookiebuild.buildbattles.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlotBoundsTest {
    @Test
    void appliesStrictLegacyStyleBoundaries() {
        PlotBounds bounds = new PlotBounds(100.5, 5.5, 100.5, 19, 20, 1);
        assertTrue(bounds.contains(100.5, 5.5, 100.5));
        assertTrue(bounds.contains(119.0, 24.0, 100.5));
        assertFalse(bounds.contains(119.5, 5.5, 100.5));
        assertFalse(bounds.contains(100.5, 4.5, 100.5));
    }
}

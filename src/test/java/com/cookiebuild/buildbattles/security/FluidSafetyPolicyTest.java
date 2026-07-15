package com.cookiebuild.buildbattles.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cookiebuild.buildbattles.map.PlotBounds;

class FluidSafetyPolicyTest {
    private static final PlotBounds FIRST = new PlotBounds(100.5, 5.5, 100.5, 13, 20, 2);
    private static final PlotBounds SECOND = new PlotBounds(140.5, 5.5, 100.5, 13, 20, 2);

    @Test
    void allowsFluidUpdatesInsideOnePlot() {
        assertTrue(FluidSafetyPolicy.staysWithinSinglePlot(List.of(FIRST, SECOND),
                100, 5, 100, 101, 5, 100));
    }

    @Test
    void blocksFlowOutsideOrBetweenPlots() {
        assertFalse(FluidSafetyPolicy.staysWithinSinglePlot(List.of(FIRST, SECOND),
                113, 5, 100, 114, 5, 100));
        assertFalse(FluidSafetyPolicy.staysWithinSinglePlot(List.of(FIRST, SECOND),
                100, 5, 100, 140, 5, 100));
        assertFalse(FluidSafetyPolicy.staysWithinSinglePlot(List.of(FIRST, SECOND),
                100, 4, 100, 100, 3, 100));
    }

    @Test
    void rejectsMissingPlotContext() {
        assertFalse(FluidSafetyPolicy.staysWithinSinglePlot(List.of(),
                100, 5, 100, 101, 5, 100));
    }
}

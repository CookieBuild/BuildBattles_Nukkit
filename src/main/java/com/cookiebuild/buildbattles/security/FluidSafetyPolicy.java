package com.cookiebuild.buildbattles.security;

import java.util.Collection;

import com.cookiebuild.buildbattles.map.PlotBounds;

/** Keeps a fluid update inside one assigned BuildBattles plot. */
public final class FluidSafetyPolicy {
    private FluidSafetyPolicy() {
    }

    public static boolean staysWithinSinglePlot(Collection<PlotBounds> plots,
            double fromX, double fromY, double fromZ,
            double toX, double toY, double toZ) {
        if (plots == null || plots.isEmpty()) return false;
        for (PlotBounds plot : plots) {
            if (plot.contains(fromX, fromY, fromZ) && plot.contains(toX, toY, toZ)) return true;
        }
        return false;
    }
}

package com.cookiebuild.buildbattles.map;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;

public final class MapTemplate {
    private final String name;
    private final String archive;
    private final List<Double> waitingSpawn;
    private final List<List<Double>> plots;
    private final double judgingHeight;
    private final int plotHalfSize;
    private final int plotUp;
    private final int plotDown;

    public MapTemplate(String name, String archive, List<Double> waitingSpawn,
            List<List<Double>> plots, double judgingHeight,
            int plotHalfSize, int plotUp, int plotDown) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }
        if (archive == null || !archive.matches("[A-Za-z0-9._-]+\\.zip")) {
            throw new IllegalArgumentException("Archive must be a simple .zip filename");
        }
        requireCoords("waiting-spawn", waitingSpawn);
        if (plots == null || plots.size() < 2) {
            throw new IllegalArgumentException("BuildBattles needs at least two plots");
        }
        plots.forEach(coords -> requireCoords("plot", coords));
        if (!Double.isFinite(judgingHeight) || judgingHeight < 2.0) {
            throw new IllegalArgumentException("judging-height must be finite and at least 2");
        }
        // Validate the shared geometry once for every plot.
        new PlotBounds(plots.getFirst().get(0), plots.getFirst().get(1), plots.getFirst().get(2),
                plotHalfSize, plotUp, plotDown);
        validateNonOverlappingPlots(plots, plotHalfSize);
        this.name = name;
        this.archive = archive;
        this.waitingSpawn = List.copyOf(waitingSpawn);
        this.plots = plots.stream().map(List::copyOf).toList();
        this.judgingHeight = judgingHeight;
        this.plotHalfSize = plotHalfSize;
        this.plotUp = plotUp;
        this.plotDown = plotDown;
    }

    private static void validateNonOverlappingPlots(List<List<Double>> plots, int halfSize) {
        for (int first = 0; first < plots.size(); first++) {
            for (int second = first + 1; second < plots.size(); second++) {
                List<Double> a = plots.get(first);
                List<Double> b = plots.get(second);
                if (Math.abs(a.get(0) - b.get(0)) < halfSize * 2.0
                        && Math.abs(a.get(2) - b.get(2)) < halfSize * 2.0) {
                    throw new IllegalArgumentException("Plots " + first + " and " + second
                            + " overlap at half-size " + halfSize);
                }
            }
        }
    }

    private static void requireCoords(String field, List<Double> values) {
        if (values == null || values.size() != 4
                || values.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException(field + " must be finite [x,y,z,yaw]");
        }
    }

    public String name() {
        return name;
    }

    public String archive() {
        return archive;
    }

    public int capacity() {
        return plots.size();
    }

    public Location waitingSpawn(World world) {
        return location(world, waitingSpawn);
    }

    public Location plotCenter(World world, int plot) {
        return location(world, plots.get(plot));
    }

    public Location judgingLocation(World world, int plot) {
        return plotCenter(world, plot).add(0.0, judgingHeight, 0.0);
    }

    public PlotBounds bounds(int plot) {
        List<Double> center = plots.get(plot);
        return new PlotBounds(center.get(0), center.get(1), center.get(2), plotHalfSize, plotUp, plotDown);
    }

    private static Location location(World world, List<Double> coords) {
        return new Location(world, coords.get(0), coords.get(1), coords.get(2), coords.get(3).floatValue(), 0.0f);
    }
}

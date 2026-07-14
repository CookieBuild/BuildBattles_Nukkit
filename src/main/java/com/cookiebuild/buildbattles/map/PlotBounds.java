package com.cookiebuild.buildbattles.map;

public record PlotBounds(double centerX, double centerY, double centerZ,
        int halfSize, int up, int down) {
    public PlotBounds {
        if (!Double.isFinite(centerX) || !Double.isFinite(centerY) || !Double.isFinite(centerZ)
                || halfSize < 2 || up < 1 || down < 0) {
            throw new IllegalArgumentException("Invalid plot bounds");
        }
    }

    public boolean contains(double x, double y, double z) {
        return x > centerX - halfSize && x < centerX + halfSize
                && y > centerY - down && y < centerY + up
                && z > centerZ - halfSize && z < centerZ + halfSize;
    }

    public int floorY() {
        return (int) Math.floor(centerY - down + 1.0);
    }

    public int minBlockX() {
        return (int) Math.floor(centerX - halfSize + 1.0);
    }

    public int maxBlockX() {
        return (int) Math.ceil(centerX + halfSize - 1.0);
    }

    public int minBlockZ() {
        return (int) Math.floor(centerZ - halfSize + 1.0);
    }

    public int maxBlockZ() {
        return (int) Math.ceil(centerZ + halfSize - 1.0);
    }
}

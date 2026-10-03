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

    /*
     * Gallery capture box (release bb-gallery-20261003): the footprint is
     * 2*halfSize+1 blocks centred on the plot's centre block, and the height
     * runs from plot-down below to plot-up above the configured centre, i.e.
     * 27 x 23 x 27 for the legacy map. It contains every buildable block.
     */

    public int captureMinX() {
        return (int) Math.floor(centerX) - halfSize;
    }

    public int captureMinY() {
        return (int) Math.floor(centerY - down);
    }

    public int captureMinZ() {
        return (int) Math.floor(centerZ) - halfSize;
    }

    public int captureSizeX() {
        return 2 * halfSize + 1;
    }

    public int captureSizeY() {
        return up + down + 1;
    }

    public int captureSizeZ() {
        return 2 * halfSize + 1;
    }

    /** A block above the floor inside the player-editable area: what counts as "built". */
    public boolean isBuildArea(int x, int y, int z) {
        return x >= minBlockX() && x <= maxBlockX() && z >= minBlockZ() && z <= maxBlockZ()
                && y > floorY() && contains(x, y, z);
    }
}

package com.cookiebuild.buildbattles;

/** Keeps runtime arena preparation bounded while guaranteeing recovery from an empty pool. */
final class StandbyRefillPolicy {
    private StandbyRefillPolicy() {}

    static int runtimeBatchSize(int standbyCount, int targetSize, boolean quietWindowReady) {
        if (standbyCount < 0 || targetSize < 1 || standbyCount > targetSize) {
            throw new IllegalArgumentException("Invalid standby pool size");
        }
        if (standbyCount == targetSize) return 0;
        if (standbyCount == 0 || quietWindowReady) return 1;
        return 0;
    }
}

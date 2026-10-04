package com.cookiebuild.buildbattles.game;

/** Pure rule deciding whether a finished builder earns the match reward. */
public final class RewardEligibilityPolicy {
    /** Blocks a non-winning builder must place during the match to earn the participation reward. */
    public static final int DEFAULT_MIN_BLOCKS_PLACED = 10;

    private RewardEligibilityPolicy() {
    }

    /**
     * Winners always earn their reward; everyone else must have built something,
     * so idling through a solo match no longer farms coins and match goals.
     */
    public static boolean earnsReward(boolean won, int blocksPlaced, int minimumBlocksPlaced) {
        return won || blocksPlaced >= Math.max(0, minimumBlocksPlaced);
    }
}

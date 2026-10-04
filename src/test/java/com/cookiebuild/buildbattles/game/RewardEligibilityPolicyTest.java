package com.cookiebuild.buildbattles.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RewardEligibilityPolicyTest {
    private static final int MIN = RewardEligibilityPolicy.DEFAULT_MIN_BLOCKS_PLACED;

    @Test
    void idleBuilderEarnsNothing() {
        assertFalse(RewardEligibilityPolicy.earnsReward(false, 0, MIN));
        assertFalse(RewardEligibilityPolicy.earnsReward(false, MIN - 1, MIN));
    }

    @Test
    void builderWhoPlacedEnoughBlocksIsRewarded() {
        assertTrue(RewardEligibilityPolicy.earnsReward(false, MIN, MIN));
        assertTrue(RewardEligibilityPolicy.earnsReward(false, 250, MIN));
    }

    @Test
    void winnerIsAlwaysRewarded() {
        assertTrue(RewardEligibilityPolicy.earnsReward(true, 0, MIN));
    }

    @Test
    void negativeConfigurationDisablesTheThreshold() {
        assertTrue(RewardEligibilityPolicy.earnsReward(false, 0, -5));
    }
}

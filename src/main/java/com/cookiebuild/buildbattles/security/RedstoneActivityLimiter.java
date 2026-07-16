package com.cookiebuild.buildbattles.security;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Fixed-window circuit breaker scoped to one player's plot. */
public final class RedstoneActivityLimiter {
    public enum Decision {
        ALLOWED,
        TRIPPED,
        BLOCKED
    }

    private static final long WINDOW_MILLIS = 1_000L;
    private final int updatesPerSecond;
    private final long cooldownMillis;
    private final Map<UUID, Activity> activityByOwner = new HashMap<>();

    private static final class Activity {
        private long windowStartedAt;
        private int updates;
        private long blockedUntil;
    }

    public RedstoneActivityLimiter(int updatesPerSecond, long cooldownMillis) {
        if (updatesPerSecond < 1 || cooldownMillis < 1) {
            throw new IllegalArgumentException("Invalid redstone activity limits");
        }
        this.updatesPerSecond = updatesPerSecond;
        this.cooldownMillis = cooldownMillis;
    }

    public Decision evaluate(UUID owner, long nowMillis) {
        Activity activity = activityByOwner.computeIfAbsent(owner, ignored -> new Activity());
        if (nowMillis < activity.blockedUntil) return Decision.BLOCKED;
        if (activity.windowStartedAt == 0L || nowMillis - activity.windowStartedAt >= WINDOW_MILLIS) {
            activity.windowStartedAt = nowMillis;
            activity.updates = 0;
        }
        activity.updates++;
        if (activity.updates <= updatesPerSecond) return Decision.ALLOWED;
        activity.blockedUntil = nowMillis + cooldownMillis;
        activity.updates = 0;
        return Decision.TRIPPED;
    }

    public void clear() {
        activityByOwner.clear();
    }
}

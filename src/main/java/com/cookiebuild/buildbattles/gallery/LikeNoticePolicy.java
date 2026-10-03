package com.cookiebuild.buildbattles.gallery;

/**
 * Join notice "your builds received N new likes". The last notified total is
 * kept per player; an unlike lowers the stored total silently so the same
 * like is never announced twice.
 */
public final class LikeNoticePolicy {
    private LikeNoticePolicy() {
    }

    /** Likes received since the last notice (0 when nothing new). */
    public static long newLikes(long lastNotifiedTotal, long currentTotal) {
        return Math.max(0L, currentTotal - Math.max(0L, lastNotifiedTotal));
    }

    /** Total to remember after this check. */
    public static long nextStoredTotal(long currentTotal) {
        return Math.max(0L, currentTotal);
    }
}

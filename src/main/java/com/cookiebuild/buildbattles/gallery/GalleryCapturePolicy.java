package com.cookiebuild.buildbattles.gallery;

import java.util.Locale;

import com.cookiebuild.buildbattles.game.ShortHandedMatchPolicy;

/** Pure rules deciding which plots enter the gallery and how their row is labelled. */
public final class GalleryCapturePolicy {
    /** Blocks above the floor a builder must have placed for the plot to be captured. */
    public static final int MIN_BUILT_BLOCKS = 20;
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_PRIVATE = "private";

    /** {@code buildbattle_builds.outcome} values. */
    public enum Outcome {
        JUDGED("judged"),
        SOLO("solo"),
        FORFEIT("forfeit");

        private final String column;

        Outcome(String column) {
            this.column = column;
        }

        public String column() {
            return column;
        }
    }

    private GalleryCapturePolicy() {
    }

    public static boolean eligible(int builtBlocks) {
        return builtBlocks >= MIN_BUILT_BLOCKS;
    }

    /**
     * Stored {@code block_count}: the non-air blocks above the floor inside the
     * editable area (the eligibility count), not the payload's non-air total,
     * which also includes the floor and the layer below it.
     */
    public static int blockCount(int builtBlocks) {
        return Math.max(0, builtBlocks);
    }

    /** Short-handed ending captured at match end, or null when it is not a match end. */
    public static Outcome outcomeOf(ShortHandedMatchPolicy.Outcome shortHanded) {
        if (shortHanded == null) return null;
        return switch (shortHanded) {
            case SOLO_RESULT -> Outcome.SOLO;
            case FORFEIT_VICTORY, FORFEIT_PARTICIPATION -> Outcome.FORFEIT;
            case CONTINUE -> null;
        };
    }

    /** Placement column: 1 = winner; solo builds and unranked plots have none. */
    public static Integer placement(Outcome outcome, Integer rankedPlacement) {
        if (outcome == Outcome.SOLO || rankedPlacement == null || rankedPlacement < 1) return null;
        return rankedPlacement;
    }

    public static String status(boolean galleryOptOut) {
        return galleryOptOut ? STATUS_PRIVATE : STATUS_PUBLISHED;
    }

    /** Stable theme key shared with {@code bb.theme.name.<key>}: "Fast Food" -> "fast_food". */
    public static String themeKey(String theme) {
        if (theme == null || theme.isBlank()) return "unknown";
        String slug = theme.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return slug.isEmpty() ? "unknown" : slug;
    }

    /** Text shown in game (plain, readable on Bedrock): host + short redirect route. */
    public static String shareUrl(String host, String shortCode) {
        return host + "/b/" + shortCode;
    }

    /** Public gallery listing, shown as plain text. */
    public static String galleryUrl(String host) {
        return host + "/builds";
    }
}

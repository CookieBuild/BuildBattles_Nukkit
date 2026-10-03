package com.cookiebuild.buildbattles.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.cookiebuild.buildbattles.game.ShortHandedMatchPolicy;
import com.cookiebuild.buildbattles.map.PlotBounds;

class GalleryCapturePolicyTest {
    @Test
    void capturesOnlyPlotsWithTwentyBuiltBlocks() {
        assertFalse(GalleryCapturePolicy.eligible(0));
        assertFalse(GalleryCapturePolicy.eligible(19));
        assertTrue(GalleryCapturePolicy.eligible(20));
        assertTrue(GalleryCapturePolicy.eligible(5_000));
    }

    @Test
    void blockCountIsThePlacedBlockCountNotThePayloadTotal() {
        BuildPayload.Builder builder = new BuildPayload.Builder(3, 3, 3);
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) builder.set(x, 0, z, "minecraft:terracotta");
        builder.set(1, 1, 1, "minecraft:oak_planks");
        BuildGalleryService.CapturedPlot plot = new BuildGalleryService.CapturedPlot(
                java.util.UUID.randomUUID(), builder.build(), 1);
        assertEquals(10, plot.payload().nonAirCount());
        assertEquals(1, plot.blockCount());
        assertEquals(GalleryCapturePolicy.eligible(plot.builtBlocks()), plot.eligible());
        assertEquals(0, GalleryCapturePolicy.blockCount(-3));
    }

    @Test
    void mapsMatchEndingsToContractColumns() {
        assertEquals("solo", GalleryCapturePolicy.outcomeOf(ShortHandedMatchPolicy.Outcome.SOLO_RESULT).column());
        assertEquals("forfeit",
                GalleryCapturePolicy.outcomeOf(ShortHandedMatchPolicy.Outcome.FORFEIT_PARTICIPATION).column());
        assertEquals("forfeit", GalleryCapturePolicy.outcomeOf(ShortHandedMatchPolicy.Outcome.FORFEIT_VICTORY).column());
        assertNull(GalleryCapturePolicy.outcomeOf(ShortHandedMatchPolicy.Outcome.CONTINUE));
        assertEquals("judged", GalleryCapturePolicy.Outcome.JUDGED.column());

        assertEquals(1, GalleryCapturePolicy.placement(GalleryCapturePolicy.Outcome.JUDGED, 1));
        assertEquals(3, GalleryCapturePolicy.placement(GalleryCapturePolicy.Outcome.JUDGED, 3));
        assertNull(GalleryCapturePolicy.placement(GalleryCapturePolicy.Outcome.SOLO, 1));
        assertNull(GalleryCapturePolicy.placement(GalleryCapturePolicy.Outcome.FORFEIT, null));
        assertNull(GalleryCapturePolicy.placement(GalleryCapturePolicy.Outcome.JUDGED, 0));
    }

    @Test
    void optOutStoresPrivateRowsAndThemeKeysMatchLocalizationKeys() {
        assertEquals("private", GalleryCapturePolicy.status(true));
        assertEquals("published", GalleryCapturePolicy.status(false));
        assertEquals("fast_food", GalleryCapturePolicy.themeKey("Fast Food"));
        assertEquals("upside_down", GalleryCapturePolicy.themeKey(" Upside Down "));
        assertEquals("unknown", GalleryCapturePolicy.themeKey(null));
        assertEquals("cookie-build.com/b/Ab3dE9gZ", GalleryCapturePolicy.shareUrl("cookie-build.com", "Ab3dE9gZ"));
        assertEquals("cookie-build.com/builds", GalleryCapturePolicy.galleryUrl("cookie-build.com"));
    }

    @Test
    void legacyPlotCaptureBoxIs27x23x27AndContainsEveryBuildableBlock() {
        PlotBounds bounds = new PlotBounds(185.5, 5.5, 155.5, 13, 20, 2);
        assertEquals(27, bounds.captureSizeX());
        assertEquals(23, bounds.captureSizeY());
        assertEquals(27, bounds.captureSizeZ());
        assertEquals(172, bounds.captureMinX());
        assertEquals(3, bounds.captureMinY());
        assertEquals(142, bounds.captureMinZ());
        assertTrue(27 * 23 * 27 <= 17_000);
        for (int x = bounds.minBlockX(); x <= bounds.maxBlockX(); x++) {
            assertTrue(x >= bounds.captureMinX() && x < bounds.captureMinX() + bounds.captureSizeX());
        }
        assertTrue(bounds.floorY() >= bounds.captureMinY());
        // The highest placeable block is inside the box; the floor itself is not "built".
        assertTrue(bounds.contains(185, 25, 155));
        assertTrue(25 < bounds.captureMinY() + bounds.captureSizeY());
        assertTrue(bounds.isBuildArea(185, 5, 155));
        assertFalse(bounds.isBuildArea(185, bounds.floorY(), 155));
        assertFalse(bounds.isBuildArea(172, 5, 155));
    }

    @Test
    void likeNoticeAnnouncesOnlyNewLikes() {
        assertEquals(3, LikeNoticePolicy.newLikes(0, 3));
        assertEquals(2, LikeNoticePolicy.newLikes(5, 7));
        assertEquals(0, LikeNoticePolicy.newLikes(7, 7));
        assertEquals(0, LikeNoticePolicy.newLikes(7, 4));
        assertEquals(4, LikeNoticePolicy.nextStoredTotal(4));
    }
}

package com.cookiebuild.buildbattles.gallery;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.buildbattles.map.PlotBounds;

/**
 * Captures finished plots for the public gallery and owns every gallery
 * database call. Blocks are read on the server thread in one bounded pass per
 * plot; compression and SQL always run asynchronously. A failure is logged once
 * and never reaches the match; while migration 0024 is missing the gallery is
 * skipped quietly (one warning, retried every ten minutes).
 */
public final class BuildGalleryService {
    public static final String DEFAULT_HOST = "cookie-build.com";
    private static final Duration MISSING_TABLE_BACKOFF = Duration.ofMinutes(10);

    private final JavaPlugin plugin;
    private final PostgresBuildGalleryRepository repository = new PostgresBuildGalleryRepository();
    private final Set<String> warnings = ConcurrentHashMap.newKeySet();
    private final NamespacedKey likeTotalKey;
    private volatile Instant disabledUntil = Instant.EPOCH;

    /** One plot read from the world; {@code builtBlocks} counts non-air blocks above the floor. */
    public record CapturedPlot(UUID playerId, BuildPayload payload, int builtBlocks) {
        public boolean eligible() {
            return GalleryCapturePolicy.eligible(builtBlocks);
        }
    }

    /** Match-wide labels of a capture; the match id is read when the row is written. */
    public record MatchContext(Supplier<UUID> matchId, String themeKey, String themeName,
            GalleryCapturePolicy.Outcome outcome, int builders) {
    }

    public enum OptOutResult { SAVED, UNAVAILABLE }

    public BuildGalleryService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.likeTotalKey = new NamespacedKey(plugin, "gallery_like_total");
    }

    public String host() {
        return plugin.getConfig().getString("gallery.host", DEFAULT_HOST);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("gallery.enabled", true);
    }

    /** Reads the exact capture box in one pass. Server thread only; the chunks must be loaded. */
    public static CapturedPlot capture(World world, PlotBounds bounds, UUID owner) {
        int minX = bounds.captureMinX();
        int minY = bounds.captureMinY();
        int minZ = bounds.captureMinZ();
        int sizeX = bounds.captureSizeX();
        int sizeY = bounds.captureSizeY();
        int sizeZ = bounds.captureSizeZ();
        BuildPayload.Builder builder = new BuildPayload.Builder(sizeX, sizeY, sizeZ);
        int built = 0;
        for (int y = 0; y < sizeY; y++) {
            int worldY = minY + y;
            if (worldY < world.getMinHeight() || worldY >= world.getMaxHeight()) continue;
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    Block block = world.getBlockAt(minX + x, worldY, minZ + z);
                    Material type = block.getType();
                    if (type.isAir()) continue;
                    builder.set(x, y, z, block.getBlockData().getAsString());
                    if (bounds.isBuildArea(minX + x, worldY, minZ + z)) built++;
                }
            }
        }
        return new CapturedPlot(owner, builder.build(), built);
    }

    /**
     * Captures now when every chunk of the box is loaded (the usual case at
     * match end), otherwise loads them asynchronously and captures on the
     * server thread afterwards, provided the arena still exists.
     *
     * @return the plot when it was captured synchronously, else null
     */
    public CapturedPlot captureWhenLoaded(World world, PlotBounds bounds, UUID owner,
            Consumer<CapturedPlot> onCaptured) {
        List<long[]> chunks = new ArrayList<>();
        boolean loaded = true;
        for (int chunkX = bounds.captureMinX() >> 4;
                chunkX <= (bounds.captureMinX() + bounds.captureSizeX() - 1) >> 4; chunkX++) {
            for (int chunkZ = bounds.captureMinZ() >> 4;
                    chunkZ <= (bounds.captureMinZ() + bounds.captureSizeZ() - 1) >> 4; chunkZ++) {
                chunks.add(new long[] { chunkX, chunkZ });
                loaded &= world.isChunkLoaded(chunkX, chunkZ);
            }
        }
        if (loaded) {
            CapturedPlot plot = capture(world, bounds, owner);
            onCaptured.accept(plot);
            return plot;
        }
        CompletableFuture<?>[] loads = chunks.stream()
                .map(chunk -> world.getChunkAtAsync((int) chunk[0], (int) chunk[1], false))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(loads).whenComplete((ignored, error) -> runOnServerThread(() -> {
            if (error != null) {
                warnOnce("chunk-load", "Gallery capture skipped: plot chunks could not be loaded: "
                        + PostgresBuildGalleryRepository.rootMessage(error));
                return;
            }
            if (Bukkit.getWorld(world.getKey()) == null) return; // arena already unloaded
            onCaptured.accept(capture(world, bounds, owner));
        }));
        return null;
    }

    /** Compresses and inserts asynchronously; {@code onSaved} runs on the server thread. */
    public void submit(CapturedPlot plot, MatchContext context, Integer placement,
            Consumer<PostgresBuildGalleryRepository.Inserted> onSaved) {
        if (plot == null || !plot.eligible() || !enabled() || paused()) return;
        runAsync(() -> {
            try {
                if (paused()) return;
                byte[] data = plot.payload().gzip();
                if (data.length > BuildPayload.MAX_COMPRESSED_BYTES) {
                    warnOnce("too-large", "Gallery capture skipped: payload of " + data.length
                            + " bytes exceeds " + BuildPayload.MAX_COMPRESSED_BYTES);
                    return;
                }
                PostgresBuildGalleryRepository.Inserted inserted = repository.insert(
                        new PostgresBuildGalleryRepository.BuildRow(UUID.randomUUID(), plot.playerId(),
                                context.matchId().get(), context.themeKey(), context.themeName(), context.outcome(),
                                GalleryCapturePolicy.placement(context.outcome(), placement), context.builders(),
                                plot.payload().nonAirCount(), plot.payload().sizeX(), plot.payload().sizeY(),
                                plot.payload().sizeZ(), data),
                        ShortCodes::next);
                plugin.getLogger().info("Gallery build saved: code=" + inserted.shortCode()
                        + " status=" + inserted.status() + " blocks=" + plot.payload().nonAirCount()
                        + " bytes=" + data.length);
                runOnServerThread(() -> onSaved.accept(inserted));
            } catch (RuntimeException error) {
                handleFailure("capture", error);
            }
        });
    }

    /** {@code /bb galerie on|off}; {@code onDone} runs on the server thread. */
    public void setOptOut(UUID playerId, boolean optOut, Consumer<OptOutResult> onDone) {
        runAsync(() -> {
            OptOutResult result;
            try {
                int hidden = repository.setOptOut(playerId, optOut);
                plugin.getLogger().info("Gallery opt-out updated: player=" + playerId + " opt_out=" + optOut
                        + " builds_made_private=" + hidden);
                result = OptOutResult.SAVED;
            } catch (RuntimeException error) {
                handleFailure("opt-out", error);
                result = OptOutResult.UNAVAILABLE;
            }
            OptOutResult done = result;
            runOnServerThread(() -> onDone.accept(done));
        });
    }

    /** Current choice: true = opted out; null when the gallery tables are unavailable. */
    public void readOptOut(UUID playerId, Consumer<Boolean> onDone) {
        runAsync(() -> {
            Boolean optOut;
            try {
                optOut = repository.optOut(playerId).orElse(false);
            } catch (RuntimeException error) {
                handleFailure("opt-out-read", error);
                optOut = null;
            }
            Boolean done = optOut;
            runOnServerThread(() -> onDone.accept(done));
        });
    }

    /** Join notice of new likes; the last notified total lives in the player's Paper data. */
    public void checkNewLikes(Player player, java.util.function.LongConsumer onNewLikes) {
        if (player == null || !enabled() || paused()) return;
        UUID playerId = player.getUniqueId();
        runAsync(() -> {
            long total;
            try {
                total = repository.totalLikes(playerId);
            } catch (RuntimeException error) {
                handleFailure("likes", error);
                return;
            }
            runOnServerThread(() -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null) return;
                var data = online.getPersistentDataContainer();
                long stored = data.getOrDefault(likeTotalKey, PersistentDataType.LONG, 0L);
                long fresh = LikeNoticePolicy.newLikes(stored, total);
                data.set(likeTotalKey, PersistentDataType.LONG, LikeNoticePolicy.nextStoredTotal(total));
                if (fresh > 0) onNewLikes.accept(fresh);
            });
        });
    }

    private boolean paused() {
        return Instant.now().isBefore(disabledUntil);
    }

    private void handleFailure(String operation, RuntimeException error) {
        if (PostgresBuildGalleryRepository.isMissingTable(error)) {
            disabledUntil = Instant.now().plus(MISSING_TABLE_BACKOFF);
            warnOnce("missing-table", "Build Battle gallery tables are missing (website migration 0024 not "
                    + "applied yet); gallery features are skipped and retried every 10 minutes");
            return;
        }
        String message = PostgresBuildGalleryRepository.rootMessage(error);
        warnOnce(operation + ":" + message, "Build Battle gallery " + operation + " failed: " + message);
    }

    private void warnOnce(String key, String warning) {
        if (warnings.size() > 200) warnings.clear();
        if (warnings.add(key)) plugin.getLogger().warning(warning);
    }

    private void runAsync(Runnable work) {
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, work);
        } catch (RuntimeException error) {
            warnOnce("schedule", "Could not schedule a gallery task: " + error.getMessage());
        }
    }

    private void runOnServerThread(Runnable work) {
        if (Bukkit.isPrimaryThread()) {
            work.run();
            return;
        }
        if (!plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTask(plugin, work);
        } catch (RuntimeException ignored) {
            // Shutting down: in-game feedback is best effort.
        }
    }
}

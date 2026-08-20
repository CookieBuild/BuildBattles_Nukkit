package com.cookiebuild.buildbattles.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;

import io.papermc.paper.math.Position;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline.WorldLoad;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline.PreparedFilesInUseException;
import com.cookiebuild.cookiedough.utils.FileUtils;
import com.cookiebuild.cookiedough.utils.ZipUtils;

public final class MapManager {
    private static final Logger LOGGER = Logger.getLogger(MapManager.class.getName());
    private static final long VALIDATION_BUDGET_MILLIS = 250L;

    public record PreparedMap(UUID gameId, MapTemplate template, NamespacedKey key,
            File archive, File destination) { }
    private static final Map<String, MapTemplate> TEMPLATES = new LinkedHashMap<>();
    private static final Map<UUID, GameMap> LOADED = new LinkedHashMap<>();
    private static String lastMap;

    private MapManager() {
    }

    public static void loadTemplates() {
        TEMPLATES.clear();
        ConfigurationSection section = BuildBattles.getInstance().getConfig().getConfigurationSection("maps");
        if (section == null) {
            throw new IllegalStateException("No BuildBattles maps configured");
        }
        for (String name : section.getKeys(false)) {
            try {
                String prefix = name + ".";
                TEMPLATES.put(name, new MapTemplate(
                        name,
                        section.getString(prefix + "archive", name + ".zip"),
                        numbers(section.getList(prefix + "waiting-spawn"), prefix + "waiting-spawn"),
                        matrix(section.getList(prefix + "plots"), prefix + "plots"),
                        section.getDouble(prefix + "judging-height", 6.0),
                        section.getInt(prefix + "plot-half-size", 19),
                        section.getInt(prefix + "plot-up", 20),
                        section.getInt(prefix + "plot-down", 1)));
            } catch (IllegalArgumentException error) {
                BuildBattles.getInstance().getLogger().severe("Ignoring invalid map " + name + ": " + error.getMessage());
            }
        }
        if (TEMPLATES.isEmpty()) {
            throw new IllegalStateException("No valid BuildBattles map configuration");
        }
    }

    private static List<List<Double>> matrix(List<?> source, String field) {
        if (source == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        List<List<Double>> result = new ArrayList<>();
        for (Object row : source) {
            if (!(row instanceof List<?> values)) {
                throw new IllegalArgumentException(field + " must contain coordinate lists");
            }
            result.add(numbers(values, field));
        }
        return result;
    }

    private static List<Double> numbers(List<?> source, String field) {
        if (source == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        List<Double> result = new ArrayList<>();
        for (Object value : source) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(field + " contains a non-number");
            }
            result.add(number.doubleValue());
        }
        return result;
    }

    public static synchronized MapTemplate selectAvailable() {
        List<MapTemplate> available = TEMPLATES.values().stream()
                .filter(template -> new File("buildbattles_maps", template.archive()).isFile()).toList();
        if (available.isEmpty()) {
            throw new IllegalStateException("No configured archive exists in buildbattles_maps/");
        }
        List<MapTemplate> candidates = available.stream()
                .filter(template -> available.size() == 1 || !template.name().equals(lastMap)).toList();
        MapTemplate selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        lastMap = selected.name();
        return selected;
    }

    public static GameMap load(UUID gameId, MapTemplate template) throws IOException {
        PreparedMap prepared = prepareIo(plan(gameId, template));
        try {
            return loadPrepared(prepared);
        } catch (IOException error) {
            discardPrepared(prepared);
            throw error;
        }
    }

    public static PreparedMap plan(UUID gameId, MapTemplate template) throws IOException {
        requirePrimaryThread("planned");
        File archive = new File("buildbattles_maps", template.archive());
        if (!archive.isFile()) {
            throw new IOException("Missing map archive " + archive.getAbsolutePath());
        }
        NamespacedKey key = new NamespacedKey(BuildBattles.getInstance(), gameId.toString());
        if (Bukkit.getWorld(key) != null || LOADED.containsKey(gameId)) {
            throw new IOException("World already loaded for " + gameId);
        }
        File destination = worldDirectory(key);
        return new PreparedMap(gameId, template, key, archive, destination);
    }

    public static PreparedMap prepareIo(PreparedMap prepared) throws IOException {
        File destination = prepared.destination();
        if (destination.exists()) {
            FileUtils.deleteDirectory(destination);
        }
        try {
            ZipUtils.unzip(prepared.archive(), destination);
            Files.deleteIfExists(destination.toPath().resolve("session.lock"));
            Files.deleteIfExists(destination.toPath().resolve("uid.dat"));
            validateArchive(destination.toPath());
            return prepared;
        } catch (IOException | RuntimeException error) {
            discardPrepared(prepared);
            if (error instanceof IOException ioError) throw ioError;
            throw new IOException("Could not prepare BuildBattles world", error);
        }
    }

    public static GameMap loadPrepared(PreparedMap prepared) throws IOException {
        requirePrimaryThread("loaded");
        UUID gameId = prepared.gameId();
        MapTemplate template = prepared.template();
        NamespacedKey key = prepared.key();
        try {
            World world = createPreparedWorld(prepared);
            validatePlots(world, template);
            GameMap map = new GameMap(template, world);
            LOADED.put(gameId, map);
            return map;
        } catch (IOException | RuntimeException error) {
            World partial = Bukkit.getWorld(key);
            if (partial != null && partial.getPlayers().isEmpty()) {
                Bukkit.unloadWorld(partial, false);
            }
            if (error instanceof IOException ioError) {
                throw ioError;
            }
            throw new IOException("Could not load BuildBattles world", error);
        }
    }

    /** Preloads exactly the plot/lobby validation chunks through Paper before reading blocks. */
    public static WorldLoad<GameMap> loadPreparedAsync(PreparedMap prepared) {
        requirePrimaryThread("loaded");
        CompletableFuture<GameMap> result = new CompletableFuture<>();
        final World world;
        long createStartedAt = System.nanoTime();
        try {
            world = createPreparedWorld(prepared);
        } catch (Throwable error) {
            failAsyncLoad(prepared, result, error, elapsedMillis(createStartedAt), 0L, 0L);
            return WorldLoad.nonCancellable(result);
        }
        long createWorldMillis = elapsedMillis(createStartedAt);

        Set<ChunkCoordinate> chunks = validationChunks(prepared.template());
        long preloadStartedAt = System.nanoTime();
        CompletableFuture<?>[] chunkLoads;
        try {
            chunkLoads = chunks.stream()
                    .map(chunk -> world.getChunkAtAsync(chunk.x(), chunk.z(), true))
                    .toArray(CompletableFuture<?>[]::new);
        } catch (Throwable error) {
            failAsyncLoad(prepared, result, error, createWorldMillis,
                    elapsedMillis(preloadStartedAt), 0L);
            return WorldLoad.nonCancellable(result);
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<Void> aggregate = CompletableFuture.allOf(chunkLoads);
        aggregate.whenComplete((ignored, preloadError) -> {
            if (cancelled.get()) return;
            // Paper 26.1.2 guarantees async chunk futures complete on the main thread.
            if (!Bukkit.isPrimaryThread()) {
                result.completeExceptionally(new PreparedFilesInUseException(
                        "Paper completed BuildBattles chunks off the server thread",
                        new IllegalStateException("Async chunk completion violated the Paper contract")));
                return;
            }
            long chunkPreloadMillis = elapsedMillis(preloadStartedAt);
            if (preloadError != null) {
                failAsyncLoad(prepared, result,
                        new IOException("Could not preload BuildBattles validation chunks", preloadError),
                        createWorldMillis, chunkPreloadMillis, 0L);
                return;
            }

            long validationStartedAt = System.nanoTime();
            try {
                validatePlots(world, prepared.template());
                GameMap map = new GameMap(prepared.template(), world);
                LOADED.put(prepared.gameId(), map);
                long validationMillis = elapsedMillis(validationStartedAt);
                logAsyncLoadTimings(prepared, createWorldMillis, chunkPreloadMillis, validationMillis, null);
                result.complete(map);
            } catch (Throwable error) {
                failAsyncLoad(prepared, result, error, createWorldMillis, chunkPreloadMillis,
                        elapsedMillis(validationStartedAt));
            }
        });
        return WorldLoad.cancellable(result,
                () -> cancelAsyncLoad(prepared, result, aggregate, chunkLoads, cancelled));
    }

    private static boolean cancelAsyncLoad(PreparedMap prepared, CompletableFuture<GameMap> result,
            CompletableFuture<Void> aggregate, CompletableFuture<?>[] chunkLoads, AtomicBoolean cancelled) {
        requirePrimaryThread("cancelled");
        if (!cancelled.compareAndSet(false, true)) return result.isDone();
        aggregate.cancel(false);
        for (CompletableFuture<?> chunkLoad : chunkLoads) chunkLoad.cancel(false);

        CancellationException cancellation = new CancellationException(
                "BuildBattles arena load cancelled for " + prepared.gameId());
        boolean unloaded = true;
        World partial = Bukkit.getWorld(prepared.key());
        if (partial != null && (!partial.getPlayers().isEmpty() || !Bukkit.unloadWorld(partial, false))) {
            unloaded = false;
        }
        Throwable completionError = unloaded ? cancellation : new PreparedFilesInUseException(
                "Could not unload cancelled BuildBattles world " + prepared.key(), cancellation);
        result.completeExceptionally(completionError);
        return unloaded && result.isDone();
    }

    private static World createPreparedWorld(PreparedMap prepared) throws IOException {
        MapTemplate template = prepared.template();
        NamespacedKey key = prepared.key();
        org.bukkit.Location forcedSpawn = template.waitingSpawn(null);
        World world = WorldCreator.ofKey(key)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                // Recovered archives do not consistently contain a usable
                // SpawnX/Y/Z. Avoid Paper's multi-second safe-spawn scan.
                .forcedSpawnPosition(Position.block(
                        forcedSpawn.getBlockX(), forcedSpawn.getBlockY(), forcedSpawn.getBlockZ()),
                        forcedSpawn.getYaw(), forcedSpawn.getPitch())
                .generator(new VoidGenerator())
                .createWorld();
        if (world == null) throw new IOException("Paper returned no world for " + key);

        Path expected = prepared.destination().toPath().toAbsolutePath().normalize();
        if (!world.getWorldFolder().toPath().toAbsolutePath().normalize().equals(expected)) {
            Bukkit.unloadWorld(world, false);
            throw new IOException("Paper loaded the map from an unexpected directory");
        }
        world.setAutoSave(false);
        world.setTime(8000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_MONSTERS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
        world.setGameRule(GameRules.TNT_EXPLODES, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        return world;
    }

    public static void discardPrepared(PreparedMap prepared) {
        try {
            if (prepared.destination().exists()) FileUtils.deleteDirectory(prepared.destination());
        } catch (IOException error) {
            LOGGER.warning("Could not delete prepared BuildBattles files: " + error.getMessage());
        }
    }

    /** Unload a world registered by loadPrepared when game construction fails; filesystem cleanup stays async. */
    public static boolean discardLoadedWorld(UUID gameId) {
        GameMap map = LOADED.get(gameId);
        if (map == null) return true;
        World world = Bukkit.getWorld(map.world().getKey());
        if (world != null && (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, false))) return false;
        LOADED.remove(gameId, map);
        return true;
    }

    public static boolean unload(UUID gameId) {
        GameMap map = LOADED.get(gameId);
        if (map == null) {
            return true;
        }
        World world = Bukkit.getWorld(map.world().getKey());
        if (world != null) {
            if (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, false)) {
                return false;
            }
        }
        try {
            FileUtils.deleteDirectory(map.world().getWorldFolder());
            LOADED.remove(gameId, map);
            return true;
        } catch (IOException error) {
            BuildBattles.getInstance().getLogger().severe("Could not delete game world: " + error.getMessage());
            return false;
        }
    }

    public static boolean unloadAll() {
        boolean success = true;
        for (UUID gameId : new ArrayList<>(LOADED.keySet())) {
            success &= unload(gameId);
        }
        return success;
    }

    private static void validateArchive(Path directory) throws IOException {
        if (!Files.isRegularFile(directory.resolve("level.dat"))) {
            throw new IOException("Archive must contain level.dat at its root");
        }
        try (var files = Files.walk(directory)) {
            if (files.noneMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".mca"))) {
                throw new IOException("Archive has no region files and would create an empty arena");
            }
        }
    }

    private static void requirePrimaryThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("BuildBattles worlds must be " + action + " on the server thread");
        }
    }

    private static void validatePlots(World world, MapTemplate template) throws IOException {
        for (int plot = 0; plot < template.capacity(); plot++) {
            org.bukkit.Location center = template.plotCenter(world, plot);
            boolean solid = false;
            for (int offset = 1; offset <= 5; offset++) {
                if (world.getBlockAt(center.getBlockX(), center.getBlockY() - offset, center.getBlockZ())
                        .getType().isSolid()) {
                    solid = true;
                    break;
                }
            }
            if (!solid) {
                throw new IOException("No solid terrain below plot " + plot + " center "
                        + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ());
            }
        }
        org.bukkit.Location waiting = template.waitingSpawn(world);
        boolean safeWaiting = false;
        for (int offset = 1; offset <= 5; offset++) {
            if (world.getBlockAt(waiting.getBlockX(), waiting.getBlockY() - offset, waiting.getBlockZ())
                    .getType().isSolid()) {
                safeWaiting = true;
                break;
            }
        }
        if (!safeWaiting) {
            throw new IOException("No solid terrain below configured waiting spawn "
                    + waiting.getBlockX() + "," + waiting.getBlockY() + "," + waiting.getBlockZ());
        }
    }

    private static Set<ChunkCoordinate> validationChunks(MapTemplate template) {
        Set<ChunkCoordinate> chunks = new LinkedHashSet<>();
        for (int plot = 0; plot < template.capacity(); plot++) {
            org.bukkit.Location center = template.plotCenter(null, plot);
            chunks.add(ChunkCoordinate.fromBlock(center.getBlockX(), center.getBlockZ()));
        }
        org.bukkit.Location waiting = template.waitingSpawn(null);
        chunks.add(ChunkCoordinate.fromBlock(waiting.getBlockX(), waiting.getBlockZ()));
        return chunks;
    }

    private static void failAsyncLoad(PreparedMap prepared, CompletableFuture<GameMap> result,
            Throwable error, long createWorldMillis, long chunkPreloadMillis, long validationMillis) {
        World partial = Bukkit.getWorld(prepared.key());
        Throwable completionError = error;
        if (partial != null) {
            if (!partial.getPlayers().isEmpty() || !Bukkit.unloadWorld(partial, false)) {
                completionError = new PreparedFilesInUseException(
                        "Could not unload failed BuildBattles world " + prepared.key(), error);
            }
        }
        logAsyncLoadTimings(prepared, createWorldMillis, chunkPreloadMillis, validationMillis, completionError);
        result.completeExceptionally(completionError);
    }

    private static void logAsyncLoadTimings(PreparedMap prepared, long createWorldMillis, long chunkPreloadMillis,
            long validationMillis, Throwable failure) {
        String timings = "BuildBattles arena " + prepared.gameId()
                + " (create_world_ms=" + createWorldMillis
                + ", chunk_preload_ms=" + chunkPreloadMillis
                + ", validation_ms=" + validationMillis + ")";
        if (failure == null) LOGGER.info("Prepared " + timings);
        else LOGGER.warning("Failed to prepare " + timings + ": " + failure.getMessage());
        if (createWorldMillis > VALIDATION_BUDGET_MILLIS) {
            LOGGER.warning("BuildBattles world creation exceeded the " + VALIDATION_BUDGET_MILLIS
                    + "ms main-thread budget (create_world_ms=" + createWorldMillis + ")");
        }
        if (validationMillis > VALIDATION_BUDGET_MILLIS) {
            LOGGER.warning("BuildBattles validation exceeded the " + VALIDATION_BUDGET_MILLIS
                    + "ms main-thread budget (validation_ms=" + validationMillis + ")");
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static File worldDirectory(NamespacedKey key) throws IOException {
        World overworld = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        if (overworld == null) {
            throw new IOException("minecraft:overworld must be loaded first");
        }
        Path folder = overworld.getWorldFolder().toPath().toAbsolutePath().normalize();
        Path suffix = Path.of("dimensions", "minecraft", "overworld");
        if (folder.endsWith(suffix)) {
            folder = folder.getParent().getParent().getParent();
        }
        return folder.resolve("dimensions").resolve(key.getNamespace()).resolve(key.getKey()).toFile();
    }

    private record ChunkCoordinate(int x, int z) {
        private static ChunkCoordinate fromBlock(int blockX, int blockZ) {
            return new ChunkCoordinate(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        }
    }

    private static final class VoidGenerator extends ChunkGenerator {
    }
}

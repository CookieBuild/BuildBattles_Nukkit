package com.cookiebuild.buildbattles.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.cookiedough.utils.FileUtils;
import com.cookiebuild.cookiedough.utils.ZipUtils;

public final class MapManager {
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
        File archive = new File("buildbattles_maps", template.archive());
        if (!archive.isFile()) {
            throw new IOException("Missing map archive " + archive.getAbsolutePath());
        }
        NamespacedKey key = new NamespacedKey(BuildBattles.getInstance(), gameId.toString());
        if (Bukkit.getWorld(key) != null || LOADED.containsKey(gameId)) {
            throw new IOException("World already loaded for " + gameId);
        }
        File destination = worldDirectory(key);
        if (destination.exists()) {
            FileUtils.deleteDirectory(destination);
        }
        try {
            ZipUtils.unzip(archive, destination);
            Files.deleteIfExists(destination.toPath().resolve("session.lock"));
            Files.deleteIfExists(destination.toPath().resolve("uid.dat"));
            validateArchive(destination.toPath());
            World world = WorldCreator.ofKey(key)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    .generator(new VoidGenerator())
                    .createWorld();
            if (world == null) {
                throw new IOException("Paper returned no world for " + key);
            }
            Path expected = destination.toPath().toAbsolutePath().normalize();
            if (!world.getWorldFolder().toPath().toAbsolutePath().normalize().equals(expected)) {
                Bukkit.unloadWorld(world, false);
                throw new IOException("Paper loaded the map from an unexpected directory");
            }
            validatePlots(world, template);
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
            GameMap map = new GameMap(template, world);
            LOADED.put(gameId, map);
            return map;
        } catch (IOException | RuntimeException error) {
            World partial = Bukkit.getWorld(key);
            if (partial != null && partial.getPlayers().isEmpty()) {
                Bukkit.unloadWorld(partial, false);
            }
            if (destination.exists()) {
                try {
                    FileUtils.deleteDirectory(destination);
                } catch (IOException cleanup) {
                    error.addSuppressed(cleanup);
                }
            }
            if (error instanceof IOException ioError) {
                throw ioError;
            }
            throw new IOException("Could not load BuildBattles world", error);
        }
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

    private static final class VoidGenerator extends ChunkGenerator {
    }
}

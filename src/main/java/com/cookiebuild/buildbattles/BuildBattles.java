package com.cookiebuild.buildbattles;

import java.util.ArrayList;
import java.time.Duration;
import java.util.Locale;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.buildbattles.game.BuildBattlesGame;
import com.cookiebuild.buildbattles.game.BuildPhase;
import com.cookiebuild.buildbattles.game.FloorMaterialResolver;
import com.cookiebuild.buildbattles.map.MapManager;
import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.StandbyGamePool;
import com.cookiebuild.cookiedough.game.StandbyRefillGate;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class BuildBattles extends JavaPlugin {
    private static final int STANDBY_GAME_TARGET = 3;
    private static final long STANDBY_REFILL_RETRY_TICKS = 20L * 5L;
    private static BuildBattles instance;
    private NamespacedKey themeKey;
    private NamespacedKey voteKey;
    private NamespacedKey entityOwnerKey;
    private NamespacedKey entityCategoryKey;
    private final StandbyGamePool<BuildBattlesGame> standbyGames =
            new StandbyGamePool<>(STANDBY_GAME_TARGET);
    private final StandbyRefillGate standbyRefillGate =
            new StandbyRefillGate(Duration.ofSeconds(30));
    private boolean standbyRefillScheduled;
    private boolean shuttingDown;

    public static BuildBattles getInstance() { return instance; }

    public static boolean registerNewGame() {
        try {
            BuildBattlesGame game = new BuildBattlesGame();
            GameManager.addGame(game);
            instance.getLogger().info("Registered BuildBattles game " + game.getGameId());
            return true;
        } catch (RuntimeException error) {
            instance.getLogger().warning("BuildBattles unavailable until a valid map archive is installed: " + error.getMessage());
            return false;
        }
    }

    /**
     * Promotes a world prepared during startup instead of copying and loading a
     * new arena on the match-start tick.
     */
    public static void activateNextGame() {
        if (instance == null || instance.shuttingDown) return;
        BuildBattlesGame game = instance.standbyGames.poll();
        if (game == null) {
            instance.getLogger().warning("No preloaded BuildBattles standby is available; "
                    + "a one-arena recovery refill has been scheduled");
            requestStandbyRefill();
            return;
        }
        GameManager.addGame(game);
        instance.getLogger().info("Activated preloaded BuildBattles game " + game.getGameId()
                + " (standby remaining=" + instance.standbyGames.size() + ")");
        requestStandbyRefill();
    }

    public static void requestStandbyRefill() {
        if (instance == null || instance.shuttingDown || instance.standbyRefillScheduled
                || !instance.standbyGames.needsRefill()) return;
        instance.standbyRefillScheduled = true;
        instance.getServer().getScheduler().runTaskLater(instance, () -> {
            if (instance == null || instance.shuttingDown) return;
            instance.standbyRefillScheduled = false;
            int refillBatchSize = instance.runtimeRefillBatchSize();
            if (refillBatchSize == 0) {
                requestStandbyRefill();
                return;
            }
            instance.preloadStandbyGames(refillBatchSize);
            instance.activatePreparedGameIfMissing();
            if (instance.standbyGames.needsRefill()) requestStandbyRefill();
        }, STANDBY_REFILL_RETRY_TICKS);
    }

    private void preloadStandbyGames() {
        preloadStandbyGames(STANDBY_GAME_TARGET);
    }

    private void preloadStandbyGames(int maxGames) {
        int loadedGames = 0;
        while (!shuttingDown && standbyGames.needsRefill() && loadedGames < maxGames) {
            long startedAt = System.nanoTime();
            try {
                BuildBattlesGame game = new BuildBattlesGame();
                if (!standbyGames.offer(game)) {
                    game.shutdown();
                    break;
                }
                getLogger().info("Preloaded BuildBattles standby " + game.getGameId()
                        + " (" + standbyGames.size() + "/" + standbyGames.targetSize() + ", load_ms="
                        + elapsedMillis(startedAt) + ")");
                loadedGames++;
            } catch (RuntimeException error) {
                getLogger().warning("Could not preload BuildBattles standby: " + error.getMessage());
                break;
            }
        }
    }

    private int runtimeRefillBatchSize() {
        boolean activeGameplay = GameManager.getGames().stream().anyMatch(game -> !game.getPlayers().isEmpty()
                || game.getState() == GameState.STARTING
                || game.getState() == GameState.RUNNING);
        boolean quietWindowReady = standbyRefillGate.canRefill(!Bukkit.getOnlinePlayers().isEmpty(), activeGameplay);
        return StandbyRefillPolicy.runtimeBatchSize(
                standbyGames.size(), standbyGames.targetSize(), quietWindowReady);
    }

    private void activatePreparedGameIfMissing() {
        boolean hasOpenGame = GameManager.getGames().stream()
                .filter(BuildBattlesGame.class::isInstance)
                .anyMatch(game -> game.getState() == GameState.OPEN);
        if (!hasOpenGame) activateNextGame();
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();
        LocaleManager.registerBundle("buildbattles_messages");
        themeKey = new NamespacedKey(this, "theme_vote");
        voteKey = new NamespacedKey(this, "plot_vote");
        entityOwnerKey = new NamespacedKey(this, "build_owner");
        entityCategoryKey = new NamespacedKey(this, "build_entity_category");
        try {
            MapManager.loadTemplates();
        } catch (RuntimeException error) {
            getLogger().severe("Invalid BuildBattles configuration: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new com.cookiebuild.buildbattles.listener.BuildBattlesListener(), this);
        registerCommands();
        if (!registerNewGame()) {
            getLogger().warning("No game registered; NPC and Quick Play stay fail-closed.");
        } else {
            // Loading spare worlds before the server opens avoids the historical
            // multi-second world-copy pause as a match begins.
            preloadStandbyGames();
        }
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 0);
        if (version >= 3) return;
        if (version < 2) {
            // Version 1 contained placeholders written before the recovered world
            // was available. They are unsafe on existing installations because the
            // waiting spawn is over void and the plot geometry overlaps.
            getConfig().set("maps.legacy.waiting-spawn", List.of(185.5, 5.5, 155.5, 135.0));
            getConfig().set("maps.legacy.plot-half-size", 13);
            getConfig().set("maps.legacy.plot-up", 20);
            getConfig().set("maps.legacy.plot-down", 2);
        }
        setIfMissing("safety.max-entities-per-player", 16);
        setIfMissing("safety.max-decoration-entities-per-player", 12);
        setIfMissing("safety.max-armor-stands-per-player", 4);
        setIfMissing("safety.max-living-entities-per-player", 4);
        setIfMissing("safety.max-villagers-per-player", 2);
        setIfMissing("safety.max-redstone-components-per-player", 64);
        setIfMissing("safety.max-lava-sources-per-player", 8);
        setIfMissing("safety.max-redstone-updates-per-second", 128);
        setIfMissing("safety.redstone-cooldown-seconds", 5);
        getConfig().set("config-version", 3);
        saveConfig();
        getLogger().info("Migrated BuildBattles safety settings to config version 3");
    }

    private void setIfMissing(String path, Object value) {
        if (!getConfig().isSet(path)) getConfig().set(path, value);
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("buildbattles")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            CookieDough.getInstance().getLobbyManager().requestGame(player, "BuildBattles");
            return true;
        });
        PluginCommand theme = Objects.requireNonNull(getCommand("bbtheme"));
        theme.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player) || args.length == 0) return false;
            BuildBattlesGame game = findGame(player);
            if (game == null || !game.voteTheme(player, String.join(" ", args))) {
                player.sendMessage(Component.text(message(player, "bb.theme.invalid"), NamedTextColor.RED));
            }
            return true;
        });
        PluginCommand vote = Objects.requireNonNull(getCommand("bbvote"));
        vote.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player) || args.length != 1) return false;
            try {
                BuildBattlesGame game = findGame(player);
                if (game != null) game.vote(player, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {
                player.sendMessage(Component.text(message(player, "bb.vote.invalid"), NamedTextColor.RED));
            }
            return true;
        });
        PluginCommand floor = Objects.requireNonNull(getCommand("floor"));
        floor.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            Material material = FloorMaterialResolver.resolve(args, player.getInventory().getItemInMainHand().getType());
            if (material == null) {
                player.sendMessage(Component.text(message(player, "bb.floor.unknown", String.join(" ", args)),
                        NamedTextColor.RED));
                return true;
            }
            if (!material.isBlock() || !material.isItem() || !BuildSafetyPolicy.isSafeFloorBlock(material)) {
                player.sendMessage(Component.text(message(player,
                        args.length == 0 ? "bb.floor.select" : "bb.floor.unsafe", material.name()),
                        NamedTextColor.RED));
                return true;
            }
            BuildBattlesGame game = findGame(player);
            BuildBattlesGame.FloorChangeResult result = game == null
                    ? BuildBattlesGame.FloorChangeResult.UNAVAILABLE : game.changeFloor(player, material);
            String messageKey = switch (result) {
                case STARTED -> "bb.floor.started";
                case NOT_BUILDING -> "bb.floor.not_building";
                case UNSAFE_MATERIAL -> "bb.floor.unsafe";
                case COOLDOWN -> "bb.floor.cooldown";
                case UNAVAILABLE -> "bb.floor.unavailable";
            };
            player.sendMessage(Component.text(message(player, messageKey, material.name()),
                    result == BuildBattlesGame.FloorChangeResult.STARTED ? NamedTextColor.GREEN : NamedTextColor.RED));
            return true;
        });
        floor.setTabCompleter((sender, command, alias, args) -> {
            if (args.length != 1) return List.of();
            return FloorMaterialResolver.suggestions(args[0]);
        });
        Objects.requireNonNull(getCommand("bbitems")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            BuildBattlesGame game = findGame(player);
            if (game == null || game.getPhase() != BuildPhase.BUILDING) {
                player.sendMessage(Component.text(message(player, "bb.items.unavailable"), NamedTextColor.RED));
                return true;
            }
            for (Material material : List.of(Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME,
                    Material.ARMOR_STAND, Material.VILLAGER_SPAWN_EGG)) {
                player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, 1));
            }
            player.sendMessage(Component.text(message(player, "bb.items.received"), NamedTextColor.GREEN));
            return true;
        });
    }

    public static BuildBattlesGame findGame(Player player) {
        if (player == null) return null;
        return GameManager.getGames().stream().filter(BuildBattlesGame.class::isInstance)
                .map(BuildBattlesGame.class::cast)
                .filter(game -> game.isActive(player.getUniqueId())).findFirst().orElse(null);
    }

    public static BuildBattlesGame findWorldGame(World world) {
        if (world == null) return null;
        return GameManager.getGames().stream().filter(BuildBattlesGame.class::isInstance)
                .map(BuildBattlesGame.class::cast).filter(game -> game.owns(world)).findFirst().orElse(null);
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        standbyGames.drain();
        for (Game game : new ArrayList<>(GameManager.getGames())) {
            if (game instanceof BuildBattlesGame buildGame) buildGame.shutdown();
        }
        if (!MapManager.unloadAll()) getLogger().warning("Some BuildBattles worlds could not be removed");
        instance = null;
    }

    public NamespacedKey getThemeKey() { return themeKey; }
    public NamespacedKey getVoteKey() { return voteKey; }
    public NamespacedKey getEntityOwnerKey() { return entityOwnerKey; }
    public NamespacedKey getEntityCategoryKey() { return entityCategoryKey; }

    public static String message(Player player, String key, Object... arguments) {
        Locale locale = player == null ? Locale.ENGLISH : player.locale();
        String registered = LocaleManager.getMessage("buildbattles_messages", key, locale, arguments);
        if (!registered.equals(key)) return registered;
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("buildbattles_messages", locale,
                    BuildBattles.class.getClassLoader());
            String value = bundle.getString(key);
            for (int index = 0; index < arguments.length; index++) {
                value = value.replace("{" + index + "}", String.valueOf(arguments[index]));
            }
            return value;
        } catch (MissingResourceException ignored) {
            return key;
        }
    }
}

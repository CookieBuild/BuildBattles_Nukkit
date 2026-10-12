package com.cookiebuild.buildbattles;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.buildbattles.game.BuildBattlesGame;
import com.cookiebuild.buildbattles.game.BuildPhase;
import com.cookiebuild.buildbattles.game.DurationBallot;
import com.cookiebuild.buildbattles.game.LateJoinPolicy;
import com.cookiebuild.buildbattles.game.FloorMaterialResolver;
import com.cookiebuild.buildbattles.gallery.BuildGalleryService;
import com.cookiebuild.buildbattles.gallery.GalleryCapturePolicy;
import com.cookiebuild.buildbattles.gallery.GalleryJoinListener;
import com.cookiebuild.buildbattles.map.MapManager;
import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.BukkitArenaPreparationScheduler;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline;
import com.cookiebuild.cookiedough.game.StandbyArenaService;
import com.cookiebuild.cookiedough.game.StandbyRefillPolicy;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import com.cookiebuild.cookiedough.ui.MenuLore;

public final class BuildBattles extends JavaPlugin {
    private static final String MESSAGE_BUNDLE = "buildbattles_messages";
    private static final String UNAVAILABLE_MESSAGE = "Text unavailable";
    private static final Locale BRAZILIAN_PORTUGUESE = Locale.of("pt", "BR");
    private static final ResourceBundle.Control NO_SYSTEM_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static BuildBattles instance;
    private NamespacedKey themeKey;
    private NamespacedKey durationKey;
    private NamespacedKey voteKey;
    private NamespacedKey ratingMenuKey;
    private NamespacedKey paletteShortcutKey;
    private NamespacedKey entityOwnerKey;
    private NamespacedKey entityCategoryKey;
    private StandbyArenaService<MapManager.PreparedMap, BuildBattlesGame> arenas;
    private BuildGalleryService gallery;
    private boolean shuttingDown;

    public static BuildBattles getInstance() { return instance; }

    /** Gallery capture and settings; null while the plugin is disabled. */
    public static BuildGalleryService gallery() { return instance == null ? null : instance.gallery; }

    public static boolean registerNewGame() {
        return instance != null && !instance.shuttingDown && instance.arenas.request(0L);
    }

    /**
     * Promotes a world prepared during startup instead of copying and loading a
     * new arena on the match-start tick.
     */
    public static void activateNextGame() {
        if (instance == null || instance.shuttingDown) return;
        instance.arenas.activateNext();
    }

    public static void requestStandbyRefill() {
        if (instance != null && !instance.shuttingDown) {
            instance.arenas.request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS);
        }
    }

    private MapManager.PreparedMap planArena() {
        try {
            return MapManager.plan(UUID.randomUUID(), MapManager.selectAvailable());
        } catch (java.io.IOException error) {
            throw new CompletionException(error);
        }
    }

    private static MapManager.PreparedMap prepareArenaIo(MapManager.PreparedMap plan) {
        try { return MapManager.prepareIo(plan); }
        catch (java.io.IOException error) { throw new CompletionException(error); }
    }

    private static ArenaPreparationPipeline.WorldLoad<BuildBattlesGame> loadArena(
            MapManager.PreparedMap prepared) {
        return MapManager.loadPreparedAsync(prepared).map(map -> {
            try {
                return new BuildBattlesGame(prepared.gameId(), map, prepared.template());
            } catch (RuntimeException error) {
                if (!MapManager.discardLoadedWorld(prepared.gameId())) {
                    BuildBattles.getInstance().getLogger().warning(
                            "Could not unload partially constructed BuildBattles arena " + prepared.gameId());
                }
                throw error;
            }
        });
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();
        LocaleManager.registerBundle("buildbattles_messages");
        themeKey = new NamespacedKey(this, "theme_vote");
        durationKey = new NamespacedKey(this, "duration_vote");
        voteKey = new NamespacedKey(this, "plot_vote");
        ratingMenuKey = new NamespacedKey(this, "rating_menu");
        paletteShortcutKey = new NamespacedKey(this, "palette_shortcut");
        entityOwnerKey = new NamespacedKey(this, "build_owner");
        entityCategoryKey = new NamespacedKey(this, "build_entity_category");
        try {
            MapManager.loadTemplates();
        } catch (RuntimeException error) {
            getLogger().severe("Invalid BuildBattles configuration: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        arenas = StandbyArenaService.asynchronous(
                "BuildBattles", new BukkitArenaPreparationScheduler(this), this::planArena,
                BuildBattles::prepareArenaIo, BuildBattles::loadArena, MapManager::discardPrepared,
                () -> GameManager.getGames().stream().filter(BuildBattlesGame.class::isInstance)
                        .anyMatch(game -> game.getState() == GameState.OPEN),
                GameManager::addGame, BuildBattlesGame::shutdown, getLogger(), true);
        gallery = new BuildGalleryService(this);
        getServer().getPluginManager().registerEvents(new com.cookiebuild.buildbattles.listener.BuildBattlesListener(), this);
        getServer().getPluginManager().registerEvents(new GalleryJoinListener(this, gallery), this);
        registerCommands();
        if (!registerNewGame()) {
            getLogger().warning("No game registered; NPC and Quick Play stay fail-closed.");
        }
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 0);
        if (version >= 4) return;
        if (version == 3) {
            migrateConfigToGallery();
            return;
        }
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
        migrateConfigToGallery();
    }

    /**
     * Version 4 (bb-gallery-20261003): 180 s solo builds, a late-join window of
     * half the build phase and the gallery. The old default fixed window
     * (90 s) is dropped so the fraction applies; any other explicit value is
     * kept and still wins.
     */
    private void migrateConfigToGallery() {
        if (getConfig().getInt("game.late-join-seconds", -1) == LateJoinPolicy.DEFAULT_WINDOW_SECONDS) {
            getConfig().set("game.late-join-seconds", null);
        }
        setIfMissing("game.solo-build-seconds", LateJoinPolicy.DEFAULT_SOLO_BUILD_SECONDS);
        setIfMissing("game.late-join-fraction", LateJoinPolicy.DEFAULT_WINDOW_FRACTION);
        setIfMissing("game.late-join-min-build-seconds", LateJoinPolicy.DEFAULT_MIN_NEWCOMER_SECONDS);
        setIfMissing("gallery.enabled", true);
        setIfMissing("gallery.host", BuildGalleryService.DEFAULT_HOST);
        getConfig().set("config-version", 4);
        saveConfig();
        getLogger().info("Migrated BuildBattles settings to config version 4 (solo timer, late join, gallery)");
    }

    private void setIfMissing(String path, Object value) {
        if (!getConfig().isSet(path)) getConfig().set(path, value);
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("buildbattles")).setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return true;
            if (args.length > 0 && (args[0].equalsIgnoreCase("galerie") || args[0].equalsIgnoreCase("gallery"))) {
                handleGalleryCommand(player, args);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("replay")) {
                FunnelTelemetry.record(player, FunnelTelemetry.Event.REMATCH_CLICKED, "game=BuildBattles");
                CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
                if (cookiePlayer != null && (cookiePlayer.getState() != PlayerState.LOBBY
                        || GameManager.getGameOfPlayer(cookiePlayer) != null)) {
                    if (!LobbyManager.teleportPlayerToLobby(cookiePlayer)) return true;
                }
            }
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
        PluginCommand duration = Objects.requireNonNull(getCommand("bbduration"));
        duration.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player) || args.length == 0) return false;
            BuildBattlesGame game = findGame(player);
            int minutes = DurationBallot.parseMinutes(String.join(" ", args));
            if (game == null || !game.voteDuration(player, minutes)) {
                player.sendMessage(Component.text(message(player, "bb.duration.invalid"), NamedTextColor.RED));
            }
            return true;
        });
        PluginCommand vote = Objects.requireNonNull(getCommand("bbvote"));
        vote.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) return false;
            BuildBattlesGame game = findGame(player);
            if (args.length == 0) return game != null && game.openRatingMenu(player);
            if (args.length != 1) return false;
            try {
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
            giveBuildPalette(player);
            return true;
        });
    }

    /** {@code /bb galerie [on|off]} (alias {@code /buildbattles gallery}). */
    private void handleGalleryCommand(Player player, String[] args) {
        UUID playerId = player.getUniqueId();
        if (args.length < 2) {
            gallery.readOptOut(playerId, optOut -> {
                Player online = getServer().getPlayer(playerId);
                if (online == null) return;
                if (optOut == null) {
                    online.sendMessage(Component.text(message(online, "bb.gallery.unavailable"), NamedTextColor.RED));
                    return;
                }
                online.sendMessage(Component.text(message(online, optOut ? "bb.gallery.status.off"
                        : "bb.gallery.status.on"), NamedTextColor.YELLOW));
                online.sendMessage(Component.text(message(online, "bb.gallery.usage"), NamedTextColor.GRAY));
            });
            return;
        }
        String choice = args[1].toLowerCase(Locale.ROOT);
        boolean optOut;
        if (choice.equals("off") || choice.equals("non")) optOut = true;
        else if (choice.equals("on") || choice.equals("oui")) optOut = false;
        else {
            player.sendMessage(Component.text(message(player, "bb.gallery.usage"), NamedTextColor.RED));
            return;
        }
        gallery.setOptOut(playerId, optOut, result -> {
            Player online = getServer().getPlayer(playerId);
            if (online == null) return;
            if (result != BuildGalleryService.OptOutResult.SAVED) {
                online.sendMessage(Component.text(message(online, "bb.gallery.unavailable"), NamedTextColor.RED));
                return;
            }
            online.sendMessage(Component.text(message(online, optOut ? "bb.gallery.off" : "bb.gallery.on",
                    GalleryCapturePolicy.galleryUrl(gallery.host())),
                    optOut ? NamedTextColor.YELLOW : NamedTextColor.GREEN));
        });
    }

    public static void givePaletteShortcut(Player player) {
        if (instance == null || player == null) return;
        ItemStack shortcut = new ItemStack(Material.CHEST);
        ItemMeta meta = shortcut.getItemMeta();
        meta.displayName(Component.text(message(player, "bb.palette.shortcut.name"), NamedTextColor.AQUA));
        meta.lore(List.of(MenuLore.detail(message(player, "bb.palette.shortcut.lore"))));
        meta.getPersistentDataContainer().set(instance.paletteShortcutKey, PersistentDataType.BYTE, (byte) 1);
        shortcut.setItemMeta(meta);
        player.getInventory().setItem(8, shortcut);
    }

    public static void giveBuildPalette(Player player) {
        BuildBattlesGame game = findGame(player);
        if (game == null || game.getPhase() != BuildPhase.BUILDING) {
            player.sendMessage(Component.text(message(player, "bb.items.unavailable"), NamedTextColor.RED));
            return;
        }
        for (Material material : BuildPalette.items()) {
            player.getInventory().addItem(new ItemStack(material, 1));
        }
        player.sendMessage(Component.text(message(player, "bb.items.received"), NamedTextColor.GREEN));
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
        if (arenas != null) arenas.shutdown();
        for (Game game : new ArrayList<>(GameManager.getGames())) {
            if (game instanceof BuildBattlesGame buildGame) buildGame.shutdown();
        }
        if (!MapManager.unloadAll()) getLogger().warning("Some BuildBattles worlds could not be removed");
        instance = null;
    }

    public NamespacedKey getThemeKey() { return themeKey; }
    public NamespacedKey getDurationKey() { return durationKey; }
    public NamespacedKey getVoteKey() { return voteKey; }
    public NamespacedKey getRatingMenuKey() { return ratingMenuKey; }
    public NamespacedKey getPaletteShortcutKey() { return paletteShortcutKey; }
    public NamespacedKey getEntityOwnerKey() { return entityOwnerKey; }
    public NamespacedKey getEntityCategoryKey() { return entityCategoryKey; }

    public static String message(Player player, String key, Object... arguments) {
        Locale locale = player == null ? Locale.ENGLISH : player.locale();
        return messageForLocale(locale, key, arguments);
    }

    static String messageForLocale(Locale locale, String key, Object... arguments) {
        String resolved = resolveMessage(locale, key, arguments);
        return resolved == null ? UNAVAILABLE_MESSAGE : resolved;
    }

    public static String themeName(Player player, String theme) {
        Locale locale = player == null ? Locale.ENGLISH : player.locale();
        return themeNameForLocale(locale, theme);
    }

    static String themeNameForLocale(Locale locale, String theme) {
        if (theme == null || theme.isBlank()) return "";
        String resolved = resolveMessage(locale, themeMessageKey(theme));
        return resolved == null ? theme : resolved;
    }

    static String themeMessageKey(String theme) {
        return "bb.theme.name." + GalleryCapturePolicy.themeKey(theme);
    }

    /** English display name of a theme: the gallery's {@code theme_name} fallback. */
    public static String englishThemeName(String theme) {
        return themeNameForLocale(Locale.ENGLISH, theme);
    }

    /**
     * Season shown beside the seasonal vote candidate ("Halloween"), or null when there is no season,
     * when the bundles do not name it, or when it would only repeat the theme itself.
     */
    public static String seasonTag(Player player, String theme, String seasonId) {
        return seasonTagForLocale(player == null ? Locale.ENGLISH : player.locale(), theme, seasonId);
    }

    static String seasonTagForLocale(Locale locale, String theme, String seasonId) {
        if (seasonId == null || seasonId.isBlank()) return null;
        String season = resolveMessage(locale, "bb.theme.season." + GalleryCapturePolicy.themeKey(seasonId));
        if (season == null || season.isBlank()) return null;
        return season.trim().equalsIgnoreCase(themeNameForLocale(locale, theme).trim()) ? null : season.trim();
    }

    /** Vote label of a candidate: its localized name, prefixed by the season for the seasonal one. */
    public static String ballotLabel(Player player, String theme, String seasonId) {
        return ballotLabelForLocale(player == null ? Locale.ENGLISH : player.locale(), theme, seasonId);
    }

    static String ballotLabelForLocale(Locale locale, String theme, String seasonId) {
        String name = themeNameForLocale(locale, theme);
        String season = seasonTagForLocale(locale, theme, seasonId);
        return season == null ? name : messageForLocale(locale, "bb.theme.seasonal_candidate", season, name);
    }

    private static String resolveMessage(Locale locale, String key, Object... arguments) {
        Locale effectiveLocale = locale == null ? Locale.ENGLISH : locale;
        List<Locale> candidates = new ArrayList<>();
        candidates.add(effectiveLocale);
        if ("pt".equals(effectiveLocale.getLanguage())) candidates.add(BRAZILIAN_PORTUGUESE);
        if (!effectiveLocale.getLanguage().isBlank()) candidates.add(Locale.of(effectiveLocale.getLanguage()));
        candidates.add(Locale.ENGLISH);

        for (Locale candidate : candidates.stream().distinct().toList()) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(MESSAGE_BUNDLE, candidate,
                        BuildBattles.class.getClassLoader(), NO_SYSTEM_LOCALE_FALLBACK);
                if (!bundle.containsKey(key)) continue;
                String value = bundle.getString(key);
                for (int index = 0; index < arguments.length; index++) {
                    value = value.replace("{" + index + "}", String.valueOf(arguments[index]));
                }
                return value;
            } catch (MissingResourceException ignored) {
                // Continue through the explicit language and English fallback chain.
            }
        }
        return null;
    }
}

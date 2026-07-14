package com.cookiebuild.buildbattles;

import java.util.ArrayList;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.buildbattles.game.BuildBattlesGame;
import com.cookiebuild.buildbattles.map.MapManager;
import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class BuildBattles extends JavaPlugin {
    private static BuildBattles instance;
    private NamespacedKey themeKey;
    private NamespacedKey voteKey;

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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        LocaleManager.registerBundle("buildbattles_messages");
        themeKey = new NamespacedKey(this, "theme_vote");
        voteKey = new NamespacedKey(this, "plot_vote");
        try {
            MapManager.loadTemplates();
        } catch (RuntimeException error) {
            getLogger().severe("Invalid BuildBattles configuration: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(new com.cookiebuild.buildbattles.listener.BuildBattlesListener(), this);
        registerCommands();
        if (!registerNewGame()) getLogger().warning("No game registered; NPC and Quick Play stay fail-closed.");
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
            Material material = player.getInventory().getItemInMainHand().getType();
            BuildBattlesGame game = findGame(player);
            if (game == null || !material.isBlock() || !material.isItem()
                    || !BuildSafetyPolicy.isSafeBuildingBlock(material) || !game.changeFloor(player, material)) {
                player.sendMessage(Component.text(message(player, "bb.floor.failed"), NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text(message(player, "bb.floor.started", material.name()), NamedTextColor.GREEN));
            }
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
        for (Game game : new ArrayList<>(GameManager.getGames())) {
            if (game instanceof BuildBattlesGame buildGame) buildGame.shutdown();
        }
        if (!MapManager.unloadAll()) getLogger().warning("Some BuildBattles worlds could not be removed");
        instance = null;
    }

    public NamespacedKey getThemeKey() { return themeKey; }
    public NamespacedKey getVoteKey() { return voteKey; }

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

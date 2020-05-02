package main.java;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockIgniteEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import main.java.Data.dataBaseQuery;
import main.java.Data.getPlayerDataTask;

import java.util.ArrayList;
import java.util.List;

public class Main extends PluginBase implements Listener {

    public BuildBattleGame game;
    public List<Vector3> pedestals = new ArrayList<>();
    public boolean isDataBaseEnabled = false;
    public boolean isProxyEnabled = false;
    String gameMapName = "game";

    /**
     * Database credentials
     */
    private String address;
    private String databaseName;
    private String username;
    private String password;
    int gameSize = 8;
    public String[] themes;

    /*
     * TODO
     *  onEnable
     *  Block connections when game has started
     *  Give coins when winning
     *  Send back to hub when finished
     *  Limit mob spawning per player
     *  Add players to the game when they join. Remove them if disconnected/kicked
     *  Add compass to go back to hub before the game starts
     *  Send messages when joining / game status / voting status / voting choice ....
     *
     *
     *
     *
     */

    @Override
    public void onEnable() {
        Config config = this.getConfig();
        this.isDataBaseEnabled = config.getBoolean("database_enabled");
        this.isProxyEnabled = config.getBoolean("proxy_enabled");

        if (this.isDataBaseEnabled) {
            this.address = config.getString("database_address");
            this.databaseName = config.getString("database_name");
            this.username = config.getString("database_user");
            this.password = config.getString("database_password");
        }

        this.gameSize = config.getInt("game_size");

        for (int i = 0; i < gameSize; i++) {
            try {
                String[] xyz = config.getString("plot_" + i).split(",");
                pedestals.add(new Vector3(Integer.parseInt(xyz[0].trim()) + 0.5, Integer.parseInt(xyz[1].trim()) + 0.5, Integer.parseInt(xyz[2].trim()) + 0.5));
                this.getServer().getLogger().info("Plot " + i + " is " + config.getString("plot_" + i));
            } catch (Exception e) {
                this.getServer().getLogger().error(e.getMessage());
            }


        }


        String allThemes = config.getString("themes");
        themes = allThemes.split(",");

        this.getServer().getLogger().info("Loaded " + themes.length + " themes");
        this.game = new BuildBattleGame(0, this.getServer(), this);
        this.game.resetGame();
        this.getServer().loadLevel("game");

        for (Level level : this.getServer().getLevels().values()) {
            for (FullChunk chunk : level.getChunks().values()) {
                level.loadChunk(chunk.getX(), chunk.getZ());
            }
            level.setTime(8000);
            level.setRaining(false);
            level.stopTime();
        }

        // Registering the listeners
        this.getServer().getPluginManager().registerEvents(this, this);

        this.getServer().getScheduler().scheduleRepeatingTask(() -> {
            this.game.tick();
        }, 20);

        this.getServer().getScheduler().scheduleRepeatingTask(this::sendPopus, 10);
    }

    public void sendPopus() {
        for (Player player : this.getServer().getOnlinePlayers().values()) {

        }
    }

    @EventHandler
    public void onBlockIgnite(BlockIgniteEvent event) {
        // Always block TNT
        if (event.getBlock().getId() == Block.TNT) {
            event.setCancelled();
        }

        if (game.isVotingTime) {
            event.setCancelled();
        }

    }

    @EventHandler
    public void onPlayerCreated(PlayerCreationEvent event) {
        event.setPlayerClass(cbPlayer.class);
    }

    @EventHandler
    public void onBlockPlaced(BlockPlaceEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (!player.isInGame) {
            event.setCancelled();
        } else {
            if (!this.game.hasStarted() || !game.isInPlot(player, event.getBlock().getLocation()) || game.isVotingTime) {
                event.setCancelled();
            }
        }
    }

    @EventHandler
    public void onBlockBreaked(BlockBreakEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (!player.isInGame) {
            event.setCancelled();
        } else {
            if (!this.game.hasStarted() || !game.isInPlot(player, event.getBlock().getLocation()) || game.isVotingTime) {
                event.setCancelled();
            }
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (game.hasStarted() && game.isVotingTime) {
            if (event.getItem().getId() == Item.CLAY) {
                int vote = 0;
                String voteText = TextFormat.WHITE + "NEUTRAL";
                switch (event.getItem().getDamage()) {
                    case 14:
                        vote = -2;
                        voteText = TextFormat.DARK_RED + "VERY BAD";
                        break;
                    case 6:
                        vote = -1;
                        voteText = TextFormat.RED + "VERY BAD";
                        break;
                    case 5:
                        vote = 1;
                        voteText = TextFormat.YELLOW + "OK";
                        break;
                    case 13:
                        vote = 3;
                        voteText = TextFormat.GREEN + "GOOD";
                        break;
                    case 11:
                        vote = 5;
                        voteText = TextFormat.AQUA + "EPIC";
                        break;
                    case 4:
                        vote = 7;
                        voteText = TextFormat.MINECOIN_GOLD + "LEGENDARY";
                        break;
                }
                if (this.game.votingSlot != player.plot) {
                    player.lastVote = vote;
                    player.sendMessage(TextFormat.GREEN + "> Your vote : " + voteText);
                } else {
                    player.sendMessage(TextFormat.RED + "> You can't vote for your own plot!");
                }

            }
        }
    }

    @EventHandler
    public void onPlayerPreLoginEvent(PlayerPreLoginEvent event) {
        if (this.game.state != Game.GAME_OPEN) {
            event.getPlayer().kick("A game is already running!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().setFoodEnabled(false);
        event.setJoinMessage("");

        if (isDataBaseEnabled) {
            this.getServer().getScheduler().scheduleTask(new getPlayerDataTask(event.getPlayer().getName(), address, databaseName, username, password), true);
        }

        this.onPlayerJoinGame((cbPlayer) event.getPlayer());
    }


    @EventHandler
    public void onPlayerKicked(PlayerKickEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (player.isInGame) {
            this.game.removePlayer(player);
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (player.isInGame) {
            this.game.removePlayer(player);
        }
    }

    @EventHandler
    public void onEntityDamaged(EntityDamageEvent event) {
        event.setCancelled();
    }

    @EventHandler
    public void onItemConsumed(PlayerItemConsumeEvent event) {
        event.setCancelled();
    }

    @EventHandler
    public void onItemDropped(PlayerDropItemEvent event) {
        event.setCancelled();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getPlayer().getLevel() == this.getServer().getDefaultLevel()) {
            if (event.getTo().y < 4) {
                event.getPlayer().teleport(event.getPlayer().getLevel().getSpawnLocation());
            }
        } else {
            cbPlayer player = (cbPlayer) event.getPlayer();
            if (player.isInGame && !game.isVotingTime) {
                if (!this.game.isInPlot(player, event.getTo())) {
                    event.setCancelled();
                    player.teleport(this.pedestals.get(player.plot));
                }
            }
        }
    }


    public void onPlayerJoinGame(cbPlayer player) {
        game.addPlayer(player);
        if (game.getPlayers().size() == gameSize) {
            game.startGame();
        }
    }


    /**
     * Teleport the player to the game map when the game start
     *
     * @param player : The player that joins the game
     */
    public void teleportToGame(cbPlayer player) {
        // Attributing the plot
        player.plot = this.game.plotOwners.size();
        this.game.plotOwners.add(player.getName());

        // Teleport the player to his plot
        Location location = Location.fromObject(this.pedestals.get(player.plot));
        player.teleport(location);

        player.setGamemode(Player.CREATIVE);

    }


    public void giveCoins(cbPlayer player, int coins) {
        if (this.isDataBaseEnabled) {
            player.coins += coins;
            player.storedPlayerData.coins += coins;
            String query = "UPDATE data set playerCoins = (playerCoins + " + coins + ") where playerName = '" + player.getName() + "' ;";
            this.getServer().getScheduler().scheduleTask(new dataBaseQuery(query, address, databaseName, username, password), true);
        }
    }
}

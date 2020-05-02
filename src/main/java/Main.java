package main.java;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockIgniteEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.event.player.PlayerItemConsumeEvent;
import cn.nukkit.event.player.PlayerItemHeldEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.util.List;

public class Main extends PluginBase implements Listener {

    public BuildBattleGame game;
    public boolean isDataBaseEnabled = false;
    public boolean isProxyEnabled = false;

    public List<Vector3> pedestals;

    /*
     * TODO
     *  onEnable
     *  Block connections when game has started
     *  Disable PvP, block breaking/placing when not supposed to ...
     *  Give blocks in inventory to vote and get votes
     *  Give coins when winning
     *  Send back to hub when finished
     *  Limit mob spawning per player
     *  Create a config that stores coordinates of plots
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

        // Registering the listeners
        this.getServer().getPluginManager().registerEvents(this, this);
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
}

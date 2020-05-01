package main.java;

import cn.nukkit.Player;
import cn.nukkit.event.Listener;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;

import java.util.List;

public class Main extends PluginBase implements Listener {

    public BuildBattleGame game;

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

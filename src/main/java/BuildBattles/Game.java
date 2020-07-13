package BuildBattles;

import cn.nukkit.Server;
import cn.nukkit.utils.TextFormat;
import de.dytanic.cloudnet.ext.bridge.BridgeHelper;
import de.dytanic.cloudnet.ext.bridge.nukkit.NukkitCloudNetHelper;

import java.util.ArrayList;

/**
 * Created by Guillaume on 08/08/2016.
 */
public abstract class Game {
    final protected Server server;
    final protected Main plugin;
    public final static int GAME_OPEN = 0;
    public final static int GAME_STARTING = 1;
    public final static int GAME_RUNNING = 2;
    public final static int GAME_FINISHED = 3;
    final public int START_DELAY = 60;

    public boolean isFilling = false;

    public int gameNumber;
    public int time;
    public int startTimer;
    public int state = GAME_OPEN;

    public int Capacity = 8;
    public int numberOfTeams;

    private ArrayList<cbPlayer> players = new ArrayList<cbPlayer>();


    public Game(int gameNumber, Server server, Main plugin) {
        this.gameNumber = gameNumber;
        this.plugin = plugin;
        this.resetGame();
        this.server = server;
    }

    public void addPlayer(cbPlayer player) {

        if (!this.players.contains(player)) {
            this.players.add(player);
        } else {
            this.server.getLogger().error(player.getName() + " was added multiple times to the game !");
        }
        player.isInGame = true;
    }

    public void removePlayer(cbPlayer player) {
        this.players.remove(player);
        player.isInGame = false;
        if (startTimer > 0 && this.players.size() < 2) { // Start timer
            startTimer = 0;
        }
    }

    public ArrayList<cbPlayer> getPlayers() {
        return this.players;
    }

    /**
     * Called every second.
     */
    public void tick() {
        // If the game is opened and has more than two players ...
        if (this.state == GAME_OPEN) {
            if (startTimer > 0) {
                startTimer++;
            }
            if (startTimer >= START_DELAY) {
                this.startGame();
                startTimer = 0;
            }
        }
    }

    public void startGame() {
        this.state = Game.GAME_RUNNING;
        this.isFilling = false;
        for (cbPlayer player : players) {
            this.plugin.teleportToGame(player);
            player.sendMessage(TextFormat.GREEN + "> The game has started!");
        }

        NukkitCloudNetHelper.setState("RUNNING");
        BridgeHelper.updateServiceInfo();

    }

    public boolean hasStarted(){
        return this.state == GAME_RUNNING;
    }


    public void resetGame() {
        this.state = GAME_OPEN;
        this.time = 0;
        this.startTimer = 0;
        players = new ArrayList<cbPlayer>();
        NukkitCloudNetHelper.setState("OPEN");
        NukkitCloudNetHelper.setMaxPlayers(Capacity);
        BridgeHelper.updateServiceInfo();
    }

    public abstract boolean isGameEnded();


}

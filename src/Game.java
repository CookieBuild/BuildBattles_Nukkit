import cn.nukkit.Server;

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
    final public int START_DELAY = 30;

    public boolean isFilling = false;

    public int gameNumber;
    public int time;
    public int startTimer;
    public int state = GAME_OPEN;

    public int Capacity = 12;
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


        switch (this.state) {
            case GAME_OPEN:
                if (startTimer > 0) {
                    startTimer++;
                }
                break;
            case GAME_STARTING:
                if (startTimer >= START_DELAY) {
                    this.startGame();
                    startTimer = 0;
                }

                break;
            default:

                break;
        }
    }

    public void startGame() {
        this.state = Game.GAME_STARTING;
        this.isFilling = false;
        for (cbPlayer player : players) {
            this.plugin.teleportToGame(player);
        }

    }

    public boolean hasStarted(){
        return this.state == GAME_RUNNING;
    }


    public void resetGame() {
        this.state = GAME_OPEN;
        this.time = 0;
        this.startTimer = 0;
        players = new ArrayList<cbPlayer>();

    }

    public abstract boolean isGameEnded();


}

package main.java;

import cn.nukkit.Server;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;

public class BuildBattleGame extends Game {

    /**
     * Length of the game in seconds
     */
    public static int GAME_LENGTH = 300;

    /**
     * The time you have to vote per slot
     */
    public static int VOTE_TIME_PER_SLOT = 20;

    /**
     * Is it time to vote ?
     */
    boolean isVotingTime = false;

    /**
     * Which slot is currently being voted
     */
    int votingSlot = 0;

    /**
     * How many players where in the game when it started
     */
    int numberOfPlayersAtStart;

    /**
     * Used to store the size of a plot
     */
    int plotHeight = 0;
    int plotDown = 0;
    int plotWidth = 0;


    public List<String> plotOwners = new ArrayList<String>();


    public BuildBattleGame(int gameNumber, Server server, Main plugin) {
        super(gameNumber, server, plugin);
    }

    @Override
    public void startGame() {
        super.startGame();
        this.numberOfPlayersAtStart = this.getPlayers().size();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasStarted()) {
            this.time++;
        }

        if (this.time >= GAME_LENGTH && !isVotingTime) {
            this.isVotingTime = true;
        }

        int nextVotingSlot = (this.time - GAME_LENGTH) % VOTE_TIME_PER_SLOT;

        if (nextVotingSlot >= numberOfPlayersAtStart) { // If we've voted for everyone
            //TODO : teleport to the winning slot


        } else {
            if (nextVotingSlot != this.votingSlot) {
                //TODO : récolter les votes


                this.votingSlot = nextVotingSlot;
                for (cbPlayer p : this.getPlayers()) {
                    p.teleport(this.plugin.pedestals.get(votingSlot));
                    p.sendMessage(TextFormat.GREEN + "> You are voting the plot of " + TextFormat.RESET + plotOwners.get(votingSlot));

                }

            }
        }
    }

    /**
     * Tells if a coordinate is within the plot of a player
     * @param player : The player which plot needs to be checked
     * @param vector3 : The coordinate that is checked
     * @return true if within plot, else false
     */
    public boolean isInPlot(cbPlayer player, Vector3 vector3) {
        Vector3 plot = this.plugin.pedestals.get(player.plot);

        return (plot.x + plotWidth > vector3.x && plot.x - plotWidth < vector3.x
                && plot.y + plotHeight > vector3.y && plot.y - plotDown < vector3.y
                && plot.z + plotHeight > vector3.z && plot.z - plotHeight < vector3.z);
    }

    @Override
    public boolean isGameEnded() {
        return false;
    }
}

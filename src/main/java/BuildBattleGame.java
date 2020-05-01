package main.java;

import cn.nukkit.Server;
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

    @Override
    public boolean isGameEnded() {
        return false;
    }
}

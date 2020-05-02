package main.java;

import cn.nukkit.Server;
import cn.nukkit.item.Item;
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
    public List<Integer> plotScores = new ArrayList<>();


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
            for (cbPlayer player : this.getPlayers()) {
                player.sendMessage(TextFormat.GREEN + "Time is over ! Voting time");
                player.getInventory().clearAll();
                // We give all the colored clays to vote
                player.getInventory().addItem(Item.get(Item.CLAY, 14));
                player.getInventory().addItem(Item.get(Item.CLAY, 6));
                player.getInventory().addItem(Item.get(Item.CLAY, 5));
                player.getInventory().addItem(Item.get(Item.CLAY, 13));
                player.getInventory().addItem(Item.get(Item.CLAY, 11));
                player.getInventory().addItem(Item.get(Item.CLAY, 4));
            }
        }

        int nextVotingSlot = (this.time - GAME_LENGTH) % VOTE_TIME_PER_SLOT;


        if (nextVotingSlot != this.votingSlot) {


            int score = 0;
            for (cbPlayer player : this.getPlayers()) {
                if (player.plot != votingSlot)
                    score += player.lastVote;
            }

            this.plotScores.add(score);

            this.votingSlot = nextVotingSlot;
            for (cbPlayer p : this.getPlayers()) {
                p.sendMessage(TextFormat.GREEN + "> This plot got " + TextFormat.YELLOW + score + TextFormat.GREEN + " points!");
            }

            if (nextVotingSlot >= numberOfPlayersAtStart) { // If we've voted for everyone
                if (nextVotingSlot == numberOfPlayersAtStart) {
                    //TODO : teleport to the winning slot
                    int bestSlot = 0;
                    int bestScore = 0;
                    for (int pS : this.plotScores) {
                        if (pS > bestScore) {
                            bestScore = pS;
                            bestSlot = this.plotScores.indexOf(bestScore);
                        }
                    }

                    for (cbPlayer p : this.getPlayers()) {
                        p.teleport(this.plugin.pedestals.get(bestSlot));
                        p.sendMessage(TextFormat.GREEN + "> The winner is... " + TextFormat.RESET + plotOwners.get(votingSlot)
                                + TextFormat.GREEN + " with " + TextFormat.YELLOW + bestScore + TextFormat.GREEN + " points!");
                        p.sendMessage(TextFormat.GREEN + "> You had " + TextFormat.YELLOW + plotScores.get(p.plot)
                                + TextFormat.GREEN + " points");
                    }
                } else {
                    for (cbPlayer p : this.getPlayers()) {
                        if (this.plugin.isProxyEnabled) {
                            p.proxyTransfer("BbLobby-1");
                        } else {
                            p.kick("End of game.");
                        }

                    }
                }


            } else {
                for (cbPlayer p : this.getPlayers()) {
                    p.teleport(this.plugin.pedestals.get(votingSlot));
                    p.sendMessage(TextFormat.GREEN + "> You are voting the plot of " + TextFormat.RESET + plotOwners.get(votingSlot));

                }

            }


        }
    }

    /**
     * Tells if a coordinate is within the plot of a player
     *
     * @param player  : The player which plot needs to be checked
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

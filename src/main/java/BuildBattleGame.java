package main.java;

import cn.nukkit.Server;
import cn.nukkit.item.Item;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    int votingSlot = -1;

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

    String theme = "";


    public List<String> plotOwners = new ArrayList<String>();
    public List<Integer> plotScores = new ArrayList<>();


    public BuildBattleGame(int gameNumber, Server server, Main plugin) {
        super(gameNumber, server, plugin);
    }

    @Override
    public void startGame() {
        super.startGame();
        this.numberOfPlayersAtStart = this.getPlayers().size();
        for (cbPlayer player : this.getPlayers()) {
            player.sendMessage(TextFormat.GREEN + "> Use /floor while holding a block to set the floor of your plot!");
        }
    }

    @Override
    public void addPlayer(cbPlayer player) {
        super.addPlayer(player);
        if (startTimer == 0 && this.getPlayers().size() >= 2) {
            startTimer += 1;
        }
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
                player.getInventory().addItem(Item.get(Item.STAINED_HARDENED_CLAY, 14));
                player.getInventory().addItem(Item.get(Item.STAINED_HARDENED_CLAY, 6));
                player.getInventory().addItem(Item.get(Item.STAINED_HARDENED_CLAY, 5));
                player.getInventory().addItem(Item.get(Item.STAINED_HARDENED_CLAY, 13));
                player.getInventory().addItem(Item.get(Item.STAINED_HARDENED_CLAY, 11));
                player.getInventory().addItem(Item.get(Item.STAINED_HARDENED_CLAY, 4));
            }
        }
        if (isVotingTime) {
            int nextVotingSlot = Math.floorDiv((this.time - GAME_LENGTH), VOTE_TIME_PER_SLOT);


            if (nextVotingSlot != this.votingSlot) {



                if (nextVotingSlot >= numberOfPlayersAtStart) { // If we've voted for everyone
                    if (nextVotingSlot == numberOfPlayersAtStart) {
                        if ((this.time - GAME_LENGTH) % VOTE_TIME_PER_SLOT == 0) {
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
                                p.sendMessage(TextFormat.GREEN + "> The winner is... " + TextFormat.RESET + plotOwners.get(bestSlot)
                                        + TextFormat.GREEN + " with " + TextFormat.YELLOW + bestScore + TextFormat.GREEN + " points!");
                                if (p.plot == bestSlot) {
                                    int coinsReceived = this.plugin.giveCoins(p, bestScore);
                                    p.sendMessage(TextFormat.GREEN + "> You won! You received " + coinsReceived + " coins!");
                                }
                                p.sendMessage(TextFormat.GREEN + "> You had " + TextFormat.YELLOW + plotScores.get(p.plot)
                                        + TextFormat.GREEN + " points");
                            }
                        }

                    } else {
                        for (cbPlayer p : this.getPlayers()) {
                            if (this.plugin.isProxyEnabled) {
                                p.proxyTransfer("Lobby-1");
                            } else {
                                p.kick("End of game.");
                            }


                        }

                        for (cbPlayer p : this.getPlayers()) {
                            p.kick("End of game.");
                            // We kick again if there are still a proxy player
                        }
                        // Game has ended. Everyone is gone, time to reset
                        this.resetGame();

                        // Unload + reload to reset map
                        this.server.unloadLevel(this.server.getLevelByName("game"), true);
                        this.server.loadLevel("game");
                    }


                } else {

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


                    for (cbPlayer p : this.getPlayers()) {
                        p.teleport(this.plugin.pedestals.get(votingSlot).add(0, 6, 0));
                        p.sendMessage(TextFormat.GREEN + "> You are voting the plot of " + TextFormat.RESET + plotOwners.get(votingSlot));

                    }

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
                && plot.z + plotWidth > vector3.z && plot.z - plotWidth < vector3.z);
    }

    @Override
    public boolean isGameEnded() {
        return false;
    }

    @Override
    public void resetGame() {
        super.resetGame();
        this.plotOwners = new ArrayList<String>();
        this.plotScores = new ArrayList<>();
        this.votingSlot = -1;
        this.isVotingTime = false;
        this.theme = this.plugin.themes[new Random().nextInt(this.plugin.themes.length)];
    }
}

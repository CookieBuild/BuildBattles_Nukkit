package com.cookiebuild.buildbattles.game;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.buildbattles.map.GameMap;
import com.cookiebuild.buildbattles.map.MapManager;
import com.cookiebuild.buildbattles.map.MapTemplate;
import com.cookiebuild.buildbattles.map.PlotBounds;
import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;
import com.cookiebuild.buildbattles.security.BuildResourceBudget;
import com.cookiebuild.buildbattles.security.BuildResourceBudget.AddResult;
import com.cookiebuild.buildbattles.security.BuildResourceBudget.BlockKey;
import com.cookiebuild.buildbattles.security.BuildResourceBudget.EntityCategory;
import com.cookiebuild.buildbattles.security.FluidSafetyPolicy;
import com.cookiebuild.buildbattles.security.RedstoneActivityLimiter;
import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.FunnelTelemetry;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.MatchService;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

public final class BuildBattlesGame extends Game {
    public static final String MINIGAME_KEY = "buildbattles";

    public enum FloorChangeResult {
        STARTED,
        NOT_BUILDING,
        UNSAFE_MATERIAL,
        COOLDOWN,
        UNAVAILABLE
    }

    private final GameMap map;
    private final ThemeBallot themeBallot;
    private final MatchService matchService = new MatchService(null);
    private final MinigameProgressionService progression = CookieDough.createMinigameProgressionService();
    private final BuildStats stats = new BuildStats();
    private final BuildResourceBudget resources = createResourceBudget();
    private final RedstoneActivityLimiter redstoneLimiter = createRedstoneLimiter();
    private final BuildBattlesScoreboard scoreboard = new BuildBattlesScoreboard();
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Map<UUID, CookiePlayer> activePlayers = new LinkedHashMap<>();
    private final Map<UUID, Integer> plotByPlayer = new LinkedHashMap<>();
    private final Map<Integer, UUID> playerByPlot = new LinkedHashMap<>();
    private final Map<UUID, Long> disconnectedAt = new HashMap<>();
    private final Set<UUID> forfeited = new LinkedHashSet<>();
    private final Map<UUID, Long> lastFloorChange = new HashMap<>();
    private final List<BukkitTask> floorTasks = new ArrayList<>();
    private final Object matchPersistenceLock = new Object();

    private BuildPhase phase = BuildPhase.WAITING;
    private VoteLedger voteLedger;
    private volatile Match match;
    private boolean matchStartPending;
    private PendingMatchOutcome pendingMatchOutcome;
    private String theme;
    private int phaseSeconds;
    private int judgingPlot = -1;
    private boolean outcomePersisted;
    private boolean cleanupStarted;

    private record PendingMatchOutcome(Set<UUID> winners, List<MatchService.Performance> performances) {
        private PendingMatchOutcome {
            winners = Set.copyOf(winners);
            performances = List.copyOf(performances);
        }
    }

    public BuildBattlesGame() {
        super("BuildBattles");
        START_DELAY_SECONDS = 30;
        QUICK_START_DELAY_SECONDS = 10;
        try {
            MapTemplate template = MapManager.selectAvailable();
            map = MapManager.load(getGameId(), template);
            setCapacity(template.capacity());
            List<String> themes = BuildBattles.getInstance().getConfig().getStringList("themes");
            themeBallot = new ThemeBallot(themes,
                    BuildBattles.getInstance().getConfig().getInt("game.theme-candidates", 3),
                    ThreadLocalRandom.current());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("BuildBattles preparation failed: " + error.getMessage(), error);
        }
    }

    private static BuildResourceBudget createResourceBudget() {
        var config = BuildBattles.getInstance().getConfig();
        return new BuildResourceBudget(
                config.getInt("safety.max-entities-per-player", 16),
                config.getInt("safety.max-decoration-entities-per-player", 12),
                config.getInt("safety.max-armor-stands-per-player", 4),
                config.getInt("safety.max-living-entities-per-player", 4),
                config.getInt("safety.max-villagers-per-player", 2),
                config.getInt("safety.max-redstone-components-per-player", 64),
                config.getInt("safety.max-lava-sources-per-player", 8));
    }

    private static RedstoneActivityLimiter createRedstoneLimiter() {
        var config = BuildBattles.getInstance().getConfig();
        return new RedstoneActivityLimiter(
                config.getInt("safety.max-redstone-updates-per-second", 128),
                config.getLong("safety.redstone-cooldown-seconds", 5) * 1_000L);
    }

    @Override
    public void registerANewGame() {
        Bukkit.getScheduler().runTask(BuildBattles.getInstance(), BuildBattles::activateNextGame);
    }

    @Override
    public synchronized boolean addPlayer(CookiePlayer cookiePlayer) {
        if (phase != BuildPhase.WAITING || !super.addPlayer(cookiePlayer)) return false;
        UUID id = cookiePlayer.getPlayer().getUniqueId();
        participants.add(id);
        activePlayers.put(id, cookiePlayer);
        stats.register(id);
        try {
            prepareWaitingPlayer(cookiePlayer);
            return true;
        } catch (RuntimeException error) {
            participants.remove(id);
            activePlayers.remove(id);
            super.removePlayer(cookiePlayer, "admission_failed");
            cookiePlayer.setState(PlayerState.LOBBY);
            return false;
        }
    }

    private void prepareWaitingPlayer(CookiePlayer cookiePlayer) {
        Player player = cookiePlayer.getPlayer();
        cookiePlayer.resetPlayer();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.teleport(map.template().waitingSpawn(map.world()));
        for (int index = 0; index < themeBallot.candidates().size(); index++) {
            String candidate = themeBallot.candidates().get(index);
            ItemStack paper = new ItemStack(Material.PAPER);
            ItemMeta meta = paper.getItemMeta();
            meta.displayName(Component.text(candidate, NamedTextColor.AQUA));
            meta.lore(List.of(Component.text("Use to vote for this theme", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(BuildBattles.getInstance().getThemeKey(),
                    PersistentDataType.STRING, candidate);
            paper.setItemMeta(meta);
            player.getInventory().setItem(index, paper);
        }
        player.showTitle(Title.title(
                Component.text(BuildBattles.message(player, "bb.waiting.title"), NamedTextColor.GOLD,
                        TextDecoration.BOLD),
                Component.text(BuildBattles.message(player, "bb.waiting.subtitle"), NamedTextColor.YELLOW),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
        player.sendMessage(Component.text(BuildBattles.message(player, "bb.waiting.area"), NamedTextColor.AQUA));
        player.sendMessage(themePrompt(player));
    }

    private Component themePrompt(Player player) {
        Component prompt = Component.text(BuildBattles.message(player, "bb.theme.prompt") + " ", NamedTextColor.YELLOW);
        for (String candidate : themeBallot.candidates()) {
            prompt = prompt.append(Component.text("[" + candidate + "] ", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand("/bbtheme " + candidate))
                    .hoverEvent(HoverEvent.showText(Component.text("Vote " + candidate))));
        }
        return prompt;
    }

    public boolean voteTheme(Player player, String requested) {
        if (phase != BuildPhase.WAITING || !participants.contains(player.getUniqueId())) return false;
        boolean accepted = themeBallot.vote(player.getUniqueId(), requested);
        if (accepted) player.sendMessage(Component.text(
                BuildBattles.message(player, "bb.theme.recorded", requested), NamedTextColor.GREEN));
        return accepted;
    }

    @Override
    public void startGame() {
        long startedAt = System.nanoTime();
        List<CookiePlayer> starters = getPlayers();
        if (starters.size() < 2 || starters.size() > map.template().capacity()) return;
        int plot = 0;
        for (CookiePlayer cookiePlayer : starters) {
            UUID id = cookiePlayer.getPlayer().getUniqueId();
            plotByPlayer.put(id, plot);
            playerByPlot.put(plot, id);
            plot++;
        }
        voteLedger = new VoteLedger(playerByPlot);
        theme = themeBallot.winner(ThreadLocalRandom.current());
        phase = BuildPhase.BUILDING;
        phaseSeconds = 0;
        long rosterReadyAt = System.nanoTime();
        super.startGame();
        long playersReadyAt = System.nanoTime();
        startMatchPersistence();
        activePlayers.values().forEach(player -> player.getPlayer().showTitle(Title.title(
                Component.text(theme, NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(BuildBattles.message(player.getPlayer(), "bb.build.start"), NamedTextColor.GREEN))));
        long finishedAt = System.nanoTime();
        BuildBattles.getInstance().getLogger().info("BuildBattles match start timing: game=" + getGameId()
                + " roster_ms=" + elapsedMillis(startedAt, rosterReadyAt)
                + " player_prepare_ms=" + elapsedMillis(rosterReadyAt, playersReadyAt)
                + " telemetry_schedule_and_titles_ms=" + elapsedMillis(playersReadyAt, finishedAt)
                + " total_ms=" + elapsedMillis(startedAt, finishedAt));
    }

    private static long elapsedMillis(long startedAt, long finishedAt) {
        return (finishedAt - startedAt) / 1_000_000L;
    }

    private void startMatchPersistence() {
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        Set<UUID> playerIds = Set.copyOf(participants);
        synchronized (matchPersistenceLock) {
            matchStartPending = true;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                Match startedMatch = null;
                try {
                    startedMatch = matchService.startMatchByPlayerIds("BuildBattles", playerIds);
                } catch (RuntimeException error) {
                    plugin.getLogger().severe(
                            "BuildBattles continues without match telemetry: " + error.getMessage());
                }
                PendingMatchOutcome pending;
                synchronized (matchPersistenceLock) {
                    match = startedMatch;
                    matchStartPending = false;
                    pending = pendingMatchOutcome;
                    pendingMatchOutcome = null;
                }
                if (startedMatch != null && pending != null) {
                    completePersistedMatch(plugin, startedMatch, pending);
                }
            });
        } catch (RuntimeException error) {
            synchronized (matchPersistenceLock) {
                matchStartPending = false;
            }
            plugin.getLogger().severe(
                    "Could not schedule BuildBattles match telemetry: " + error.getMessage());
        }
    }

    @Override
    protected void teleportToGame(CookiePlayer cookiePlayer) {
        Integer plot = plotByPlayer.get(cookiePlayer.getPlayer().getUniqueId());
        if (plot == null) throw new IllegalStateException("Player has no assigned plot");
        prepareBuilder(cookiePlayer, plot);
    }

    private void prepareBuilder(CookiePlayer cookiePlayer, int plot) {
        Player player = cookiePlayer.getPlayer();
        cookiePlayer.resetPlayer();
        cookiePlayer.setState(PlayerState.IN_GAME);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.teleport(map.template().plotCenter(map.world(), plot));
        BuildBattles.givePaletteShortcut(player);
        player.sendMessage(Component.text(BuildBattles.message(player, "bb.floor.hint"), NamedTextColor.YELLOW));
        player.sendMessage(Component.text(BuildBattles.message(player, "bb.build.palette"), NamedTextColor.AQUA));
    }

    @Override
    public void tick() {
        super.tick();
        updateDisplay();
        if (phase == BuildPhase.WAITING || phase == BuildPhase.FINISHED) return;
        if (stopAbandonedMatch()) return;
        phaseSeconds++;
        switch (phase) {
            case BUILDING -> {
                if (phaseSeconds >= buildSeconds()) startJudging();
            }
            case JUDGING -> {
                if (phaseSeconds >= judgingSeconds()) advanceJudging();
            }
            case RESULTS -> {
                if (phaseSeconds >= resultsSeconds()) cleanup();
            }
            default -> { }
        }
    }

    private void updateDisplay() {
        int remaining = switch (phase) {
            case BUILDING -> Math.max(0, buildSeconds() - phaseSeconds);
            case JUDGING -> Math.max(0, judgingSeconds() - phaseSeconds);
            case RESULTS -> Math.max(0, resultsSeconds() - phaseSeconds);
            default -> 0;
        };
        for (CookiePlayer cookiePlayer : activePlayers.values()) {
            Player player = cookiePlayer.getPlayer();
            if (!player.isOnline()) continue;
            String phaseName = BuildBattles.message(player, "bb.phase." + phase.name().toLowerCase());
            if (phase == BuildPhase.WAITING) {
                WaitingStatus waiting = WaitingStatus.from(getPlayers().size(), getCapacity(), getMinimumPlayers(),
                        getStartTimer(), inQuickStart ? QUICK_START_DELAY_SECONDS : START_DELAY_SECONDS);
                String status = waiting.isCountingDown()
                        ? BuildBattles.message(player, "bb.waiting.starting", waiting.secondsRemaining(),
                                waiting.players(), waiting.capacity())
                        : BuildBattles.message(player, "bb.waiting.players", waiting.players(), waiting.capacity(),
                                waiting.morePlayersNeeded());
                player.sendActionBar(Component.text(status, waiting.isCountingDown()
                        ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            } else {
                player.sendActionBar(Component.text(theme + " · " + phaseName + " · " + remaining + "s",
                        NamedTextColor.YELLOW));
            }
            BuildStats.Snapshot snapshot = stats.snapshot(player.getUniqueId());
            scoreboard.update(player, List.of(
                    "§6Theme: §f" + (theme == null ? BuildBattles.message(player, "bb.theme.voting") : theme),
                    "§6Phase: §f" + phaseName,
                    " ",
                    phase == BuildPhase.WAITING
                            ? "§6Players: §f" + getPlayers().size() + "/" + getCapacity()
                            : "§6Time: §f" + String.format("%d:%02d", remaining / 60, remaining % 60),
                    "§6Blocks: §a" + snapshot.blocksPlaced(),
                    phase == BuildPhase.WAITING ? "§b" + BuildBattles.message(player, "bb.waiting.area_short")
                            : judgingPlot >= 0 ? "§6Plot: §f" + (judgingPlot + 1) + "/" + playerByPlot.size()
                                    : "§7/floor changes the floor"));
        }
    }

    private void startJudging() {
        cancelFloorTasks();
        phase = BuildPhase.JUDGING;
        phaseSeconds = 0;
        judgingPlot = 0;
        prepareJudgingPlot();
    }

    private void advanceJudging() {
        judgingPlot++;
        phaseSeconds = 0;
        if (judgingPlot >= playerByPlot.size()) {
            showResults();
        } else {
            prepareJudgingPlot();
        }
    }

    private void prepareJudgingPlot() {
        for (CookiePlayer cookiePlayer : activePlayers.values()) {
            Player player = cookiePlayer.getPlayer();
            if (!player.isOnline()) continue;
            cookiePlayer.resetPlayer();
            cookiePlayer.setState(PlayerState.SPECTATING);
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.teleport(map.template().judgingLocation(map.world(), judgingPlot));
            giveVoteItems(player);
            player.sendMessage(Component.text(BuildBattles.message(player, "bb.judging.anonymous"), NamedTextColor.AQUA));
        }
    }

    private void giveVoteItems(Player player) {
        Material[] materials = {Material.RED_TERRACOTTA, Material.ORANGE_TERRACOTTA,
                Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.CYAN_TERRACOTTA};
        for (int index = 0; index < materials.length; index++) {
            int value = index + 1;
            ItemStack item = new ItemStack(materials[index]);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(value + "/5 · " + BuildBattles.message(player, "bb.vote." + value),
                    value >= 4 ? NamedTextColor.GREEN : value <= 2 ? NamedTextColor.RED : NamedTextColor.YELLOW));
            meta.lore(List.of(Component.text("Use to submit your one vote", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(BuildBattles.getInstance().getVoteKey(), PersistentDataType.INTEGER, value);
            item.setItemMeta(meta);
            player.getInventory().setItem(index, item);
        }
        player.sendMessage(Component.text(BuildBattles.message(player, "bb.vote.bedrock"), NamedTextColor.YELLOW));
    }

    public VoteLedger.Result vote(Player player, int score) {
        if (phase != BuildPhase.JUDGING || judgingPlot < 0 || !participants.contains(player.getUniqueId())) {
            return VoteLedger.Result.INVALID_SCORE;
        }
        VoteLedger.Result result = voteLedger.vote(judgingPlot, player.getUniqueId(), score);
        String key = switch (result) {
            case ACCEPTED -> "bb.vote.accepted";
            case SELF_VOTE -> "bb.vote.self";
            case ALREADY_VOTED -> "bb.vote.duplicate";
            case INVALID_SCORE -> "bb.vote.invalid";
        };
        player.sendMessage(Component.text(BuildBattles.message(player, key, score),
                result == VoteLedger.Result.ACCEPTED ? NamedTextColor.GREEN : NamedTextColor.RED));
        return result;
    }

    private void showResults() {
        phase = BuildPhase.RESULTS;
        phaseSeconds = 0;
        judgingPlot = -1;
        BuildResultRanking.Result result = BuildResultRanking.calculate(playerByPlot, voteLedger, forfeited);
        Map<UUID, Integer> placements = new HashMap<>();
        result.entries().forEach(entry -> placements.put(entry.playerId(), entry.placement()));
        Set<UUID> winners = result.winners();
        Integer bestEligiblePlot = result.winningPlot();
        Location resultLocation = bestEligiblePlot == null ? map.template().waitingSpawn(map.world())
                : map.template().judgingLocation(map.world(), bestEligiblePlot);
        for (CookiePlayer cookiePlayer : activePlayers.values()) {
            Player player = cookiePlayer.getPlayer();
            if (!player.isOnline()) continue;
            player.teleport(resultLocation);
            boolean won = winners.contains(player.getUniqueId());
            player.showTitle(Title.title(
                    Component.text(won ? BuildBattles.message(player, "bb.result.victory")
                                    : BuildBattles.message(player, "bb.result.finished"),
                            won ? NamedTextColor.GOLD : NamedTextColor.AQUA, TextDecoration.BOLD),
                    Component.text(resultSummary(player, result),
                            NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500))));
            sendRanking(player, result);
            sendReplay(player);
        }
        persistOutcome(winners, placements, false, true);
    }

    private String resultSummary(Player player, BuildResultRanking.Result result) {
        if (result.winners().isEmpty()) {
            return BuildBattles.message(player, "bb.result.no_votes");
        }
        Map<UUID, BuildResultRanking.Entry> entriesByPlayer = result.entries().stream()
                .collect(java.util.stream.Collectors.toMap(BuildResultRanking.Entry::playerId, entry -> entry));
        String summary = result.winners().stream().map(playerId -> {
            BuildResultRanking.Entry entry = entriesByPlayer.get(playerId);
            return BuildBattles.message(player, "bb.result.summary", playerName(playerId), entry.points(),
                    entry.votes(), formatRating(entry.average()));
        }).collect(java.util.stream.Collectors.joining(", "));
        return BuildBattles.message(player, result.winners().size() == 1 ? "bb.result.winner" : "bb.result.tie",
                summary);
    }

    private void sendRanking(Player player, BuildResultRanking.Result result) {
        player.sendMessage(Component.text(BuildBattles.message(player, "bb.result.ranking"), NamedTextColor.GOLD,
                TextDecoration.BOLD));
        for (BuildResultRanking.Entry entry : result.entries()) {
            String status = entry.forfeited() ? BuildBattles.message(player, "bb.result.forfeit") : "";
            NamedTextColor color = entry.placement() == 1 && !entry.forfeited()
                    ? NamedTextColor.GOLD : NamedTextColor.GRAY;
            player.sendMessage(Component.text(BuildBattles.message(player, "bb.result.rank_line",
                    entry.placement(), playerName(entry.playerId()), entry.points(), entry.votes(),
                    formatRating(entry.average()), status), color));
        }
    }

    private String formatRating(double rating) {
        return String.format(java.util.Locale.ROOT, "%.2f", rating);
    }

    private void sendReplay(Player player) {
        FunnelTelemetry.record(player, FunnelTelemetry.Event.MATCH_COMPLETED, "game=BuildBattles");
        player.sendMessage(Component.text(BuildBattles.message(player, "bb.replay"), NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(replayCommand()))
                .hoverEvent(HoverEvent.showText(Component.text(BuildBattles.message(player, "bb.replay.hover")))));
    }

    static String replayCommand() {
        return "/buildbattles replay";
    }

    private void persistOutcome(Set<UUID> winners, Map<UUID, Integer> placements,
            boolean interrupted, boolean rewards) {
        if (outcomePersisted) return;
        outcomePersisted = true;
        List<MatchService.Performance> performances = participants.stream().map(playerId -> {
            int plot = plotByPlayer.getOrDefault(playerId, -1);
            BuildStats.Snapshot snapshot = stats.snapshot(playerId);
            int totalScore = plot < 0 || voteLedger == null ? 0 : voteLedger.total(plot);
            double rating = plot < 0 || voteLedger == null ? 0.0 : voteLedger.average(plot);
            int votes = plot < 0 || voteLedger == null ? 0 : voteLedger.voteCount(plot);
            return new MatchService.Performance(playerId, 0, 0, 0, Map.ofEntries(
                    Map.entry("theme", theme == null ? "" : theme),
                    Map.entry("map", map.template().name()),
                    Map.entry("plot", plot),
                    Map.entry("score", totalScore),
                    Map.entry("rating", rating),
                    Map.entry("votesReceived", votes),
                    Map.entry("placement", placements.getOrDefault(playerId, 0)),
                    Map.entry("blocksPlaced", snapshot.blocksPlaced()),
                    Map.entry("blocksBroken", snapshot.blocksBroken()),
                    Map.entry("floorChanges", snapshot.floorChanges()),
                    Map.entry("interrupted", interrupted)));
        }).toList();
        persistMatchOutcome(new PendingMatchOutcome(winners, performances));
        if (!rewards) return;
        Match persistedMatch = match;
        String sourceId = persistedMatch == null ? getGameId().toString() : persistedMatch.getId().toString();
        for (UUID playerId : participants) {
            if (forfeited.contains(playerId)) continue;
            boolean won = winners.contains(playerId);
            int coins = BuildBattles.getInstance().getConfig().getInt("rewards.participation-coins", 5)
                    + (won ? BuildBattles.getInstance().getConfig().getInt("rewards.victory-coins", 20) : 0);
            int xp = BuildBattles.getInstance().getConfig().getInt("rewards.participation-xp", 20)
                    + (won ? BuildBattles.getInstance().getConfig().getInt("rewards.victory-xp", 80) : 0);
            try {
                progression.applyReward(playerId, MINIGAME_KEY, xp, coins,
                        "match:" + sourceId + ":buildbattles-reward");
                LobbyScoreboard.invalidatePlayerCache(playerId);
                CookieDough.getInstance().getGoalTracker().recordMatch(playerId, "BuildBattles", won, 0);
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) player.sendMessage(Component.text(
                        BuildBattles.message(player, "bb.reward", coins, xp), NamedTextColor.GREEN));
            } catch (RuntimeException error) {
                BuildBattles.getInstance().getLogger().warning("Could not reward " + playerId + ": " + error.getMessage());
            }
        }
    }

    private void persistMatchOutcome(PendingMatchOutcome outcome) {
        Match persistedMatch;
        synchronized (matchPersistenceLock) {
            if (matchStartPending) {
                pendingMatchOutcome = outcome;
                return;
            }
            persistedMatch = match;
        }
        if (persistedMatch == null) return;
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> completePersistedMatch(plugin, persistedMatch, outcome));
        } catch (RuntimeException error) {
            plugin.getLogger().severe(
                    "Could not schedule BuildBattles result persistence: " + error.getMessage());
        }
    }

    private void completePersistedMatch(BuildBattles plugin, Match persistedMatch, PendingMatchOutcome outcome) {
        try {
            matchService.completeMatchByWinnerIds(persistedMatch, outcome.winners(), outcome.performances());
        } catch (RuntimeException error) {
            plugin.getLogger().severe(
                    "Could not persist BuildBattles result: " + error.getMessage());
        }
    }

    public boolean owns(World world) {
        return world != null && world.getKey().equals(map.world().getKey());
    }

    public boolean isParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    public boolean isActive(UUID playerId) {
        return activePlayers.containsKey(playerId);
    }

    public BuildPhase getPhase() { return phase; }

    public boolean isInsideOwnPlot(Player player, Location location) {
        Integer plot = plotByPlayer.get(player.getUniqueId());
        return phase == BuildPhase.BUILDING && plot != null && location != null && owns(location.getWorld())
                && map.template().bounds(plot).contains(location.getX(), location.getY(), location.getZ());
    }

    public boolean isInsideOwnPlot(Player player, Entity entity) {
        if (entity == null || !owns(entity.getWorld())) return false;
        Integer plot = plotByPlayer.get(player.getUniqueId());
        if (phase != BuildPhase.BUILDING || plot == null) return false;
        PlotBounds bounds = map.template().bounds(plot);
        BoundingBox box = entity.getBoundingBox();
        return bounds.contains(box.getMinX(), box.getMinY(), box.getMinZ())
                && bounds.contains(box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    public UUID plotOwner(Location location) {
        if (location == null || !owns(location.getWorld())) return null;
        for (Map.Entry<Integer, UUID> entry : playerByPlot.entrySet()) {
            if (map.template().bounds(entry.getKey()).contains(
                    location.getX(), location.getY(), location.getZ())) {
                return entry.getValue();
            }
        }
        return null;
    }

    public AddResult trackBuildEntity(Player player, Entity entity, EntityCategory category) {
        if (!isInsideOwnPlot(player, entity)) return AddResult.TOTAL_LIMIT;
        boolean villager = entity.getType() == EntityType.VILLAGER;
        AddResult result = resources.tryAddEntity(
                player.getUniqueId(), entity.getUniqueId(), category, villager);
        if (!result.accepted()) return result;
        entity.getPersistentDataContainer().set(BuildBattles.getInstance().getEntityOwnerKey(),
                PersistentDataType.STRING, player.getUniqueId().toString());
        entity.getPersistentDataContainer().set(BuildBattles.getInstance().getEntityCategoryKey(),
                PersistentDataType.STRING, category.name());
        configureBuildEntity(entity);
        return result;
    }

    private void configureBuildEntity(Entity entity) {
        entity.setPersistent(true);
        entity.setSilent(true);
        entity.setGravity(false);
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setCanPickupItems(false);
            living.setRemoveWhenFarAway(false);
        }
        if (entity instanceof Mob mob) mob.setAware(false);
    }

    public UUID buildEntityOwner(Entity entity) {
        return entity == null ? null : resources.entityOwner(entity.getUniqueId());
    }

    public EntityCategory buildEntityCategory(Entity entity) {
        return entity == null ? null : resources.entityCategory(entity.getUniqueId());
    }

    public boolean canEditBuildEntity(Player player, Entity entity) {
        UUID owner = buildEntityOwner(entity);
        return phase == BuildPhase.BUILDING && owner != null
                && owner.equals(player.getUniqueId()) && isInsideOwnPlot(player, entity);
    }

    public void releaseBuildEntity(Entity entity) {
        if (entity != null) resources.removeEntity(entity.getUniqueId());
    }

    public boolean reserveRedstone(Player player, Location location) {
        pruneTrackedBlocks();
        return phase == BuildPhase.BUILDING && isInsideOwnPlot(player, location)
                && resources.tryAddRedstone(player.getUniqueId(), blockKey(location));
    }

    public void releaseRedstone(Location location) {
        if (location != null && owns(location.getWorld())) resources.removeRedstone(blockKey(location));
    }

    public boolean reserveLavaSource(Player player, Location location) {
        pruneTrackedBlocks();
        return phase == BuildPhase.BUILDING && isInsideOwnPlot(player, location)
                && resources.tryAddLavaSource(player.getUniqueId(), blockKey(location));
    }

    public void releaseLavaSource(Location location) {
        if (location != null && owns(location.getWorld())) resources.removeLavaSource(blockKey(location));
    }

    private void pruneTrackedBlocks() {
        resources.pruneRedstone(block -> {
            World world = Bukkit.getWorld(block.worldId());
            return world != null && BuildSafetyPolicy.isRedstoneComponent(
                    world.getBlockAt(block.x(), block.y(), block.z()).getType());
        });
        resources.pruneLavaSources(block -> {
            World world = Bukkit.getWorld(block.worldId());
            return world != null && world.getBlockAt(block.x(), block.y(), block.z()).getType() == Material.LAVA;
        });
    }

    public RedstoneActivityLimiter.Decision recordRedstoneUpdate(UUID owner, long nowMillis) {
        return owner == null ? RedstoneActivityLimiter.Decision.BLOCKED
                : redstoneLimiter.evaluate(owner, nowMillis);
    }

    private BlockKey blockKey(Location location) {
        return new BlockKey(location.getWorld().getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public boolean canFluidFlow(Location source, Location destination) {
        if (phase != BuildPhase.BUILDING || source == null || destination == null
                || !owns(source.getWorld()) || !owns(destination.getWorld())) {
            return false;
        }
        List<PlotBounds> assignedPlots = playerByPlot.keySet().stream()
                .map(map.template()::bounds)
                .toList();
        return FluidSafetyPolicy.staysWithinSinglePlot(assignedPlots,
                source.getX(), source.getY(), source.getZ(),
                destination.getX(), destination.getY(), destination.getZ());
    }

    public void recordPlaced(Player player) { stats.placed(player.getUniqueId()); }
    public void recordBroken(Player player) { stats.broken(player.getUniqueId()); }

    public FloorChangeResult changeFloor(Player player, Material material) {
        if (!isParticipant(player.getUniqueId()) || phase != BuildPhase.BUILDING) {
            return FloorChangeResult.NOT_BUILDING;
        }
        if (material == null || !material.isBlock() || !material.isItem()
                || !BuildSafetyPolicy.isSafeFloorBlock(material)) {
            return FloorChangeResult.UNSAFE_MATERIAL;
        }
        long now = System.currentTimeMillis();
        long cooldown = BuildBattles.getInstance().getConfig().getLong("floor.cooldown-seconds", 5) * 1000L;
        if (now - lastFloorChange.getOrDefault(player.getUniqueId(), 0L) < cooldown) {
            return FloorChangeResult.COOLDOWN;
        }
        Integer plot = plotByPlayer.get(player.getUniqueId());
        if (plot == null) return FloorChangeResult.UNAVAILABLE;
        lastFloorChange.put(player.getUniqueId(), now);
        stats.floorChanged(player.getUniqueId());
        PlotBounds bounds = map.template().bounds(plot);
        List<Block> blocks = new ArrayList<>();
        for (int x = bounds.minBlockX(); x <= bounds.maxBlockX(); x++) {
            for (int z = bounds.minBlockZ(); z <= bounds.maxBlockZ(); z++) {
                blocks.add(map.world().getBlockAt(x, bounds.floorY(), z));
            }
        }
        int batch = Math.max(50, BuildBattles.getInstance().getConfig().getInt("floor.blocks-per-tick", 300));
        BukkitRunnable task = new BukkitRunnable() {
            private int index;
            @Override public void run() {
                if (phase != BuildPhase.BUILDING || index >= blocks.size()) {
                    cancel();
                    return;
                }
                int end = Math.min(blocks.size(), index + batch);
                while (index < end) blocks.get(index++).setType(material, false);
                if (index >= blocks.size()) cancel();
            }
        };
        floorTasks.add(task.runTaskTimer(BuildBattles.getInstance(), 0L, 1L));
        return FloorChangeResult.STARTED;
    }

    public void keepInsidePlot(Player player, Location destination) {
        if (destination == null || !isParticipant(player.getUniqueId())) return;
        if (phase == BuildPhase.WAITING) {
            Location waiting = map.template().waitingSpawn(map.world());
            if (destination.getWorld() != waiting.getWorld() || destination.distanceSquared(waiting) > 16.0) {
                player.teleport(waiting);
            }
            return;
        }
        if (phase == BuildPhase.BUILDING && !isInsideOwnPlot(player, destination)) {
            Integer plot = plotByPlayer.get(player.getUniqueId());
            if (plot != null) player.teleport(map.template().plotCenter(map.world(), plot));
            return;
        }
        if (phase == BuildPhase.JUDGING && judgingPlot >= 0) {
            Location center = map.template().plotCenter(map.world(), judgingPlot);
            if (destination.getWorld() != center.getWorld()
                    || Math.abs(destination.getX() - center.getX()) > 30.0
                    || Math.abs(destination.getZ() - center.getZ()) > 30.0
                    || destination.getY() < center.getY() - 2.0
                    || destination.getY() > center.getY() + 30.0) {
                player.teleport(map.template().judgingLocation(map.world(), judgingPlot));
            }
        }
    }

    public boolean reconnect(CookiePlayer cookiePlayer) {
        UUID id = cookiePlayer.getPlayer().getUniqueId();
        Long disconnected = disconnectedAt.get(id);
        long grace = BuildBattles.getInstance().getConfig().getLong("game.reconnect-grace-seconds", 60) * 1000L;
        if (disconnected == null || System.currentTimeMillis() - disconnected > grace
                || phase == BuildPhase.WAITING || phase == BuildPhase.FINISHED) return false;
        if (!restorePlayerAfterReconnect(cookiePlayer)) return false;
        activePlayers.put(id, cookiePlayer);
        disconnectedAt.remove(id);
        if (phase == BuildPhase.BUILDING) {
            prepareBuilder(cookiePlayer, plotByPlayer.get(id));
        } else {
            cookiePlayer.resetPlayer();
            cookiePlayer.setState(PlayerState.SPECTATING);
            Player player = cookiePlayer.getPlayer();
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
            Location destination = judgingPlot >= 0 ? map.template().judgingLocation(map.world(), judgingPlot)
                    : map.template().waitingSpawn(map.world());
            player.teleport(destination);
            if (phase == BuildPhase.JUDGING) giveVoteItems(player);
        }
        cookiePlayer.getPlayer().sendMessage(Component.text(
                BuildBattles.message(cookiePlayer.getPlayer(), "bb.reconnected"), NamedTextColor.GREEN));
        return true;
    }

    private boolean stopAbandonedMatch() {
        long now = System.currentTimeMillis();
        long grace = BuildBattles.getInstance().getConfig().getLong("game.reconnect-grace-seconds", 60) * 1000L;
        disconnectedAt.entrySet().removeIf(entry -> {
            boolean expired = now - entry.getValue() > grace;
            if (expired) forfeited.add(entry.getKey());
            return expired;
        });
        if (!activePlayers.isEmpty() || !disconnectedAt.isEmpty()) return false;
        BuildBattles.getInstance().getLogger().warning(
                "Stopping abandoned BuildBattles match " + getGameId() + " after reconnect grace expired");
        persistOutcome(Set.of(), Map.of(), true, false);
        cleanup();
        return true;
    }

    @Override public boolean isGameEnded() { return phase == BuildPhase.FINISHED; }
    @Override public boolean addPlayerToAvailableTeam(CookiePlayer player) { return addPlayer(player); }

    @Override public void removePlayer(CookiePlayer player) { removePlayer(player, "left_game"); }

    @Override
    public synchronized void removePlayer(CookiePlayer player, String reason) {
        UUID id = player.getPlayer().getUniqueId();
        if (phase == BuildPhase.WAITING) {
            if (getPlayers().contains(player)) super.removePlayer(player, reason);
            participants.remove(id);
            activePlayers.remove(id);
            stats.clear(); // waiting stats contain no durable contribution
            scoreboard.remove(player.getPlayer());
            return;
        }
        if (getPlayers().contains(player)) super.removePlayer(player, reason);
        activePlayers.remove(id);
        if ("disconnect".equalsIgnoreCase(reason)) {
            disconnectedAt.put(id, System.currentTimeMillis());
        } else {
            disconnectedAt.remove(id);
            forfeited.add(id);
        }
        scoreboard.remove(player.getPlayer());
    }

    public void shutdown() {
        if (getState() == GameState.RUNNING && !outcomePersisted) {
            persistOutcome(Set.of(), Map.of(), true, false);
        }
        cleanup();
    }

    private void cleanup() {
        if (cleanupStarted) return;
        cleanupStarted = true;
        phase = BuildPhase.FINISHED;
        setState(GameState.FINISHED);
        cancelFloorTasks();
        for (UUID entityId : resources.trackedEntityIds()) {
            Entity entity = map.world().getEntity(entityId);
            if (entity != null) entity.remove();
        }
        resources.clear();
        redstoneLimiter.clear();
        for (CookiePlayer cookiePlayer : new ArrayList<>(activePlayers.values())) {
            Player player = cookiePlayer.getPlayer();
            try {
                if (player.isOnline()) {
                    if (!getPlayers().contains(cookiePlayer)) cookiePlayer.setState(PlayerState.LOBBY);
                    LobbyManager.teleportPlayerToLobby(cookiePlayer);
                }
            } catch (RuntimeException error) {
                World fallback = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
                if (fallback != null && player.isOnline()) {
                    cookiePlayer.resetPlayer();
                    cookiePlayer.setState(PlayerState.LOBBY);
                    player.teleport(fallback.getSpawnLocation());
                }
            } finally {
                if (getPlayers().contains(cookiePlayer)) super.removePlayer(cookiePlayer, "cleanup");
                scoreboard.remove(player);
            }
        }
        activePlayers.clear();
        if (!MapManager.unload(getGameId())) {
            BuildBattles.getInstance().getLogger().warning("Map cleanup remains pending for " + getGameId());
        }
        scoreboard.clear();
        stats.clear();
        GameManager.removeGame(this);
        BuildBattles.requestStandbyRefill();
    }

    private void cancelFloorTasks() {
        floorTasks.forEach(BukkitTask::cancel);
        floorTasks.clear();
    }

    private int buildSeconds() { return BuildBattles.getInstance().getConfig().getInt("game.build-seconds", 300); }
    private int judgingSeconds() { return BuildBattles.getInstance().getConfig().getInt("game.judging-seconds-per-plot", 12); }
    private int resultsSeconds() { return BuildBattles.getInstance().getConfig().getInt("game.results-seconds", 10); }

    private String playerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) return online.getName();
        CookiePlayer tracked = activePlayers.get(playerId);
        return tracked == null ? playerId.toString().substring(0, 8) : tracked.getPlayer().getName();
    }
}

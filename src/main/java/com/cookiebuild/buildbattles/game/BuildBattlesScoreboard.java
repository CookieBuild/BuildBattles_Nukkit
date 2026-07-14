package com.cookiebuild.buildbattles.game;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class BuildBattlesScoreboard {
    private static final String[] ENTRIES = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7"};
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private record View(Scoreboard scoreboard, Objective objective, Team[] lines) {}
    private final Map<UUID, View> views = new HashMap<>();

    void update(Player player, List<String> content) {
        View view = views.computeIfAbsent(player.getUniqueId(), ignored -> create());
        if (view == null) return;
        if (player.getScoreboard() != view.scoreboard()) player.setScoreboard(view.scoreboard());
        for (int index = 0; index < ENTRIES.length; index++) {
            if (index < content.size()) {
                view.lines()[index].prefix(LEGACY.deserialize(content.get(index)));
                view.objective().getScore(ENTRIES[index]).setScore(ENTRIES.length - index);
            } else {
                view.lines()[index].prefix(Component.empty());
                view.scoreboard().resetScores(ENTRIES[index]);
            }
        }
    }

    void remove(Player player) {
        views.remove(player.getUniqueId());
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null && player.isOnline()) player.setScoreboard(manager.getNewScoreboard());
    }

    void clear() { views.clear(); }

    private static View create() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return null;
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("buildbattles", Criteria.DUMMY,
                Component.text("BuildBattles", NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Team[] lines = new Team[ENTRIES.length];
        for (int index = 0; index < ENTRIES.length; index++) {
            Team team = board.registerNewTeam("bb_" + index);
            team.addEntry(ENTRIES[index]);
            lines[index] = team;
        }
        return new View(board, objective, lines);
    }
}

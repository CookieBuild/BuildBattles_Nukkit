package com.cookiebuild.buildbattles.listener;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.buildbattles.game.BuildBattlesGame;
import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;

public final class BuildBattlesListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "floor", "bbtheme", "bbvote", "buildbattles", "buildbattle", "bb",
            "lobby", "hub", "feedback", "party", "msg", "tell", "reply");

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getBlock().getWorld());
        if (worldGame == null) return;
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        if (game != worldGame || !game.isInsideOwnPlot(event.getPlayer(), event.getBlock().getLocation())
                || !BuildSafetyPolicy.isSafeBuildingBlock(event.getBlockPlaced().getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.build.blocked"));
            return;
        }
        game.recordPlaced(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getBlock().getWorld());
        if (worldGame == null) return;
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        if (game != worldGame || !game.isInsideOwnPlot(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        game.recordBroken(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event)) return;
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        if (game != null && game.owns(event.getPlayer().getWorld())) game.keepInsidePlot(event.getPlayer(), event.getTo());
    }

    private boolean sameBlock(PlayerMoveEvent event) {
        return event.getFrom().getWorld() == event.getTo().getWorld()
                && event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || !event.getItem().hasItemMeta()) return;
        var data = event.getItem().getItemMeta().getPersistentDataContainer();
        if (data.has(BuildBattles.getInstance().getThemeKey(), PersistentDataType.STRING)) {
            if (!isVoteUseAction(event.getAction())) return;
            event.setCancelled(true);
            String theme = data.get(BuildBattles.getInstance().getThemeKey(), PersistentDataType.STRING);
            BuildBattlesGame themeGame = BuildBattles.findGame(event.getPlayer());
            if (themeGame == null || !themeGame.voteTheme(event.getPlayer(), theme)) {
                event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.theme.invalid"));
            }
            return;
        }
        if (data.has(BuildBattles.getInstance().getVoteKey(), PersistentDataType.INTEGER)) {
            if (!isVoteUseAction(event.getAction())) return;
            event.setCancelled(true);
            Integer score = data.get(BuildBattles.getInstance().getVoteKey(), PersistentDataType.INTEGER);
            BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
            if (game != null && score != null) game.vote(event.getPlayer(), score);
            return;
        }
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getPlayer().getWorld());
        if (worldGame != null && (game != worldGame
                || !BuildSafetyPolicy.isSafeBuildingBlock(event.getItem().getType()))) {
            event.setCancelled(true);
        }
    }

    static boolean isVoteUseAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getPlayer().getWorld());
        if (worldGame == null) return;
        String command = event.getMessage().substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (game != worldGame || command.contains(":") || !ALLOWED_COMMANDS.contains(command)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.command.blocked"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getEntity().getWorld());
        if (game != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosion(EntityExplodeEvent event) {
        if (BuildBattles.findWorldGame(event.getLocation().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (BuildBattles.findWorldGame(event.getLocation().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (BuildBattles.findWorldGame(event.getEntity().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (BuildBattles.findWorldGame(event.getRightClicked().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityBlockChange(EntityChangeBlockEvent event) {
        if (BuildBattles.findWorldGame(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onFlow(BlockFromToEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getBlock().getWorld());
        if (game != null && !game.canFluidFlow(
                event.getBlock().getLocation(), event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onIgnite(BlockIgniteEvent event) {
        if (BuildBattles.findWorldGame(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBurn(BlockBurnEvent event) {
        if (BuildBattles.findWorldGame(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (BuildBattles.findWorldGame(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (BuildBattles.findWorldGame(event.getBlock().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRedstone(BlockRedstoneEvent event) {
        if (BuildBattles.findWorldGame(event.getBlock().getWorld()) != null) event.setNewCurrent(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getBlock().getWorld());
        if (worldGame == null) return;
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        var destination = event.getBlockClicked().getRelative(event.getBlockFace()).getLocation();
        if (game != worldGame || !BuildSafetyPolicy.isAllowedPlotBucket(event.getBucket())
                || !game.isInsideOwnPlot(event.getPlayer(), destination)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.build.blocked"));
            return;
        }
        game.recordPlaced(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getBlock().getWorld());
        if (worldGame == null) return;
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        if (game != worldGame || event.getBlock().getType() != Material.LAVA
                || !game.isInsideOwnPlot(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        game.recordBroken(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (BuildBattles.findGame(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (BuildBattles.findGame(event.getPlayer()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(BuildBattles.getInstance(), () -> {
            CookiePlayer cookiePlayer = PlayerManager.getPlayer(event.getPlayer());
            if (cookiePlayer == null) return;
            for (Game candidate : GameManager.getGames()) {
                if (candidate instanceof BuildBattlesGame game && game.reconnect(cookiePlayer)) return;
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        if (game == null) return;
        CookiePlayer tracked = game.getPlayers().stream()
                .filter(value -> value.getPlayer().getUniqueId().equals(event.getPlayer().getUniqueId()))
                .findFirst().orElse(PlayerManager.getPlayer(event.getPlayer()));
        if (tracked != null) game.removePlayer(tracked, "disconnect");
    }
}

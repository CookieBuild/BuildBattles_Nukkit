package com.cookiebuild.buildbattles.listener;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.buildbattles.game.BuildPhase;
import com.cookiebuild.buildbattles.game.BuildBattlesGame;
import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;
import com.cookiebuild.buildbattles.ui.BuildBattlesBedrockForms;
import com.cookiebuild.buildbattles.security.BuildResourceBudget.AddResult;
import com.cookiebuild.buildbattles.security.BuildResourceBudget.EntityCategory;
import com.cookiebuild.buildbattles.security.RedstoneActivityLimiter;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;

public final class BuildBattlesListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "floor", "bbtheme", "bbvote", "buildbattles", "buildbattle", "bb",
            "bbitems", "lobby", "hub", "feedback", "party", "msg", "tell", "reply");
    private PendingSpawn pendingSpawn;

    private record PendingSpawn(BuildBattlesGame game, UUID playerId, EntityType type) {
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getBlock().getWorld());
        if (worldGame == null) return;
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        Material material = event.getBlockPlaced().getType();
        if (game != worldGame || !game.isInsideOwnPlot(event.getPlayer(), event.getBlock().getLocation())
                || !BuildSafetyPolicy.isSafeBuildingBlock(material)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.build.blocked"));
            return;
        }
        if (BuildSafetyPolicy.isRedstoneComponent(material)
                && !game.reserveRedstone(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.limit.redstone"));
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
        if (BuildSafetyPolicy.isRedstoneComponent(event.getBlock().getType())) {
            game.releaseRedstone(event.getBlock().getLocation());
        }
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
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() != null && event.getItem().hasItemMeta()) {
            var data = event.getItem().getItemMeta().getPersistentDataContainer();
            if (data.has(BuildBattles.getInstance().getPaletteShortcutKey(), PersistentDataType.BYTE)) {
                if (!isPaletteShortcutUse(event.getAction())) return;
                event.setCancelled(true);
                if (!BuildBattlesBedrockForms.openPalette(event.getPlayer(),
                        () -> BuildBattles.giveBuildPalette(event.getPlayer()))) {
                    BuildBattles.giveBuildPalette(event.getPlayer());
                }
                return;
            }
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
        }
        BuildBattlesGame game = BuildBattles.findGame(event.getPlayer());
        BuildBattlesGame worldGame = BuildBattles.findWorldGame(event.getPlayer().getWorld());
        if (worldGame == null) return;
        if (game != worldGame) {
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                && !canInteractWithBuildBlock(game.getPhase(),
                        game.isInsideOwnPlot(event.getPlayer(), event.getClickedBlock().getLocation()))) {
            event.setCancelled(true);
            return;
        }
        if (event.getItem() == null) return;
        String passiveTypeName = BuildSafetyPolicy.passiveEntityNameForEgg(event.getItem().getType());
        EntityType passiveType = passiveTypeName == null ? null : EntityType.valueOf(passiveTypeName);
        if (passiveType != null && event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null) {
            event.setCancelled(true);
            var destination = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
            if (!game.isInsideOwnPlot(event.getPlayer(), destination)) {
                event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.build.blocked"));
                return;
            }
            pendingSpawn = new PendingSpawn(game, event.getPlayer().getUniqueId(), passiveType);
            try {
                destination.getWorld().spawnEntity(destination, passiveType, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
            } finally {
                pendingSpawn = null;
            }
            return;
        }
        if (isVoteUseAction(event.getAction())
                && !BuildSafetyPolicy.isSafeBuildItem(event.getItem().getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.build.blocked"));
        }
    }

    static boolean isVoteUseAction(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    static boolean isPaletteShortcutUse(Action action) {
        return isVoteUseAction(action);
    }

    static boolean canInteractWithBuildBlock(BuildPhase phase, boolean insideOwnPlot) {
        return phase == BuildPhase.BUILDING && insideOwnPlot;
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
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getEntity().getWorld());
        if (game == null) return;
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player && game.canEditBuildEntity(player, event.getEntity())) {
            game.releaseBuildEntity(event.getEntity());
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onExplosion(EntityExplodeEvent event) {
        if (BuildBattles.findWorldGame(event.getLocation().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getLocation().getWorld());
        if (game == null) return;
        // Paper fires EntityPlaceEvent for armor stands before adding the entity,
        // then follows with CreatureSpawnEvent. The tagged entity already passed
        // all owner, phase, bounds and quota checks in onEntityPlace.
        if (game.buildEntityCategory(event.getEntity()) == EntityCategory.ARMOR_STAND) return;
        PendingSpawn pending = pendingSpawn;
        Player player = pending == null ? null : Bukkit.getPlayer(pending.playerId());
        if (pending == null || pending.game() != game || player == null
                || pending.type() != event.getEntityType()
                || event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG
                || !BuildSafetyPolicy.isAllowedPassiveEntityName(event.getEntityType().name())
                || !game.isInsideOwnPlot(player, event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        AddResult result = game.trackBuildEntity(player, event.getEntity(), EntityCategory.LIVING);
        if (!result.accepted()) {
            event.setCancelled(true);
            player.sendMessage(BuildBattles.message(player, "bb.limit.entities"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getEntity().getWorld());
        if (game == null) return;
        Player player = event.getPlayer();
        if (player == null || !isDecoration(event.getEntity().getType())
                || !game.isInsideOwnPlot(player, event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        AddResult result = game.trackBuildEntity(player, event.getEntity(), EntityCategory.DECORATION);
        if (!result.accepted()) {
            event.setCancelled(true);
            player.sendMessage(BuildBattles.message(player, "bb.limit.entities"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getEntity().getWorld());
        if (game == null) return;
        event.setCancelled(true);
        if (event.getRemover() instanceof Player player && game.canEditBuildEntity(player, event.getEntity())) {
            game.releaseBuildEntity(event.getEntity());
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreakFromPhysics(HangingBreakEvent event) {
        if (event instanceof HangingBreakByEntityEvent) return;
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getEntity().getWorld());
        if (game == null) return;
        event.setCancelled(true);
        game.releaseBuildEntity(event.getEntity());
        event.getEntity().remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityPlace(EntityPlaceEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getEntity().getWorld());
        if (game == null) return;
        Player player = event.getPlayer();
        EntityCategory category = event.getEntityType() == EntityType.ARMOR_STAND
                ? EntityCategory.ARMOR_STAND
                : isDecoration(event.getEntityType()) ? EntityCategory.DECORATION : null;
        // Some Paper versions also surface spawn-egg placement through this
        // generic event after CreatureSpawnEvent. Only the already-tagged entity
        // from our one-shot spawn path may pass through.
        if (category == null && game.buildEntityCategory(event.getEntity()) == EntityCategory.LIVING) return;
        if (player == null || category == null || !game.isInsideOwnPlot(player, event.getEntity())) {
            event.setCancelled(true);
            return;
        }
        AddResult result = game.trackBuildEntity(player, event.getEntity(), category);
        if (!result.accepted()) {
            event.setCancelled(true);
            player.sendMessage(BuildBattles.message(player, "bb.limit.entities"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getRightClicked().getWorld());
        if (game != null && !game.canEditBuildEntity(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getRightClicked().getWorld());
        if (game == null) return;
        EntityCategory category = game.buildEntityCategory(event.getRightClicked());
        if (category == EntityCategory.LIVING
                || !game.canEditBuildEntity(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getEntity().getWorld());
        if (game != null && shouldReleaseEntity(event.getCause())) game.releaseBuildEntity(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (BuildBattles.findWorldGame(event.getEntity().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleCreate(VehicleCreateEvent event) {
        if (BuildBattles.findWorldGame(event.getVehicle().getWorld()) != null) event.setCancelled(true);
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
        BuildBattlesGame game = BuildBattles.findWorldGame(event.getBlock().getWorld());
        if (game == null) return;
        UUID owner = game.plotOwner(event.getBlock().getLocation());
        RedstoneActivityLimiter.Decision decision = game.recordRedstoneUpdate(owner, System.currentTimeMillis());
        if (decision != RedstoneActivityLimiter.Decision.ALLOWED) event.setNewCurrent(0);
        if (decision == RedstoneActivityLimiter.Decision.TRIPPED && owner != null) {
            Player player = Bukkit.getPlayer(owner);
            if (player != null) player.sendMessage(BuildBattles.message(player, "bb.redstone.throttled"));
        }
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
        if (!game.reserveLavaSource(event.getPlayer(), destination)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(BuildBattles.message(event.getPlayer(), "bb.limit.lava"));
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
        game.releaseLavaSource(event.getBlock().getLocation());
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

    private static boolean isDecoration(EntityType type) {
        return type == EntityType.ITEM_FRAME || type == EntityType.GLOW_ITEM_FRAME || type == EntityType.PAINTING;
    }

    static boolean shouldReleaseEntity(EntityRemoveEvent.Cause cause) {
        return cause != EntityRemoveEvent.Cause.UNLOAD;
    }
}

package com.cookiebuild.buildbattles.gallery;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.buildbattles.BuildBattles;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** On join: plain-text notice of likes received since the last notice (Bedrock-safe). */
public final class GalleryJoinListener implements Listener {
    /** After the welcome and login-calendar messages, so the notice is not lost in the burst. */
    private static final long NOTICE_DELAY_TICKS = 20L * 12L;

    private final JavaPlugin plugin;
    private final BuildGalleryService gallery;

    public GalleryJoinListener(JavaPlugin plugin, BuildGalleryService gallery) {
        this.plugin = plugin;
        this.gallery = gallery;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            gallery.checkNewLikes(player, likes -> {
                Player online = Bukkit.getPlayer(playerId);
                if (online == null) return;
                online.sendMessage(Component.text(BuildBattles.message(online,
                        likes == 1 ? "bb.gallery.likes.one" : "bb.gallery.likes.many", likes),
                        NamedTextColor.LIGHT_PURPLE));
                online.sendMessage(Component.text(BuildBattles.message(online, "bb.gallery.likes.where",
                        GalleryCapturePolicy.galleryUrl(gallery.host())), NamedTextColor.GRAY));
            });
        }, NOTICE_DELAY_TICKS);
    }
}

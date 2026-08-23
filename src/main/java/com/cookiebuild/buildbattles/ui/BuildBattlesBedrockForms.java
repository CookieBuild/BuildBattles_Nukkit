package com.cookiebuild.buildbattles.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.cookiedough.ui.BedrockButtonText;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockFormSupport;
import com.cookiebuild.cookiedough.ui.BedrockMenuSessionRegistry;
import com.cookiebuild.cookiedough.ui.MainThreadPlayerAction;

/** Native forms over BuildBattles' existing guarded theme, rating and palette actions. */
public final class BuildBattlesBedrockForms {
    static final String MODE_IMAGE = "modes/buildbattles";
    static final String PALETTE_IMAGE = "actions/palette";
    static final String CLOSE_IMAGE = "actions/close";
    private static final BedrockMenuSessionRegistry SESSIONS = new BedrockMenuSessionRegistry();

    private BuildBattlesBedrockForms() { }

    public static boolean openTheme(Player player, List<String> candidates, Consumer<String> voteHandler) {
        if (!BedrockFormSupport.isBedrock(player)) return false;
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return false;
        List<String> options = List.copyOf(candidates);
        String scope = "buildbattles:theme:" + options.hashCode();
        UUID nonce = SESSIONS.issue(player.getUniqueId(), scope);
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + BuildBattles.message(player, "bb.form.theme.title"))
                .content(BuildBattles.message(player, "bb.form.theme.content"));
        options.forEach(theme -> BedrockFormImages.button(form,
                BedrockButtonText.format(BuildBattles.themeName(player, theme)), MODE_IMAGE));
        BedrockFormImages.button(form, BedrockButtonText.format(
                BuildBattles.message(player, "bb.form.close")), CLOSE_IMAGE);
        form.validResultHandler(response -> {
            int index = response.clickedButtonId();
            MainThreadPlayerAction.dispatch(plugin, player, () -> {
                if (SESSIONS.consume(player.getUniqueId(), nonce, scope)
                        && index >= 0 && index < options.size()) voteHandler.accept(options.get(index));
            });
        });
        form.closedOrInvalidResultHandler(() -> SESSIONS.invalidate(player.getUniqueId(), nonce, scope));
        boolean sent = BedrockFormSupport.send(player, form.build());
        if (!sent) SESSIONS.invalidate(player.getUniqueId(), nonce, scope);
        return sent;
    }

    public static boolean openRating(Player player, IntConsumer voteHandler) {
        if (!BedrockFormSupport.isBedrock(player)) return false;
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return false;
        String scope = "buildbattles:rating";
        UUID nonce = SESSIONS.issue(player.getUniqueId(), scope);
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + BuildBattles.message(player, "bb.form.rating.title"))
                .content(BuildBattles.message(player, "bb.form.rating.content"));
        for (int score = 1; score <= 5; score++) {
            BedrockFormImages.button(form, BedrockButtonText.format(score + "/5",
                    BuildBattles.message(player, "bb.vote." + score)), MODE_IMAGE);
        }
        BedrockFormImages.button(form, BedrockButtonText.format(
                BuildBattles.message(player, "bb.form.close")), CLOSE_IMAGE);
        form.validResultHandler(response -> {
            int index = response.clickedButtonId();
            MainThreadPlayerAction.dispatch(plugin, player, () -> {
                if (SESSIONS.consume(player.getUniqueId(), nonce, scope)
                        && index >= 0 && index < 5) voteHandler.accept(index + 1);
            });
        });
        form.closedOrInvalidResultHandler(() -> SESSIONS.invalidate(player.getUniqueId(), nonce, scope));
        boolean sent = BedrockFormSupport.send(player, form.build());
        if (!sent) SESSIONS.invalidate(player.getUniqueId(), nonce, scope);
        return sent;
    }

    public static boolean openPalette(Player player, Runnable receiveHandler) {
        if (!BedrockFormSupport.isBedrock(player)) return false;
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return false;
        String scope = "buildbattles:palette";
        UUID nonce = SESSIONS.issue(player.getUniqueId(), scope);
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + BuildBattles.message(player, "bb.palette.shortcut.name"))
                .content(BuildBattles.message(player, "bb.form.palette.content"));
        BedrockFormImages.button(form, BedrockButtonText.format(
                        BuildBattles.message(player, "bb.form.palette.receive")),
                PALETTE_IMAGE);
        BedrockFormImages.button(form, BedrockButtonText.format(
                BuildBattles.message(player, "bb.form.close")), CLOSE_IMAGE);
        form.validResultHandler(response -> {
            int index = response.clickedButtonId();
            MainThreadPlayerAction.dispatch(plugin, player, () -> {
                if (SESSIONS.consume(player.getUniqueId(), nonce, scope) && index == 0) receiveHandler.run();
            });
        });
        form.closedOrInvalidResultHandler(() -> SESSIONS.invalidate(player.getUniqueId(), nonce, scope));
        boolean sent = BedrockFormSupport.send(player, form.build());
        if (!sent) SESSIONS.invalidate(player.getUniqueId(), nonce, scope);
        return sent;
    }

    public static void invalidate(Player player) {
        SESSIONS.invalidate(player.getUniqueId());
    }
}

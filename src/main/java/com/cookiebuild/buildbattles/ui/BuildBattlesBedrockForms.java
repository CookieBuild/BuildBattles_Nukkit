package com.cookiebuild.buildbattles.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;

import com.cookiebuild.buildbattles.BuildBattles;
import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockFormSupport;
import com.cookiebuild.cookiedough.ui.MainThreadPlayerAction;

/** Native forms over BuildBattles' existing guarded theme, rating and palette actions. */
public final class BuildBattlesBedrockForms {
    private BuildBattlesBedrockForms() { }

    public static boolean openTheme(Player player, List<String> candidates, Consumer<String> voteHandler) {
        if (!BedrockFormSupport.isBedrock(player)) return false;
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return false;
        List<String> options = List.copyOf(candidates);
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + BuildBattles.message(player, "bb.form.theme.title"))
                .content("§7" + BuildBattles.message(player, "bb.form.theme.content"));
        options.forEach(theme -> BedrockFormImages.button(form, "§f§l" + theme, "modes/buildbattles"));
        BedrockFormImages.button(form, "§c§l" + BuildBattles.message(player, "bb.form.close"), "actions/close");
        form.validResultHandler(response -> {
            int index = response.getClickedButtonId();
            MainThreadPlayerAction.dispatch(plugin, player, () -> {
                if (index >= 0 && index < options.size()) voteHandler.accept(options.get(index));
            });
        });
        return BedrockFormSupport.send(player, form.build());
    }

    public static boolean openRating(Player player, IntConsumer voteHandler) {
        if (!BedrockFormSupport.isBedrock(player)) return false;
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return false;
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + BuildBattles.message(player, "bb.form.rating.title"))
                .content("§7" + BuildBattles.message(player, "bb.form.rating.content"));
        for (int score = 1; score <= 5; score++) {
            BedrockFormImages.button(form, "§f§l" + score + "/5 · "
                    + BuildBattles.message(player, "bb.vote." + score), "modes/buildbattles");
        }
        BedrockFormImages.button(form, "§c§l" + BuildBattles.message(player, "bb.form.close"), "actions/close");
        form.validResultHandler(response -> {
            int index = response.getClickedButtonId();
            MainThreadPlayerAction.dispatch(plugin, player, () -> {
                if (index >= 0 && index < 5) voteHandler.accept(index + 1);
            });
        });
        return BedrockFormSupport.send(player, form.build());
    }

    public static boolean openPalette(Player player, Runnable receiveHandler) {
        if (!BedrockFormSupport.isBedrock(player)) return false;
        BuildBattles plugin = BuildBattles.getInstance();
        if (plugin == null || !plugin.isEnabled()) return false;
        SimpleForm.Builder form = SimpleForm.builder()
                .title("§l§6" + BuildBattles.message(player, "bb.palette.shortcut.name"))
                .content("§7" + BuildBattles.message(player, "bb.form.palette.content"));
        BedrockFormImages.button(form, "§a§l" + BuildBattles.message(player, "bb.form.palette.receive"),
                "actions/palette");
        BedrockFormImages.button(form, "§c§l" + BuildBattles.message(player, "bb.form.close"), "actions/close");
        form.validResultHandler(response -> {
            int index = response.getClickedButtonId();
            MainThreadPlayerAction.dispatch(plugin, player, () -> {
                if (index == 0) receiveHandler.run();
            });
        });
        return BedrockFormSupport.send(player, form.build());
    }
}

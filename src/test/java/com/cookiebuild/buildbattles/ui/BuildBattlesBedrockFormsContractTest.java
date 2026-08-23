package com.cookiebuild.buildbattles.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.cookiebuild.cookiedough.ui.BedrockFormImages;
import com.cookiebuild.cookiedough.ui.BedrockMenuSessionRegistry;

class BuildBattlesBedrockFormsContractTest {
    @Test
    void themeRatingAndPaletteRemainSeparateServerGuardedActions() throws Exception {
        assertEquals(boolean.class, BuildBattlesBedrockForms.class.getDeclaredMethod(
                "openTheme", org.bukkit.entity.Player.class, java.util.List.class,
                java.util.function.Consumer.class).getReturnType());
        assertEquals(boolean.class, BuildBattlesBedrockForms.class.getDeclaredMethod(
                "openRating", org.bukkit.entity.Player.class,
                java.util.function.IntConsumer.class).getReturnType());
        assertEquals(boolean.class, BuildBattlesBedrockForms.class.getDeclaredMethod(
                "openPalette", org.bukkit.entity.Player.class, Runnable.class).getReturnType());
        assertEquals(true, BedrockFormImages.isKnown(BuildBattlesBedrockForms.MODE_IMAGE));
        assertEquals(true, BedrockFormImages.isKnown(BuildBattlesBedrockForms.PALETTE_IMAGE));
        assertEquals(true, BedrockFormImages.isKnown(BuildBattlesBedrockForms.CLOSE_IMAGE));
        assertEquals(BedrockMenuSessionRegistry.class,
                BuildBattlesBedrockForms.class.getDeclaredField("SESSIONS").getType());
    }

    @Test
    void bedrockThemeLabelsAreLocalizedWithoutChangingTheSubmittedTheme() throws Exception {
        Path path = Path.of("src/main/java/com/cookiebuild/buildbattles/ui/BuildBattlesBedrockForms.java");
        if (!Files.exists(path)) path = Path.of("BuildBattles").resolve(path);
        String source = Files.readString(path);
        assertTrue(source.contains("BuildBattles.themeName(player, theme)"));
        assertTrue(source.contains("voteHandler.accept(options.get(index))"));
    }
}

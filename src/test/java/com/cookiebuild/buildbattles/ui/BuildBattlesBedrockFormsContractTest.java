package com.cookiebuild.buildbattles.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

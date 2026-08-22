package com.cookiebuild.buildbattles.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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
    }
}

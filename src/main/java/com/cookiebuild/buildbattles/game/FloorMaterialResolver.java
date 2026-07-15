package com.cookiebuild.buildbattles.game;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.bukkit.Material;

import com.cookiebuild.buildbattles.security.BuildSafetyPolicy;

/** Resolves the optional /floor material argument and supplies safe completions. */
public final class FloorMaterialResolver {
    private FloorMaterialResolver() {
    }

    public static Material resolve(String[] arguments, Material heldMaterial) {
        if (arguments == null || arguments.length == 0) {
            return heldMaterial;
        }
        String requested = String.join("_", arguments).trim();
        if (requested.isEmpty()) {
            return null;
        }
        String normalized = requested.toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.startsWith("MINECRAFT:")) {
            normalized = normalized.substring("MINECRAFT:".length());
        }
        try {
            return Material.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static List<String> suggestions(String prefix) {
        return suggestions(prefix, material -> material.isBlock() && material.isItem());
    }

    static List<String> suggestions(String prefix, Predicate<Material> placeable) {
        String normalized = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return Arrays.stream(Material.values())
                .filter(placeable)
                .filter(BuildSafetyPolicy::isSafeBuildingBlock)
                .map(material -> material.name().toLowerCase(Locale.ROOT))
                .filter(value -> value.startsWith(normalized))
                .sorted()
                .toList();
    }
}

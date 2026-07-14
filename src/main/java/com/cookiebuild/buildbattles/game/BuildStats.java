package com.cookiebuild.buildbattles.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BuildStats {
    public record Snapshot(int blocksPlaced, int blocksBroken, int floorChanges) {
    }

    private static final class Mutable {
        private int placed;
        private int broken;
        private int floors;
    }

    private final Map<UUID, Mutable> values = new HashMap<>();

    public void register(UUID playerId) {
        values.computeIfAbsent(playerId, ignored -> new Mutable());
    }

    public void placed(UUID playerId) {
        register(playerId);
        values.get(playerId).placed++;
    }

    public void broken(UUID playerId) {
        register(playerId);
        values.get(playerId).broken++;
    }

    public void floorChanged(UUID playerId) {
        register(playerId);
        values.get(playerId).floors++;
    }

    public Snapshot snapshot(UUID playerId) {
        Mutable value = values.getOrDefault(playerId, new Mutable());
        return new Snapshot(value.placed, value.broken, value.floors);
    }

    public void clear() {
        values.clear();
    }
}

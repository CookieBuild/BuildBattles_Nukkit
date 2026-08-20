package com.cookiebuild.buildbattles.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AsyncChunkPreloadArchitectureTest {
    @Test
    void validationWaitsForCancellablePaperChunkPreload() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/cookiebuild/buildbattles/map/MapManager.java"));
        int async = source.indexOf("WorldLoad<GameMap> loadPreparedAsync");
        int callback = source.indexOf("aggregate.whenComplete", async);
        int validation = source.indexOf("validatePlots", callback);
        assertTrue(async >= 0 && callback > async && validation > callback);
        assertTrue(source.substring(async, callback).contains("getChunkAtAsync(chunk.x(), chunk.z(), true)"));
        assertFalse(source.substring(async, callback).contains("getBlockAt("));
        assertFalse(source.contains("getChunksAtAsync("));

        int cancel = source.indexOf("boolean cancelAsyncLoad");
        int cancelAggregate = source.indexOf("aggregate.cancel(false)", cancel);
        int cancelChunk = source.indexOf("chunkLoad.cancel(false)", cancel);
        int unload = source.indexOf("Bukkit.unloadWorld", cancel);
        int complete = source.indexOf("result.completeExceptionally", cancel);
        assertTrue(cancelAggregate > cancel && cancelChunk > cancelAggregate
                && unload > cancelChunk && complete > unload);
        assertTrue(source.contains("PreparedFilesInUseException"));
        assertTrue(source.contains("create_world_ms=") && source.contains("chunk_preload_ms=")
                && source.contains("validation_ms="));
    }
}

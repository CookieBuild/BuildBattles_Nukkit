package com.cookiebuild.buildbattles.gallery;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;

class BuildPayloadTest {
    private static final String PLANKS = "minecraft:oak_planks";
    private static final String STAIRS = "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]";
    private static final String SIGN = "minecraft:oak_sign[rotation=4,waterlogged=false]";

    /** Independent decoder of the contract payload: gunzip, parse, expand the runs. */
    private record Decoded(int version, int[] size, List<String> palette, int[] blocks) {
    }

    private static Decoded decode(byte[] gzip) throws IOException {
        String json;
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(gzip))) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher version = Pattern.compile("^\\{\"v\":(\\d+),").matcher(json);
        assertTrue(version.find(), json);
        Matcher size = Pattern.compile("\"size\":\\[(\\d+),(\\d+),(\\d+)]").matcher(json);
        assertTrue(size.find());
        int[] dimensions = { Integer.parseInt(size.group(1)), Integer.parseInt(size.group(2)),
                Integer.parseInt(size.group(3)) };
        String paletteJson = json.substring(json.indexOf("\"palette\":[") + 11, json.indexOf("],\"blocks\":"));
        List<String> palette = new ArrayList<>();
        Matcher entry = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(paletteJson);
        while (entry.find()) palette.add(entry.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
        String runsJson = json.substring(json.indexOf("\"blocks\":[") + 10, json.lastIndexOf("]}"));
        String[] runs = runsJson.split(",");
        assertEquals(0, runs.length % 2, "runs are index/length pairs");
        int total = dimensions[0] * dimensions[1] * dimensions[2];
        int[] blocks = new int[total];
        int cursor = 0;
        for (int index = 0; index < runs.length; index += 2) {
            int value = Integer.parseInt(runs[index]);
            int length = Integer.parseInt(runs[index + 1]);
            assertTrue(length > 0);
            for (int step = 0; step < length; step++) blocks[cursor++] = value;
        }
        assertEquals(total, cursor, "runs cover the whole box");
        return new Decoded(Integer.parseInt(version.group(1)), dimensions, palette, blocks);
    }

    @Test
    void roundTripsThroughGzipJsonWithXThenZThenYOrder() throws IOException {
        BuildPayload payload = new BuildPayload.Builder(3, 2, 4)
                .set(0, 0, 0, PLANKS)
                .set(1, 0, 0, PLANKS)
                .set(2, 1, 3, STAIRS)
                .set(0, 0, 1, "minecraft:air")
                .set(1, 1, 2, SIGN)
                .build();

        Decoded decoded = decode(payload.gzip());

        assertEquals(1, decoded.version());
        assertArrayEquals(new int[] { 3, 2, 4 }, decoded.size());
        assertEquals(List.of("minecraft:air", PLANKS, STAIRS, SIGN), decoded.palette());
        assertArrayEquals(payload.blocks(), decoded.blocks());
        // index = x + z*sx + y*sx*sz
        assertEquals(1, decoded.blocks()[0]);
        assertEquals(1, decoded.blocks()[1]);
        assertEquals(2, decoded.blocks()[2 + 3 * 3 + 3 * 4]);
        assertEquals(3, decoded.blocks()[1 + 2 * 3 + 3 * 4]);
        assertEquals(4, payload.nonAirCount());
    }

    @Test
    void emptyBoxIsOneAirRunAndLegacyPlotFitsTheContractCap() throws IOException {
        BuildPayload empty = new BuildPayload.Builder(27, 23, 27).build();
        assertArrayEquals(new int[] { 0, 27 * 23 * 27 }, empty.runLengths());
        assertEquals("{\"v\":1,\"size\":[27,23,27],\"palette\":[\"minecraft:air\"],\"blocks\":[0,16767]}",
                empty.toJson());

        Random random = new Random(42);
        List<String> states = List.of(PLANKS, STAIRS, SIGN, "minecraft:stone", "minecraft:glass");
        BuildPayload.Builder noisy = new BuildPayload.Builder(27, 23, 27);
        for (int y = 0; y < 23; y++) {
            for (int z = 0; z < 27; z++) {
                for (int x = 0; x < 27; x++) {
                    int pick = random.nextInt(states.size() + 1);
                    noisy.set(x, y, z, pick == states.size() ? null : states.get(pick));
                }
            }
        }
        BuildPayload worstCase = noisy.build();
        byte[] gzip = worstCase.gzip();
        assertTrue(gzip.length <= BuildPayload.MAX_COMPRESSED_BYTES, "bytes=" + gzip.length);
        assertArrayEquals(worstCase.blocks(), decode(gzip).blocks());
    }

    @Test
    void escapesJsonAndRejectsInvalidPayloads() throws IOException {
        BuildPayload payload = new BuildPayload.Builder(1, 1, 2).set(0, 0, 1, "odd\"state\\").build();
        assertEquals(List.of("minecraft:air", "odd\"state\\"), decode(payload.gzip()).palette());
        assertThrows(IllegalArgumentException.class,
                () -> new BuildPayload(1, 1, 1, List.of(PLANKS), new int[] { 0 }));
        assertThrows(IllegalArgumentException.class,
                () -> new BuildPayload(1, 1, 2, List.of(BuildPayload.AIR), new int[] { 0 }));
        assertThrows(IllegalArgumentException.class,
                () -> new BuildPayload(1, 1, 1, List.of(BuildPayload.AIR), new int[] { 1 }));
        assertThrows(IndexOutOfBoundsException.class, () -> new BuildPayload.Builder(2, 2, 2).set(2, 0, 0, PLANKS));
    }
}

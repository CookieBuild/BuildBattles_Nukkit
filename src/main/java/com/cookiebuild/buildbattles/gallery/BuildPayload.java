package com.cookiebuild.buildbattles.gallery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Gallery block payload (contract bb-gallery-20261003, section 1):
 * {@code {"v":1,"size":[x,y,z],"palette":[...],"blocks":[index,run,...]}},
 * gzip-compressed. Palette entries are {@code BlockData#getAsString()} and
 * index 0 is always {@code minecraft:air}. Blocks are ordered x-fastest, then
 * z, then y ({@code index = x + z*sx + y*sx*sz}) and run-length encoded as
 * alternating palette index / run length pairs.
 */
public record BuildPayload(int sizeX, int sizeY, int sizeZ, List<String> palette, int[] blocks) {
    public static final int VERSION = 1;
    public static final String AIR = "minecraft:air";
    /** Contract cap for the stored {@code data} column. */
    public static final int MAX_COMPRESSED_BYTES = 512 * 1024;

    public BuildPayload {
        if (sizeX < 1 || sizeY < 1 || sizeZ < 1) throw new IllegalArgumentException("Invalid size");
        palette = List.copyOf(palette);
        if (palette.isEmpty() || !AIR.equals(palette.getFirst())) {
            throw new IllegalArgumentException("palette[0] must be " + AIR);
        }
        if (blocks == null || blocks.length != sizeX * sizeY * sizeZ) {
            throw new IllegalArgumentException("Block count does not match the size");
        }
        for (int value : blocks) {
            if (value < 0 || value >= palette.size()) throw new IllegalArgumentException("Unknown palette index");
        }
        blocks = blocks.clone();
    }

    @Override
    public int[] blocks() {
        return blocks.clone();
    }

    /** Linear position of a block inside the box. */
    public static int index(int x, int y, int z, int sizeX, int sizeZ) {
        return x + z * sizeX + y * sizeX * sizeZ;
    }

    public int nonAirCount() {
        int count = 0;
        for (int value : blocks) if (value != 0) count++;
        return count;
    }

    /** Alternating {@code [paletteIndex, runLength, ...]} over the ordered blocks. */
    public int[] runLengths() {
        List<Integer> runs = new ArrayList<>();
        int current = blocks[0];
        int length = 0;
        for (int value : blocks) {
            if (value == current) {
                length++;
                continue;
            }
            runs.add(current);
            runs.add(length);
            current = value;
            length = 1;
        }
        runs.add(current);
        runs.add(length);
        return runs.stream().mapToInt(Integer::intValue).toArray();
    }

    public String toJson() {
        StringBuilder json = new StringBuilder(64 + palette.size() * 32 + blocks.length / 2);
        json.append("{\"v\":").append(VERSION)
                .append(",\"size\":[").append(sizeX).append(',').append(sizeY).append(',').append(sizeZ)
                .append("],\"palette\":[");
        for (int index = 0; index < palette.size(); index++) {
            if (index > 0) json.append(',');
            appendString(json, palette.get(index));
        }
        json.append("],\"blocks\":[");
        int[] runs = runLengths();
        for (int index = 0; index < runs.length; index++) {
            if (index > 0) json.append(',');
            json.append(runs[index]);
        }
        return json.append("]}").toString();
    }

    public byte[] gzip() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write(toJson().getBytes(StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return bytes.toByteArray();
    }

    private static void appendString(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) json.append(String.format("\\u%04x", (int) character));
                    else json.append(character);
                }
            }
        }
        json.append('"');
    }

    /** Collects one capture pass; {@code null} or an air state maps to palette index 0. */
    public static final class Builder {
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int[] blocks;
        private final List<String> palette = new ArrayList<>(List.of(AIR));
        private final Map<String, Integer> paletteIndex = new HashMap<>(Map.of(AIR, 0));

        public Builder(int sizeX, int sizeY, int sizeZ) {
            if (sizeX < 1 || sizeY < 1 || sizeZ < 1) throw new IllegalArgumentException("Invalid size");
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.blocks = new int[sizeX * sizeY * sizeZ];
        }

        /** Coordinates are relative to the box minimum corner. */
        public Builder set(int x, int y, int z, String blockState) {
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
                throw new IndexOutOfBoundsException("Outside the capture box");
            }
            int value = blockState == null ? 0
                    : paletteIndex.computeIfAbsent(blockState, state -> {
                        palette.add(state);
                        return palette.size() - 1;
                    });
            blocks[index(x, y, z, sizeX, sizeZ)] = value;
            return this;
        }

        public BuildPayload build() {
            return new BuildPayload(sizeX, sizeY, sizeZ, palette, blocks);
        }
    }
}

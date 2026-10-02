package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import net.minecraft.nbt.CompoundTag;

/**
 * The schema of the saved form of the crater, volcano and geode pieces. Vanilla saves structure starts in chunk data
 * and finishes them in chunks generated after a restart; a piece whose saved schema is not {@link #CURRENT} is
 * refused, and vanilla then drops that start with a logged error instead of building it from unknown fields.
 */
public final class PieceSchema {
    public static final String KEY = "schema";
    public static final int CURRENT = 1;

    private PieceSchema() {
    }

    static void write(CompoundTag tag) {
        tag.putInt(KEY, CURRENT);
    }

    static void require(CompoundTag tag, String piece) {
        int schema = tag.getInt(KEY);
        if (schema != CURRENT) {
            throw new IllegalArgumentException("unsupported " + piece + " piece schema " + schema);
        }
    }
}

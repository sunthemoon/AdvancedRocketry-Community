package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.function.Function;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.function.Executable;

/** Exact-size, depth and node fixtures for the loader codec boundaries. */
final class LoaderTestTags {
    private LoaderTestTags() { }

    /** Native named size and node count, measured with the production walk. */
    static BoundedNbt.Size size(Tag tag) {
        return BoundedNbt.measure(tag, Integer.MAX_VALUE, 1_024, 1 << 20);
    }

    /** A copy of {@code base} with a byte-array field {@code key} making its named size exactly {@code bytes}. */
    static CompoundTag padTo(CompoundTag base, String key, int bytes) {
        CompoundTag padded = base.copy();
        long length = bytes - size(base).bytes() - 3 - key.length() - 4;
        assertTrue(length >= 0, "base already exceeds " + bytes);
        padded.putByteArray(key, new byte[(int) length]);
        assertEquals(bytes, size(padded).bytes());
        return padded;
    }

    /** A copy of {@code base} with a list {@code key} of one-byte elements making it exactly {@code nodes} nodes. */
    static CompoundTag nodesTo(CompoundTag base, String key, int nodes) {
        CompoundTag grown = base.copy();
        int elements = nodes - size(base).nodes() - 1;
        assertTrue(elements >= 1, "base already holds " + nodes + " nodes");
        ListTag list = new ListTag();
        for (int index = 0; index < elements; index++) { list.add(ByteTag.valueOf((byte) index)); }
        grown.put(key, list);
        assertEquals(nodes, size(grown).nodes());
        return grown;
    }

    /** A chain of {@code levels} nested compounds; a tag holding it at depth d reaches depth d + levels - 1. */
    static CompoundTag chain(int levels) {
        CompoundTag top = new CompoundTag();
        CompoundTag cursor = top;
        for (int level = 1; level < levels; level++) {
            CompoundTag child = new CompoundTag();
            cursor.put("c", child);
            cursor = child;
        }
        return top;
    }

    static LoaderDataException.Reason refusal(Executable decode) {
        return assertThrows(LoaderDataException.class, decode).reason();
    }

    static void invalid(Executable decode) {
        assertEquals(LoaderDataException.Reason.INVALID, refusal(decode));
    }

    /** Future, older, missing and mistyped schema versions of an otherwise valid encoding. */
    static void schemaVersions(CompoundTag valid, Function<Tag, ?> decode) {
        for (int future : new int[] {2, 3, Integer.MAX_VALUE}) {
            CompoundTag tag = valid.copy();
            tag.putInt(LoaderNbt.SCHEMA, future);
            assertEquals(LoaderDataException.Reason.FUTURE, refusal(() -> decode.apply(tag)), "future " + future);
            // A newer jar may allow other bounds, so a future root is FUTURE whatever its size.
            CompoundTag huge = tag.copy();
            huge.putByteArray("future_payload", new byte[64 * 1_024]);
            assertEquals(LoaderDataException.Reason.FUTURE, refusal(() -> decode.apply(huge)), "oversized future " + future);
        }
        for (int unknown : new int[] {0, -1, Integer.MIN_VALUE}) {
            CompoundTag tag = valid.copy();
            tag.putInt(LoaderNbt.SCHEMA, unknown);
            invalid(() -> decode.apply(tag));
        }
        CompoundTag missing = valid.copy();
        missing.remove(LoaderNbt.SCHEMA);
        invalid(() -> decode.apply(missing));
        CompoundTag mistyped = valid.copy();
        mistyped.putLong(LoaderNbt.SCHEMA, 1);
        invalid(() -> decode.apply(mistyped));
        CompoundTag futureLong = valid.copy();
        futureLong.putLong(LoaderNbt.SCHEMA, 2);
        invalid(() -> decode.apply(futureLong));
        CompoundTag unknownField = valid.copy();
        unknownField.putInt("unknown", 1);
        invalid(() -> decode.apply(unknownField));
        invalid(() -> decode.apply(net.minecraft.nbt.IntTag.valueOf(1)));
        invalid(() -> decode.apply(null));
    }

    /** The byte, depth and node gates of {@code bounded} sit exactly at the stated limits. */
    static void bounds(CompoundTag valid, Function<Tag, Boolean> bounded, int bytes, int depth, int nodes) {
        assertTrue(bounded.apply(valid));
        assertTrue(bounded.apply(padTo(valid, "pad", bytes)));
        assertFalse(bounded.apply(padTo(valid, "pad", bytes + 1)));
        CompoundTag deep = valid.copy();
        deep.put("deep", chain(depth - 1));
        assertTrue(bounded.apply(deep));
        CompoundTag deeper = valid.copy();
        deeper.put("deep", chain(depth));
        assertFalse(bounded.apply(deeper));
        assertTrue(bounded.apply(nodesTo(valid, "n", nodes)));
        assertFalse(bounded.apply(nodesTo(valid, "n", nodes + 1)));
    }
}

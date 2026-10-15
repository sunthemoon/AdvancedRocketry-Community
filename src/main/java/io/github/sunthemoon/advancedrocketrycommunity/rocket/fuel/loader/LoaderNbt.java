package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Exact native field types and bounded preflight shared by the loader codecs; nothing is truncated or substituted. */
final class LoaderNbt {
    static final String SCHEMA = "schema_version";
    static final int MAX_ID_CHARS = 128;
    /** Vanilla's horizontal world limit, [-30,000,000, 30,000,000), and the Y range that {@link BlockPos#asLong()} packs. */
    static final int MAX_HORIZONTAL = 30_000_000;
    static final int MIN_Y = -2_048;
    static final int MAX_Y = 2_047;

    private LoaderNbt() { }

    static LoaderDataException invalid(String reason) {
        return new LoaderDataException(LoaderDataException.Reason.INVALID, reason);
    }

    static void require(boolean valid, String reason) {
        if (!valid) { throw invalid(reason); }
    }

    static boolean bounded(Tag tag, int bytes, int depth, int nodes) {
        return BoundedNbt.fits(tag, bytes, depth, nodes);
    }

    /**
     * Schema first: a newer version is FUTURE whatever its size, because a newer jar may allow other bounds. Then
     * the envelope, before any recursive read, and then version 1 exactly; an older, missing or mistyped one is INVALID.
     */
    static CompoundTag schemaOne(Tag raw, int bytes, int depth, int nodes, String what) {
        require(raw instanceof CompoundTag, what + " is not a compound");
        CompoundTag tag = (CompoundTag) raw;
        if (tag.contains(SCHEMA, Tag.TAG_INT) && tag.getInt(SCHEMA) > 1) {
            throw new LoaderDataException(LoaderDataException.Reason.FUTURE, what + " schema is newer than 1");
        }
        require(bounded(tag, bytes, depth, nodes), what + " exceeds its envelope");
        require(tag.contains(SCHEMA, Tag.TAG_INT) && tag.getInt(SCHEMA) == 1, "Unsupported " + what + " schema");
        return tag;
    }

    /** Runs a decoder; any failure other than a loader refusal (a cast, a parse, a model check) is INVALID. */
    static <T> T decoding(String what, Supplier<T> decoder) {
        try {
            return decoder.get();
        } catch (LoaderDataException refused) {
            throw refused;
        } catch (RuntimeException malformed) {
            throw invalid("Invalid " + what + ": " + malformed.getMessage());
        }
    }

    static void fields(CompoundTag tag, String... allowed) {
        require(Set.of(allowed).containsAll(tag.getAllKeys()), "Unknown loader field");
    }

    private static void type(CompoundTag tag, String key, int type) {
        require(tag.contains(key, type), "Missing or invalid loader field: " + key);
    }

    static int integer(CompoundTag tag, String key) { type(tag, key, Tag.TAG_INT); return tag.getInt(key); }

    static long number(CompoundTag tag, String key) { type(tag, key, Tag.TAG_LONG); return tag.getLong(key); }

    static String string(CompoundTag tag, String key) { type(tag, key, Tag.TAG_STRING); return tag.getString(key); }

    static CompoundTag compound(CompoundTag tag, String key) { type(tag, key, Tag.TAG_COMPOUND); return tag.getCompound(key); }

    static UUID uuid(CompoundTag tag, String key) {
        type(tag, key, Tag.TAG_INT_ARRAY);
        require(tag.hasUUID(key), "Invalid loader UUID: " + key);
        return tag.getUUID(key);
    }

    static UUID optionalUuid(CompoundTag tag, String key) { return tag.contains(key) ? uuid(tag, key) : null; }

    /** A canonical namespaced ID of at most 128 printable ASCII characters, exactly as written. */
    static ResourceLocation canonical(String value) {
        require(value != null && value.length() <= MAX_ID_CHARS && value.chars().allMatch(c -> c > 32 && c < 127),
                "Invalid canonical ID");
        ResourceLocation id = ResourceLocation.tryParse(value);
        require(id != null && id.toString().equals(value), "Noncanonical ID");
        return id;
    }

    static ResourceLocation id(CompoundTag tag, String key) { return canonical(string(tag, key)); }

    static BlockPos position(CompoundTag tag) {
        return checked(new BlockPos(integer(tag, "x"), integer(tag, "y"), integer(tag, "z")));
    }

    static void putPosition(CompoundTag tag, BlockPos pos) {
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
    }

    static BlockPos checked(BlockPos pos) {
        require(pos != null && pos.getX() >= -MAX_HORIZONTAL && pos.getX() < MAX_HORIZONTAL
                && pos.getZ() >= -MAX_HORIZONTAL && pos.getZ() < MAX_HORIZONTAL
                && pos.getY() >= MIN_Y && pos.getY() <= MAX_Y, "Position outside the world limits");
        return pos.immutable();
    }
}

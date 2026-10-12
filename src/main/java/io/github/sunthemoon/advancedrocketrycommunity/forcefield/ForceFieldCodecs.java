package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/** Native preflight always precedes copies, equality, identity decoding and allocation. */
public final class ForceFieldCodecs {
    public static final String PROJECTOR_ROOT = "arce_force_field_projector";
    public static final String FIELD_ROOT = "arce_force_field";
    public static final int PROJECTOR_BYTES = 4096;
    public static final int FIELD_BYTES = 1024;
    static final Set<String> IDENTITY_KEYS = Set.of("schema", "id", "owner", "generation", "level", "pos", "direction");

    public static boolean projectorBounded(Tag raw) { return BoundedNbt.fits(raw, PROJECTOR_BYTES, 8, 128); }
    public static boolean fieldBounded(Tag raw) { return BoundedNbt.fits(raw, FIELD_BYTES, 8, 64); }

    public static CompoundTag projector(ProjectorState state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putUUID("id", state.id());
        state.owner().ifPresent(owner -> tag.putUUID("owner", owner));
        tag.putLong("generation", state.generation());
        tag.putString("direction", state.direction().getName());
        tag.putInt("claims", state.claims());
        return tag;
    }

    public static ProjectorState projector(Tag raw) {
        if (!projectorBounded(raw) || !(raw instanceof CompoundTag tag)) { throw invalid(); }
        Set<String> keys = tag.contains("owner") ? Set.of("schema", "id", "owner", "generation", "direction", "claims")
                : Set.of("schema", "id", "generation", "direction", "claims");
        if (!tag.getAllKeys().equals(keys) || integer(tag, "schema") != 1) { throw invalid(); }
        return new ProjectorState(uuid(tag, "id"), tag.contains("owner") ? Optional.of(uuid(tag, "owner")) : Optional.empty(),
                longValue(tag, "generation"), direction(tag), integer(tag, "claims"));
    }

    public static CompoundTag field(FieldIdentity identity) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putUUID("id", identity.projector());
        tag.putUUID("owner", identity.owner());
        tag.putLong("generation", identity.generation());
        tag.putString("level", identity.level().location().toString());
        tag.putLong("pos", identity.source().asLong());
        tag.putString("direction", identity.direction().getName());
        return tag;
    }

    public static FieldIdentity field(Tag raw) {
        if (!fieldBounded(raw) || !(raw instanceof CompoundTag tag) || !tag.getAllKeys().equals(IDENTITY_KEYS)) {
            throw invalid();
        }
        return identity(tag);
    }

    static FieldIdentity identity(CompoundTag tag) {
        if (integer(tag, "schema") != 1 || !tag.contains("level", Tag.TAG_STRING)) { throw invalid(); }
        String name = tag.getString("level");
        ResourceLocation level = name.length() <= 255 ? ResourceLocation.tryParse(name) : null;
        if (level == null || !level.toString().equals(name)) { throw invalid(); }
        return new FieldIdentity(uuid(tag, "id"), uuid(tag, "owner"), longValue(tag, "generation"),
                ResourceKey.create(Registries.DIMENSION, level), BlockPos.of(longValue(tag, "pos")), direction(tag));
    }

    static int integer(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT)) { throw invalid(); }
        return tag.getInt(key);
    }
    private static long longValue(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_LONG)) { throw invalid(); }
        return tag.getLong(key);
    }
    private static UUID uuid(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_INT_ARRAY) || tag.getIntArray(key).length != 4) { throw invalid(); }
        return tag.getUUID(key);
    }
    private static Direction direction(CompoundTag tag) {
        if (!tag.contains("direction", Tag.TAG_STRING)) { throw invalid(); }
        Direction direction = Direction.byName(tag.getString("direction"));
        if (direction == null || !direction.getName().equals(tag.getString("direction"))) { throw invalid(); }
        return direction;
    }
    static IllegalArgumentException invalid() { return new IllegalArgumentException("Unsupported force-field root"); }
    private ForceFieldCodecs() { }
}

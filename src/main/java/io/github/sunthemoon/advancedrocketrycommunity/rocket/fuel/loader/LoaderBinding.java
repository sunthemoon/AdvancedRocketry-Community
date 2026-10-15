package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The loader's schema-1 rocket binding, in 512 bytes, depth 8 and 32 nodes. The logical rocket is the identity; the
 * physical UUID and block position only locate it, and the server rechecks the entity it resolves.
 */
public record LoaderBinding(UUID logicalRocket, ResourceLocation level, UUID physicalRocket, BlockPos locator, UUID station) {
    public static final String KEY = "binding";
    public static final int MAX_BYTES = 512;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 32;

    public LoaderBinding {
        Objects.requireNonNull(logicalRocket, "logicalRocket");
        Objects.requireNonNull(physicalRocket, "physicalRocket");
        LoaderNbt.require(level != null && LoaderNbt.canonical(level.toString()).equals(level), "Invalid binding Level");
        locator = LoaderNbt.checked(locator);
    }

    public static boolean bounded(Tag tag) {
        return LoaderNbt.bounded(tag, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(LoaderNbt.SCHEMA, 1);
        tag.putUUID("logical_rocket", logicalRocket);
        tag.putString("level", level.toString());
        tag.putUUID("physical_rocket", physicalRocket);
        LoaderNbt.putPosition(tag, locator);
        if (station != null) { tag.putUUID("station", station); }
        LoaderNbt.require(bounded(tag), "Loader binding exceeds its envelope");
        return tag;
    }

    public static LoaderBinding decode(Tag raw) {
        return LoaderNbt.decoding("loader binding", () -> {
            CompoundTag tag = LoaderNbt.schemaOne(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES, "Loader binding");
            LoaderNbt.fields(tag, LoaderNbt.SCHEMA, "logical_rocket", "level", "physical_rocket", "x", "y", "z", "station");
            return new LoaderBinding(LoaderNbt.uuid(tag, "logical_rocket"), LoaderNbt.id(tag, "level"),
                    LoaderNbt.uuid(tag, "physical_rocket"), LoaderNbt.position(tag), LoaderNbt.optionalUuid(tag, "station"));
        });
    }
}

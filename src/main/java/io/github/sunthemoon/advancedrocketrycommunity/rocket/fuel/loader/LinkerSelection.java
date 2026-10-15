package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The linker item's schema-1 {@code arce_linker} selection, in 1 KiB, depth 8 and 32 nodes: one device UUID, its Level
 * and its block position, with no arbitrary NBT, entity number or list. An invalid or future selection is inert.
 */
public record LinkerSelection(UUID device, ResourceLocation level, BlockPos pos) {
    public static final String KEY = "arce_linker";
    public static final int MAX_BYTES = 1_024;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 32;

    public LinkerSelection {
        Objects.requireNonNull(device, "device");
        LoaderNbt.require(level != null && LoaderNbt.canonical(level.toString()).equals(level), "Invalid selection Level");
        pos = LoaderNbt.checked(pos);
    }

    public static boolean bounded(Tag tag) {
        return LoaderNbt.bounded(tag, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(LoaderNbt.SCHEMA, 1);
        tag.putUUID("device", device);
        tag.putString("level", level.toString());
        LoaderNbt.putPosition(tag, pos);
        LoaderNbt.require(bounded(tag), "Linker selection exceeds its envelope");
        return tag;
    }

    public static LinkerSelection decode(Tag raw) {
        return LoaderNbt.decoding("linker selection", () -> {
            CompoundTag tag = LoaderNbt.schemaOne(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES, "Linker selection");
            LoaderNbt.fields(tag, LoaderNbt.SCHEMA, "device", "level", "x", "y", "z");
            return new LinkerSelection(LoaderNbt.uuid(tag, "device"), LoaderNbt.id(tag, "level"), LoaderNbt.position(tag));
        });
    }
}

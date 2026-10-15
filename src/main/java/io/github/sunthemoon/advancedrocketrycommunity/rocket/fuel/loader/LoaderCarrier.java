package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The dropped loader item's schema-1 carrier, in 12 KiB, depth 18 and 1,024 nodes. It holds only the owner and the
 * tank: no device identity, binding or escrow marker survives a break. Placement applies the owner rule separately.
 */
public record LoaderCarrier(UUID owner, LoaderTank tank) {
    public static final String KEY = "arce_fluid_fuel_loader_carrier";
    public static final int MAX_BYTES = 12_288;
    public static final int MAX_DEPTH = 18;
    public static final int MAX_NODES = 1_024;

    public LoaderCarrier {
        Objects.requireNonNull(tank, "tank");
    }

    /** What breaking an unprotected device drops; a device that escrow holds drops nothing. */
    public static LoaderCarrier drop(LoaderRoot root) {
        LoaderNbt.require(!root.escrow().locked(), "An escrow-locked loader drops nothing");
        return new LoaderCarrier(root.owner(), root.tank());
    }

    public static boolean bounded(Tag tag) {
        return LoaderNbt.bounded(tag, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(LoaderNbt.SCHEMA, 1);
        if (owner != null) { tag.putUUID("owner", owner); }
        tag.put("tank", tank.encode());
        LoaderNbt.require(bounded(tag), "Loader carrier exceeds its envelope");
        return tag;
    }

    public static LoaderCarrier decode(Tag raw) {
        return LoaderNbt.decoding("loader carrier", () -> {
            CompoundTag tag = LoaderNbt.schemaOne(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES, "Loader carrier");
            LoaderNbt.fields(tag, LoaderNbt.SCHEMA, "owner", "tank");
            return new LoaderCarrier(LoaderNbt.optionalUuid(tag, "owner"), LoaderTank.decode(LoaderNbt.compound(tag, "tank")));
        });
    }
}

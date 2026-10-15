package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The loader block entity's one schema-1 root under {@code arce_fluid_fuel_loader}, in 32 KiB, depth 20 and 2,048
 * nodes: the owner (absent while unclaimed), the fresh placement UUID, the binding, the native tank and the escrow
 * section. Every valid tank, binding and section fits it, so a device never holds state it cannot save.
 */
public record LoaderRoot(UUID owner, UUID device, LoaderBinding binding, LoaderTank tank, LoaderEscrowSection escrow) {
    public static final String KEY = "arce_fluid_fuel_loader";
    public static final int MAX_BYTES = 32_768;
    public static final int MAX_DEPTH = 20;
    public static final int MAX_NODES = 2_048;

    /** How a block entity's saved data reads. Only ABSENT (an empty, unclaimed device) and VALID are supported. */
    public enum Status {
        ABSENT, VALID, FUTURE, INVALID, UNREGISTERED_FLUID;

        public boolean supported() { return this == ABSENT || this == VALID; }
    }

    /** The root is present only when the status is VALID; the caller keeps any unsupported raw data verbatim. */
    public record Read(Status status, LoaderRoot root, String reason) {
        public Read {
            Objects.requireNonNull(status, "status");
            LoaderNbt.require(status == Status.VALID == (root != null), "A read carries a root exactly when it is valid");
        }
    }

    public LoaderRoot {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(tank, "tank");
        Objects.requireNonNull(escrow, "escrow");
        LoaderNbt.require(binding == null || owner != null, "Only a claimed loader may be bound");
    }

    /** Validated placement: a fresh device UUID with no binding and a pristine escrow section. */
    public static LoaderRoot placed(UUID owner, UUID device, LoaderTank tank) {
        return new LoaderRoot(owner, device, null, tank, LoaderEscrowSection.neutral());
    }

    /** The escrow endpoint signature of this device's tank. */
    public String signature() { return tank.signature(device); }

    public static boolean bounded(Tag tag) {
        return LoaderNbt.bounded(tag, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(LoaderNbt.SCHEMA, 1);
        if (owner != null) { tag.putUUID("owner", owner); }
        tag.putUUID("device", device);
        if (binding != null) { tag.put(LoaderBinding.KEY, binding.encode()); }
        tag.put("tank", tank.encode());
        tag.put(LoaderEscrowSection.KEY, escrow.encode());
        LoaderNbt.require(bounded(tag), "Loader root exceeds its envelope");
        return tag;
    }

    public static LoaderRoot decode(Tag raw) {
        return LoaderNbt.decoding("loader root", () -> {
            CompoundTag tag = LoaderNbt.schemaOne(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES, "Loader root");
            LoaderNbt.fields(tag, LoaderNbt.SCHEMA, "owner", "device", LoaderBinding.KEY, "tank", LoaderEscrowSection.KEY);
            UUID owner = LoaderNbt.optionalUuid(tag, "owner");
            UUID device = LoaderNbt.uuid(tag, "device");
            LoaderBinding binding = tag.contains(LoaderBinding.KEY) ? LoaderBinding.decode(LoaderNbt.compound(tag, LoaderBinding.KEY)) : null;
            LoaderTank tank = LoaderTank.decode(LoaderNbt.compound(tag, "tank"));
            LoaderEscrowSection escrow = LoaderEscrowSection.decode(LoaderNbt.compound(tag, LoaderEscrowSection.KEY));
            return new LoaderRoot(owner, device, binding, tank, escrow);
        });
    }

    /** Reads the root from a block entity's saved data; a missing root means an empty, unclaimed device. */
    public static Read read(CompoundTag parent) {
        if (!parent.contains(KEY)) { return new Read(Status.ABSENT, null, "absent"); }
        try {
            return new Read(Status.VALID, decode(parent.get(KEY)), "valid");
        } catch (LoaderDataException refused) {
            return new Read(switch (refused.reason()) {
                case FUTURE -> Status.FUTURE;
                case INVALID -> Status.INVALID;
                case UNREGISTERED_FLUID -> Status.UNREGISTERED_FLUID;
            }, null, refused.getMessage());
        }
    }
}

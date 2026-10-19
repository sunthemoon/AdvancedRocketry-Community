package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderDataException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Strict schema-1 cargo root. Unsupported input is never repaired, truncated or converted to empty state. */
public record RocketFluidCargoRoot(UUID owner, UUID device, RocketCargoRole role, LoaderBinding binding,
                                   LoaderTank tank, RocketEscrowSection escrow) {
    public static final String KEY = "arce_rocket_fluid_cargo";
    public static final int MAX_BYTES = 32_768;
    public static final int MAX_DEPTH = 20;
    public static final int MAX_NODES = 2_048;
    public static final String ESCROW = "resource_escrow";
    private static final Set<String> FIELDS = Set.of("schema_version", "owner", "device", "role", "binding", "tank", ESCROW);

    public enum Status {
        ABSENT, VALID, FUTURE, INVALID, UNREGISTERED_FLUID;
        public boolean supported() { return this == ABSENT || this == VALID; }
    }
    public record Read(Status status, RocketFluidCargoRoot root) {
        public Read {
            Objects.requireNonNull(status);
            require((status == Status.VALID) == (root != null), "Invalid cargo read result");
        }
    }

    public RocketFluidCargoRoot {
        Objects.requireNonNull(device); Objects.requireNonNull(role); Objects.requireNonNull(tank); Objects.requireNonNull(escrow);
        require(binding == null || owner != null, "Only a claimed device may be bound");
        require(escrow.markers().size() <= 1 && escrow.markers().stream().allMatch(m -> m.role().equals(role.markerRole())),
                "Cargo marker does not match the device role");
        require(escrowBounded(escrow.encode()), "Cargo escrow exceeds its envelope");
    }

    public static RocketFluidCargoRoot placed(UUID owner, UUID device, RocketCargoRole role, LoaderTank tank) {
        return new RocketFluidCargoRoot(owner, device, role, null, tank, RocketEscrowSection.neutral());
    }
    public RocketFluidCargoRoot withTank(LoaderTank next) {
        return new RocketFluidCargoRoot(owner, device, role, binding, next, escrow);
    }
    public RocketFluidCargoRoot withBinding(LoaderBinding next) {
        return new RocketFluidCargoRoot(owner, device, role, next, tank, escrow);
    }
    public String signature() { return tank.signature(device); }
    public boolean marked() { return !escrow.markers().isEmpty(); }
    public static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES); }
    public static boolean escrowBounded(Tag raw) { return BoundedNbt.fits(raw, 256, 8, 128); }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", 1);
        if (owner != null) { tag.putUUID("owner", owner); }
        tag.putUUID("device", device);
        tag.putString("role", role.name());
        if (binding != null) { tag.put(LoaderBinding.KEY, binding.encode()); }
        tag.put("tank", tank.encode());
        tag.put(ESCROW, escrow.encode());
        require(bounded(tag), "Cargo root exceeds its envelope");
        return tag;
    }

    public static RocketFluidCargoRoot decode(Tag raw, RocketCargoRole expectedRole) {
        require(raw instanceof CompoundTag && bounded(raw), "Cargo root exceeds its envelope or is not a compound");
        CompoundTag tag = (CompoundTag) raw;
        require(tag.contains("schema_version", Tag.TAG_INT) && tag.getInt("schema_version") == 1,
                "Unsupported cargo schema");
        require(FIELDS.containsAll(tag.getAllKeys()), "Unknown cargo field");
        require(tag.contains("role", Tag.TAG_STRING) && tag.getString("role").equals(expectedRole.name()),
                "Cargo role does not match the block");
        require(tag.hasUUID("device") && (!tag.contains("owner") || tag.hasUUID("owner")), "Invalid cargo identity");
        CompoundTag section = compound(tag, ESCROW);
        require(escrowBounded(section), "Cargo escrow exceeds its envelope");
        return new RocketFluidCargoRoot(tag.contains("owner") ? tag.getUUID("owner") : null, tag.getUUID("device"),
                expectedRole, tag.contains("binding") ? LoaderBinding.decode(compound(tag, "binding")) : null,
                LoaderTank.decode(compound(tag, "tank")), RocketEscrowSection.decode(section));
    }

    public static Read read(CompoundTag outer, RocketCargoRole expectedRole) {
        if (!outer.contains(KEY)) { return new Read(Status.ABSENT, null); }
        Tag raw = outer.get(KEY);
        if (raw instanceof CompoundTag tag && future(tag)) { return new Read(Status.FUTURE, null); }
        try { return new Read(Status.VALID, decode(raw, expectedRole)); }
        catch (LoaderDataException refused) {
            return new Read(switch (refused.reason()) {
                case FUTURE -> Status.FUTURE;
                case UNREGISTERED_FLUID -> Status.UNREGISTERED_FLUID;
                case INVALID -> Status.INVALID;
            }, null);
        } catch (RuntimeException refused) { return new Read(Status.INVALID, null); }
    }

    private static boolean future(CompoundTag tag) {
        if (tag.contains("schema_version", Tag.TAG_INT) && tag.getInt("schema_version") > 1) { return true; }
        // Only inspect a known bounded schema-1 frame before reading its nested versions.
        return bounded(tag) && tag.contains("schema_version", Tag.TAG_INT) && tag.getInt("schema_version") == 1
                && tag.contains(ESCROW, Tag.TAG_COMPOUND)
                && tag.getCompound(ESCROW).contains("schema_version", Tag.TAG_INT)
                && tag.getCompound(ESCROW).getInt("schema_version") > 1;
    }
    private static CompoundTag compound(CompoundTag tag, String key) {
        require(tag.contains(key, Tag.TAG_COMPOUND), "Invalid cargo compound: " + key);
        return tag.getCompound(key);
    }
    private static void require(boolean valid, String reason) {
        if (!valid) { throw new IllegalArgumentException(reason); }
    }
}

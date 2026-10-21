package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemStackCodec;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderDataException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Strict schema-1 item cargo root, D-P's form: the C-A fields with {@code buffer} (four slots, an empty slot written
 * as {}) in place of the tank. The field set is closed. Unsupported input is never repaired, truncated or emptied.
 */
public record RocketItemCargoRoot(UUID owner, UUID device, RocketCargoRole role, LoaderBinding binding,
                                  EscrowItemBuffer buffer, RocketEscrowSection escrow) {
    public static final String KEY = "arce_rocket_item_cargo";
    public static final int MAX_BYTES = 32_768;
    public static final int MAX_DEPTH = 20;
    public static final int MAX_NODES = 2_048;
    public static final String BUFFER = "buffer";
    public static final String ESCROW = "resource_escrow";
    public static final EscrowItemBuffer EMPTY = new EscrowItemBuffer(Collections.nCopies(EscrowItemBuffer.SLOTS, new CompoundTag()));
    private static final Set<String> FIELDS = Set.of("schema_version", "owner", "device", "role", "binding", BUFFER, ESCROW);

    public enum Status {
        ABSENT, VALID, FUTURE, INVALID, UNREGISTERED_ITEM;
        public boolean supported() { return this == ABSENT || this == VALID; }
    }
    public record Read(Status status, RocketItemCargoRoot root) {
        public Read {
            Objects.requireNonNull(status);
            require((status == Status.VALID) == (root != null), "Invalid cargo read result");
        }
    }
    /** A structurally valid stack whose item is not registered in this session. */
    static final class UnregisteredItem extends IllegalArgumentException {
        UnregisteredItem() { super("Unregistered cargo item"); }
    }

    public RocketItemCargoRoot {
        Objects.requireNonNull(device); Objects.requireNonNull(role); Objects.requireNonNull(buffer); Objects.requireNonNull(escrow);
        require(binding == null || owner != null, "Only a claimed device may be bound");
        require(escrow.markers().size() <= 1 && escrow.markers().stream().allMatch(m -> m.role().equals(role.markerRole())),
                "Cargo marker does not match the device role");
        require(RocketFluidCargoRoot.escrowBounded(escrow.encode()), "Cargo escrow exceeds its envelope");
    }

    public static RocketItemCargoRoot placed(UUID owner, UUID device, RocketCargoRole role) {
        return new RocketItemCargoRoot(owner, device, role, null, EMPTY, RocketEscrowSection.neutral());
    }
    public RocketItemCargoRoot withBuffer(EscrowItemBuffer next) {
        return new RocketItemCargoRoot(owner, device, role, binding, next, escrow);
    }
    public RocketItemCargoRoot withBinding(LoaderBinding next) {
        return new RocketItemCargoRoot(owner, device, role, next, buffer, escrow);
    }
    /** The same root with one slot replaced; {@code stack} is a saved stack or {} for empty. */
    public RocketItemCargoRoot withSlot(int slot, CompoundTag stack) {
        List<CompoundTag> slots = new ArrayList<>(buffer.slots());
        slots.set(slot, stack);
        return withBuffer(new EscrowItemBuffer(slots));
    }
    public String signature() { return buffer.signature(device); }
    public boolean marked() { return !escrow.markers().isEmpty(); }
    public static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES); }

    /** The encoding without the envelope check, so a caller can refuse a change that would not fit. */
    public CompoundTag encodeUnchecked() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", 1);
        if (owner != null) { tag.putUUID("owner", owner); }
        tag.putUUID("device", device);
        tag.putString("role", role.name());
        if (binding != null) { tag.put(LoaderBinding.KEY, binding.encode()); }
        tag.put(BUFFER, buffer.encode());
        tag.put(ESCROW, escrow.encode());
        return tag;
    }
    public CompoundTag encode() {
        CompoundTag tag = encodeUnchecked();
        require(bounded(tag), "Cargo root exceeds its envelope");
        return tag;
    }

    public static RocketItemCargoRoot decode(Tag raw, RocketCargoRole expectedRole) {
        require(raw instanceof CompoundTag && bounded(raw), "Cargo root exceeds its envelope or is not a compound");
        CompoundTag tag = (CompoundTag) raw;
        require(tag.contains("schema_version", Tag.TAG_INT) && tag.getInt("schema_version") == 1,
                "Unsupported cargo schema");
        require(FIELDS.containsAll(tag.getAllKeys()), "Unknown cargo field");
        require(tag.contains("role", Tag.TAG_STRING) && tag.getString("role").equals(expectedRole.name()),
                "Cargo role does not match the block");
        require(tag.hasUUID("device") && (!tag.contains("owner") || tag.hasUUID("owner")), "Invalid cargo identity");
        CompoundTag section = compound(tag, ESCROW);
        require(RocketFluidCargoRoot.escrowBounded(section), "Cargo escrow exceeds its envelope");
        return new RocketItemCargoRoot(tag.contains("owner") ? tag.getUUID("owner") : null, tag.getUUID("device"),
                expectedRole, tag.contains("binding") ? LoaderBinding.decode(compound(tag, "binding")) : null,
                buffer(compound(tag, BUFFER)), RocketEscrowSection.decode(section));
    }

    /**
     * The buffer's structure first (D-K's registry-independent stack check on each slot), then the registry, then the
     * native round trip, so a missing mod reads as UNREGISTERED_ITEM rather than as invalid data.
     */
    private static EscrowItemBuffer buffer(CompoundTag tag) {
        require(tag.size() == 1 && tag.contains("slots", Tag.TAG_LIST), "Invalid cargo buffer frame");
        ListTag slots = tag.getList("slots", Tag.TAG_COMPOUND);
        require(slots.size() == EscrowItemBuffer.SLOTS && ((ListTag) tag.get("slots")).getElementType() == Tag.TAG_COMPOUND,
                "Cargo buffer must hold four slots");
        for (int index = 0; index < slots.size(); index++) {
            CompoundTag slot = slots.getCompound(index);
            if (slot.isEmpty()) { continue; }
            EscrowItemStackCodec.validateStructure(slot);
            // validateStructure has already checked the canonical ID syntax, so the parse cannot fail.
            if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.tryParse(slot.getString("id")))) { throw new UnregisteredItem(); }
        }
        return EscrowItemBuffer.decode(tag);
    }

    public static Read read(CompoundTag outer, RocketCargoRole expectedRole) {
        if (!outer.contains(KEY)) { return new Read(Status.ABSENT, null); }
        Tag raw = outer.get(KEY);
        if (raw instanceof CompoundTag tag && future(tag)) { return new Read(Status.FUTURE, null); }
        try { return new Read(Status.VALID, decode(raw, expectedRole)); }
        catch (UnregisteredItem missing) { return new Read(Status.UNREGISTERED_ITEM, null); }
        catch (LoaderDataException refused) {
            return new Read(refused.reason() == LoaderDataException.Reason.FUTURE ? Status.FUTURE : Status.INVALID, null);
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

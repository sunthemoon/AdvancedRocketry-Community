package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemStackCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.ArrayList;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Pure frozen item delta on one 27-slot vanilla chest, trapped chest or barrel payload, and on the device's
 * four-slot buffer. Like the fluid replay it never reinterprets a frozen delta; it only refuses one that no
 * longer applies.
 */
public final class RocketItemCargoMutation {
    public static final int SLOTS = 27;
    private static final Set<ResourceLocation> STORAGE = Set.of(ResourceLocation.tryParse("minecraft:chest"),
            ResourceLocation.tryParse("minecraft:trapped_chest"), ResourceLocation.tryParse("minecraft:barrel"));
    private static final Set<String> FIELDS = Set.of("Items", "CustomName", "Lock");

    /** A refusal with its own cargo status; callers report it as is, never widened to {@code INVALID_DATA}. */
    public static final class Refusal extends IllegalArgumentException {
        private final Status status;
        Refusal(Status status, String message) { super(message); this.status = status; }
        public Status status() { return status; }
    }
    private RocketItemCargoMutation() { }

    /** Synchronous pure replay, not a tick-budgeted server preparation or publication entry point. */
    public static RocketStructureSnapshot apply(RocketStructureSnapshot before, EscrowItemCargoPayload delta, boolean load) {
        requireTarget(before, delta, load);
        var after = RocketCargoMutation.replace(before, delta.cursor(), block -> payload(block, delta, load));
        RocketCargoMutation.verifyRoundTrip(RocketSnapshotNbtCodec.encode(after));
        return after;
    }

    static void requireTarget(RocketStructureSnapshot before, EscrowItemCargoPayload delta, boolean load) {
        delta.requireDirection(load);
        delta.cursor().requirePayload(before);
        if (before.propulsion().isEmpty() || !VanillaContainerRocketAdapter.ID.toString().equals(delta.cursor().adapter())) {
            throw new IllegalArgumentException("Item cargo requires modern propulsion and the container adapter");
        }
    }

    /** The cursor's container after the delta; Items stay sorted by slot, CustomName and Lock are copied unchanged. */
    static RocketBlockEntityPayload payload(RocketBlock block, EscrowItemCargoPayload delta, boolean load) {
        var before = block.blockEntityPayload().orElseThrow();
        var slots = container(block.state(), before);
        int slot = load ? delta.to() : delta.from();
        var moved = delta.stack();
        int n = count(moved);
        nativeStack(moved);
        var held = slots.get(slot);
        CompoundTag next;
        if (load) {
            next = held == null ? moved : merged(stack(held), moved, n);
        } else {
            if (held == null || !same(stack(held), moved) || count(held) < n) {
                throw new IllegalArgumentException("Item cargo container slot no longer holds the frozen stack");
            }
            next = count(held) == n ? null : withCount(stack(held), count(held) - n);
        }
        if (next == null) { slots.remove(slot); }
        else { next.putByte("Slot", (byte) slot); slots.put(slot, next); }
        var data = before.data(); var items = new ListTag();
        slots.values().forEach(items::add);
        data.put("Items", items);
        return new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, data);
    }

    /** The device buffer after the same delta: a load takes from slot {@code from}, an unload adds to slot {@code to}. */
    public static EscrowItemBuffer buffer(EscrowItemBuffer before, EscrowItemCargoPayload delta, boolean load) {
        delta.requireDirection(load);
        var slots = new ArrayList<>(before.slots());
        int slot = load ? delta.from() : delta.to();
        var moved = delta.stack();
        int n = count(moved);
        nativeStack(moved);
        var held = slots.get(slot);
        if (load) {
            if (held.isEmpty() || !same(held, moved) || count(held) < n) {
                throw new IllegalArgumentException("Item cargo buffer slot no longer holds the frozen stack");
            }
            slots.set(slot, count(held) == n ? new CompoundTag() : withCount(held, count(held) - n));
        } else {
            slots.set(slot, held.isEmpty() ? moved : merged(held, moved, n));
        }
        try { return new EscrowItemBuffer(slots); }
        catch (EscrowItemStackCodec.Refusal refused) { throw refusal(refused); }
    }

    /** A cargo container's occupied slots, keyed by unsigned slot; refuses non-cargo storage before reading Items. */
    public static SortedMap<Integer, CompoundTag> container(RocketBlockState state, RocketBlockEntityPayload payload) {
        if (!VanillaContainerRocketAdapter.ID.equals(payload.adapterId()) || !storage(state)) {
            throw new Refusal(Status.NO_CARGO_STORAGE, "Only chests, trapped chests and barrels hold item cargo");
        }
        var data = payload.data();
        if (data.contains("LootTable")) { throw new Refusal(Status.UNRESOLVED_LOOT, "Container loot is unresolved"); }
        require(FIELDS.containsAll(data.getAllKeys()), "Unknown container field");
        for (String key : new String[] {"CustomName", "Lock"}) {
            require(!data.contains(key) || data.contains(key, Tag.TAG_STRING), "Invalid container field " + key);
        }
        var slots = new TreeMap<Integer, CompoundTag>();
        if (data.contains("Items")) {
            require(data.contains("Items", Tag.TAG_LIST), "Container Items is not a list");
            var items = (ListTag) data.get("Items");
            require(items.isEmpty() || items.getElementType() == Tag.TAG_COMPOUND, "Container Items has the wrong element type");
            for (var raw : items) {
                var entry = (CompoundTag) raw;
                require(entry.contains("Slot", Tag.TAG_BYTE), "Container item has no slot");
                int slot = Byte.toUnsignedInt(entry.getByte("Slot"));
                require(slot < SLOTS && slots.put(slot, entry.copy()) == null, "Container slot is outside 27 or duplicated");
            }
        }
        return slots;
    }

    /** A chest, trapped chest or barrel; each half of a vanilla double chest is its own 27-slot payload (R44). */
    public static boolean storage(RocketBlockState state) {
        return STORAGE.contains(state.blockId());
    }

    /** Merge identity is D-K's: same item, equal tags and equal canonical digests; Count never decides. */
    static boolean mergeable(CompoundTag held, CompoundTag stack) {
        try { return EscrowItemStackCodec.sameItemAndTags(held, stack); }
        catch (EscrowItemStackCodec.Refusal notCargo) { return false; }
    }
    static ItemStack nativeStack(CompoundTag saved) {
        try { return EscrowItemStackCodec.validateNative(saved); }
        catch (EscrowItemStackCodec.Refusal refused) { throw refusal(refused); }
    }
    static int room(CompoundTag held, CompoundTag stack) {
        return Math.min(64, nativeStack(stack).getMaxStackSize()) - (held.isEmpty() ? 0 : count(held));
    }
    /** A container entry without its slot, as the codec and the buffer see it. */
    static CompoundTag stack(CompoundTag entry) {
        var stack = entry.copy(); stack.remove("Slot"); return stack;
    }
    static int count(CompoundTag stack) { return stack.getByte("Count"); }
    static CompoundTag withCount(CompoundTag stack, int count) {
        var result = stack.copy(); result.putByte("Count", (byte) count); return result;
    }

    private static CompoundTag merged(CompoundTag held, CompoundTag moved, int n) {
        if (!same(held, moved) || n > room(held, moved)) {
            throw new IllegalArgumentException("Item cargo target cannot merge the frozen stack");
        }
        return withCount(held, count(held) + n);
    }
    private static boolean same(CompoundTag held, CompoundTag moved) {
        try { return EscrowItemStackCodec.sameItemAndTags(held, moved); }
        catch (EscrowItemStackCodec.Refusal refused) { throw refusal(refused); }
    }
    static Refusal refusal(EscrowItemStackCodec.Refusal refused) {
        return new Refusal(refused.status() == EscrowItemStackCodec.Status.CARGO_TOO_LARGE
                ? Status.CARGO_TOO_LARGE : Status.INVALID_DATA, refused.getMessage());
    }
    private static void require(boolean valid, String message) {
        if (!valid) { throw new IllegalArgumentException(message); }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation.count;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation.mergeable;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation.room;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation.stack;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation.withCount;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemStackCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;

/**
 * W11CD section 5.3's server-side selection, with no packet. Containers are walked in canonical block order, with one
 * call of the supplied pure check per offered move: at most one per container in each of a load's two passes, and at
 * most one per occupied slot on an unload (R45). That check must include the complete capture and O1 envelopes.
 */
public final class RocketItemCargoSelection {
    public record Result(Status status, EscrowItemCargoPayload delta) { }
    public enum NoMove { DEVICE_EMPTY, DEVICE_FULL, NO_CARGO, CARGO_FULL, INCOMPATIBLE_CARGO }
    private RocketItemCargoSelection() { }

    public static Result select(RocketStructureSnapshot snapshot, EscrowItemBuffer buffer, boolean load,
                                Function<EscrowItemCargoPayload, Status> eligibility) {
        return new Cursor(snapshot, buffer, load, eligibility).advance(0, null);
    }

    /**
     * Whether an unload can place nothing, from the buffer alone: all four slots hold a stack at {@code min(64,
     * maxStackSize)}. A slot whose stack fails the native check cannot receive either. Exact, so admission can say
     * DEVICE_FULL before walking the rocket (W132-R2).
     */
    public static boolean full(List<CompoundTag> slots) {
        return slots.size() == EscrowItemBuffer.SLOTS && slots.stream().allMatch(s -> !s.isEmpty() && !hasRoom(s));
    }
    private static boolean hasRoom(CompoundTag entry) {
        try { return RocketItemCargoMutation.room(entry, entry) > 0; }
        catch (IllegalArgumentException refused) { return false; }
    }

    /**
     * Admission's resumable walk. The callback is a cheap candidate filter; complete eligibility runs separately
     * through the preparation ticket. One container (at most 27 offers) reserves a bounded estimate of its own work
     * (W132-R1); blocks without container payloads charge one visit. No container is copied or decoded before
     * reserving. Admission steps it through {@link RocketCargoPreparations#select}, which yields to active tickets.
     */
    public static final class Cursor {
        private final RocketStructureSnapshot snapshot;
        private final List<CompoundTag> slots;
        private final long bufferBytes;
        private final boolean load;
        private final Walk walk;
        private final int source;
        private int pass, index;
        private boolean storage, contents, room;
        private Result result;
        public Cursor(RocketStructureSnapshot snapshot, EscrowItemBuffer buffer, boolean load,
                      Function<EscrowItemCargoPayload, Status> eligibility) {
            this.snapshot = snapshot; this.slots = buffer.slots(); this.load = load;
            bufferBytes = slots.stream().mapToLong(RocketNbtSize::uncompressedBytes).sum();
            walk = new Walk(Objects.requireNonNull(eligibility));
            int first = -1;
            for (int slot = 0; first < 0 && slot < slots.size(); slot++) { if (!slots.get(slot).isEmpty()) { first = slot; } }
            source = first;
        }
        /** Null means the shared runtime budget is exhausted, not a refusal. */
        public Result advance(long tick, RocketCargoWorkBudget budget) {
            if (result != null) { return result; }
            if (load && source < 0) { return result = new Result(walk.refusal, null); }
            for (; pass < (load ? 2 : 1); pass++, index = 0) {
                while (index < snapshot.blocks().size()) {
                    var block = snapshot.blocks().get(index);
                    if (budget != null && !budget.charge(tick, containerBytes(block), 1, false)) { return null; }
                    if (visit(block)) { return result = new Result(Status.ELIGIBLE, walk.accepted); }
                    index++;
                }
            }
            return result = new Result(walk.refusal, null);
        }
        /**
         * One visit's byte reservation. A container's copies, native round trips and merge digests are a few passes over
         * its payload and, for each of its 27 slots, over the buffer, so this is a fixed multiple of both, like the item
         * preparation's stage 0. At the 256 KiB payload and four 4 KiB stacks it is 11,276,288 bytes, under the 16 MiB
         * tick budget; the cap only keeps a visit that could never fit from stalling the walk.
         */
        long containerBytes(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock block) {
            var payload = block.blockEntityPayload().filter(p -> VanillaContainerRocketAdapter.ID.equals(p.adapterId())).orElse(null);
            return payload == null ? 0 : Math.min(RocketCargoWorkBudget.MAX_BYTES,
                    16L * (payload.uncompressedBytes() + RocketItemCargoMutation.SLOTS * bufferBytes + 256));
        }
        /** Retry the same position with a filter that now rejects its previous candidate. */
        public void retry() {
            if (result == null || result.status() != Status.ELIGIBLE) { throw new IllegalStateException("No selected candidate to retry"); }
            result = null;
        }
        public NoMove noMove() {
            if (load && source < 0) { return NoMove.DEVICE_EMPTY; }
            if (!storage || walk.explicitRefusal) { return null; }
            if (load) { return room ? NoMove.INCOMPATIBLE_CARGO : NoMove.CARGO_FULL; }
            if (!contents) { return NoMove.NO_CARGO; }
            return full(slots) ? NoMove.DEVICE_FULL : NoMove.INCOMPATIBLE_CARGO;
        }
        private boolean visit(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock block) {
                var payload = block.blockEntityPayload().orElse(null);
                if (payload == null || !VanillaContainerRocketAdapter.ID.equals(payload.adapterId())) { return false; }
                SortedMap<Integer, CompoundTag> container;
                try { container = RocketItemCargoMutation.container(block.state(), payload); }
                catch (RocketItemCargoMutation.Refusal refused) { walk.refused(refused.status()); return false; }
                catch (IllegalArgumentException | ClassCastException unreadable) { walk.refused(Status.UNSUPPORTED_DATA); return false; }
                storage = true; contents |= !container.isEmpty();
                room |= container.size() < RocketItemCargoMutation.SLOTS || container.values().stream().anyMatch(e -> hasRoom(stack(e)));
                var p = block.position();
                var cursor = new EscrowCargoPayload.Cursor(snapshot.snapshotId(), p.x(), p.y(), p.z(), payload.adapterId().toString());
                return load ? walk.offer(walk.load(cursor, container, slots.get(source), source, pass == 0))
                        : walk.unload(cursor, container, slots);
        }
    }

    private static final class Walk {
        private final Function<EscrowItemCargoPayload, Status> eligibility;
        private Status refusal = Status.NO_CARGO_STORAGE;
        private EscrowItemCargoPayload accepted;
        private boolean explicitRefusal;
        Walk(Function<EscrowItemCargoPayload, Status> eligibility) { this.eligibility = eligibility; }
        void refused(Status status) { refusal = status; explicitRefusal = true; }

        /** The supplied check decides; its last refusal is the result when no later move is accepted. */
        boolean offer(EscrowItemCargoPayload delta) {
            if (delta == null) { return false; }
            Status status = Objects.requireNonNull(eligibility.apply(delta));
            if (status == Status.ELIGIBLE) { accepted = delta; return true; }
            refused(status); return false;
        }

        /** The first container slot with the same item and tags and room when merging, otherwise the first empty slot. */
        EscrowItemCargoPayload load(EscrowCargoPayload.Cursor cursor, SortedMap<Integer, CompoundTag> container,
                                    CompoundTag moving, int source, boolean merge) {
            try { RocketItemCargoMutation.nativeStack(moving); }
            catch (RocketItemCargoMutation.Refusal refused) { refused(refused.status()); return null; }
            int target = -1;
            if (merge) {
                for (var held : container.entrySet()) {
                    var entry = stack(held.getValue());
                    if (mergeable(entry, moving) && room(entry, moving) > 0) { target = held.getKey(); break; }
                }
            } else {
                for (int slot = 0; target < 0 && slot < RocketItemCargoMutation.SLOTS; slot++) {
                    if (!container.containsKey(slot)) { target = slot; }
                }
            }
            if (target < 0) { return null; }
            var held = container.containsKey(target) ? stack(container.get(target)) : new CompoundTag();
            return payload(cursor, withCount(moving, Math.min(count(moving), room(held, moving))), source, target);
        }

        /**
         * Each nonempty container slot in order, into a buffer slot with the same item and tags and room, else an empty
         * one. A slot that fails the stack checks or the supplied check stays where it is, and the next slot is offered.
         */
        boolean unload(EscrowCargoPayload.Cursor cursor, SortedMap<Integer, CompoundTag> container, List<CompoundTag> buffer) {
            for (var held : container.entrySet()) {
                var entry = stack(held.getValue());
                try { RocketItemCargoMutation.nativeStack(entry); }
                catch (RocketItemCargoMutation.Refusal refused) { refused(refused.status()); continue; }
                int target = -1;
                for (int slot = 0; slot < EscrowItemBuffer.SLOTS; slot++) {
                    var current = buffer.get(slot);
                    if (!current.isEmpty() && mergeable(current, entry) && room(current, entry) > 0) { target = slot; break; }
                }
                for (int slot = 0; target < 0 && slot < EscrowItemBuffer.SLOTS; slot++) {
                    if (buffer.get(slot).isEmpty()) { target = slot; }
                }
                if (target < 0) { continue; }
                if (offer(payload(cursor, withCount(entry, Math.min(count(entry), room(buffer.get(target), entry))), held.getKey(), target))) {
                    return true;
                }
            }
            return false;
        }

        /** A framed delta outside the payload bounds stays where it is, before any capture or reservation. */
        private EscrowItemCargoPayload payload(EscrowCargoPayload.Cursor cursor, CompoundTag stack, int from, int to) {
            try { return new EscrowItemCargoPayload(cursor, stack, from, to); }
            catch (EscrowItemStackCodec.Refusal refused) { refused(RocketItemCargoMutation.refusal(refused).status()); }
            catch (IllegalArgumentException invalid) { refused(Status.INVALID_DATA); }
            return null;
        }
    }
}

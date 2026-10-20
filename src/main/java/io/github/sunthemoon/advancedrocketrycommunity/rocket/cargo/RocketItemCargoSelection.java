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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;

/**
 * W11CD section 5.3's server-side selection, with no packet. Containers are walked in canonical block order, with one
 * call of the supplied pure check per offered move: at most one per container on a load, and at most one per occupied
 * slot on an unload (R45). That check must include the complete capture and O1 envelopes.
 */
public final class RocketItemCargoSelection {
    public record Result(Status status, EscrowItemCargoPayload delta) { }
    private RocketItemCargoSelection() { }

    public static Result select(RocketStructureSnapshot snapshot, EscrowItemBuffer buffer, boolean load,
                                Function<EscrowItemCargoPayload, Status> eligibility) {
        Objects.requireNonNull(eligibility);
        var slots = buffer.slots();
        var walk = new Walk(eligibility);
        int source = -1;
        for (int slot = 0; load && source < 0 && slot < EscrowItemBuffer.SLOTS; slot++) {
            if (!slots.get(slot).isEmpty()) { source = slot; }
        }
        if (load && source < 0) { return new Result(walk.refusal, null); }
        for (var block : snapshot.blocks()) {
            var payload = block.blockEntityPayload().orElse(null);
            if (payload == null || !VanillaContainerRocketAdapter.ID.equals(payload.adapterId())) { continue; }
            SortedMap<Integer, CompoundTag> container;
            try { container = RocketItemCargoMutation.container(block.state(), payload); }
            catch (RocketItemCargoMutation.Refusal refused) { walk.refusal = refused.status(); continue; }
            catch (IllegalArgumentException | ClassCastException unreadable) { walk.refusal = Status.UNSUPPORTED_DATA; continue; }
            var p = block.position();
            var cursor = new EscrowCargoPayload.Cursor(snapshot.snapshotId(), p.x(), p.y(), p.z(), payload.adapterId().toString());
            if (load ? walk.offer(walk.load(cursor, container, slots.get(source), source)) : walk.unload(cursor, container, slots)) {
                return new Result(Status.ELIGIBLE, walk.accepted);
            }
        }
        return new Result(walk.refusal, null);
    }

    private static final class Walk {
        private final Function<EscrowItemCargoPayload, Status> eligibility;
        private Status refusal = Status.NO_CARGO_STORAGE;
        private EscrowItemCargoPayload accepted;
        Walk(Function<EscrowItemCargoPayload, Status> eligibility) { this.eligibility = eligibility; }

        /** The supplied check decides; its last refusal is the result when no later move is accepted. */
        boolean offer(EscrowItemCargoPayload delta) {
            if (delta == null) { return false; }
            Status status = Objects.requireNonNull(eligibility.apply(delta));
            if (status == Status.ELIGIBLE) { accepted = delta; return true; }
            refusal = status; return false;
        }

        /** The first container slot with the same item and tags and room, otherwise the first empty slot. */
        EscrowItemCargoPayload load(EscrowCargoPayload.Cursor cursor, SortedMap<Integer, CompoundTag> container,
                                    CompoundTag moving, int source) {
            try { RocketItemCargoMutation.nativeStack(moving); }
            catch (RocketItemCargoMutation.Refusal refused) { refusal = refused.status(); return null; }
            int target = -1;
            for (var held : container.entrySet()) {
                var entry = stack(held.getValue());
                if (mergeable(entry, moving) && room(entry, moving) > 0) { target = held.getKey(); break; }
            }
            for (int slot = 0; target < 0 && slot < RocketItemCargoMutation.SLOTS; slot++) {
                if (!container.containsKey(slot)) { target = slot; }
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
                catch (RocketItemCargoMutation.Refusal refused) { refusal = refused.status(); continue; }
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
            catch (EscrowItemStackCodec.Refusal refused) { refusal = RocketItemCargoMutation.refusal(refused).status(); }
            catch (IllegalArgumentException invalid) { refusal = Status.INVALID_DATA; }
            return null;
        }
    }
}

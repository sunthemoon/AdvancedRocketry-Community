package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptWriter;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * W170's D6 and D7 (W27A-DESIGN sections 4, 4.1 R4 and 5). D6: once the journal's acknowledged version for L is T', one
 * census of both bounded ends through W98's port and the barrier's ordinary sub-quota, with the source validated by W98's
 * {@code survivorSlot} against a frozen altitude projection (S', TRANSIT for T, T's fuel, the bound position). D7: the
 * group's REGISTERED receipts leave in one guarded generation; the guard reads only the barrier proof, the journal lease
 * and the group's invalidation counter. A refused guard or a definite failure repeats D6; the exact acknowledgement ends
 * the claim with READY_SOURCE. W185 consumes/removes recognized copies before proving absence in every other slot.
 * W27d3: D6 waits for every checked end column, including unticketed margins, to be entity-loaded.
 * W170-R22: from D6's capture until D7's outcome, the source is watched on the server thread. Any position write or Level
 * removal of a rocket naming L invalidates the group at once, and each coordinator tick compares the source's position,
 * Level, flight, fuel and payload with the captured image, so a change after the removal queued refuses its guard too.
 */
final class BaySourceProof {
    /** W98's retry after an IO, force, bounds or veto failure; never after a superseding save (W98-R11). */
    static final int RETRY_TICKS = RocketOrdinaryRetirement.RETRY_TICKS;
    private static final Logger LOGGER = LogUtils.getLogger();
    private record Proved(BaySlotProofs.Proof proof, BooleanSupplier lease, BooleanSupplier token, CompoundTag frozen) {
        boolean valid() { return proof.valid() && lease.getAsBoolean() && token.getAsBoolean(); }
    }

    /** W170-R22: the captured source as the server thread sees it; owner thread only, never read from the IO guard. */
    private record Watch(BayClaim claim, RocketEntity source, ResourceKey<Level> level, double x, double y, double z,
                         String hash, RocketFlightData flight) {
        static Watch of(BayClaim claim, RocketEntity source) {
            return new Watch(claim, source, source.level().dimension(), source.getX(), source.getY(), source.getZ(),
                    source.snapshot().orElseThrow().contentHash(), source.flightData().orElseThrow());
        }
        boolean current() {
            return RocketTransferEntities.loadedEntity(source) && !source.isPassenger() && source.level().dimension() == level
                    && source.getX() == x && source.getY() == y && source.getZ() == z
                    && source.snapshot().filter(value -> value.contentHash().equals(hash)).isPresent()
                    && source.flightData().filter(flight::equals).isPresent();
        }
    }

    private final MinecraftServer server;
    private final BayDeploymentSession session;
    private final Consumer<UUID> offer;
    private final java.util.function.BiConsumer<BayClaim, UUID> beforeRemoval;
    private final java.util.function.Predicate<BayClaim> stall;
    private final java.util.function.BiFunction<BayClaim, BooleanSupplier, BooleanSupplier> guardCut;
    /** W98's port; the coordinator uses only its slot and barrier calls, so it is built without a transfer dispatch. */
    private final RocketRetirementWorld port;
    private final Map<UUID, Proved> proofs = new HashMap<>();
    private final Map<UUID, Watch> watches = new HashMap<>();
    private final Map<UUID, Long> retryAt = new HashMap<>();
    private final Map<UUID, BaySlotProofs> capturing = new HashMap<>();
    private final Set<UUID> retiring = new HashSet<>();

    /** {@code beforeRemoval}, {@code stall} and {@code guardCut} are the gated native cut seams (no-ops outside release tests). */
    BaySourceProof(MinecraftServer server, BayDeploymentSession session, Consumer<UUID> offer,
                   java.util.function.BiConsumer<BayClaim, UUID> beforeRemoval, java.util.function.Predicate<BayClaim> stall,
                   java.util.function.BiFunction<BayClaim, BooleanSupplier, BooleanSupplier> guardCut) {
        this.server = server; this.session = session; this.offer = offer; this.beforeRemoval = beforeRemoval; this.stall = stall;
        this.guardCut = guardCut;
        port = new RocketRetirementWorld(server, null);
    }

    /**
     * W170-R22, each coordinator tick before the steps: a watched source that left its captured image (moved, left its
     * Level or chunk, changed flight, fuel or payload, or became a passenger) invalidates its group's D6/D7 token.
     */
    void observe() {
        if (watches.isEmpty()) { return; }
        for (var it = watches.values().iterator(); it.hasNext(); ) {
            Watch watch = it.next();
            if (!watch.current()) { it.remove(); invalidate(watch.claim()); }
        }
    }

    /** W170-R22: D6's capture starts watching the captured source; the watch lasts through D7's outcome. */
    void watch(BayClaim claim, RocketEntity source) { watches.put(claim.logical(), Watch.of(claim, source)); }

    /** W170-R22, from the entity's own position write or removal: a rocket naming a watched L invalidates that group. */
    void changed(RocketEntity rocket) {
        if (watches.isEmpty()) { return; }
        UUID logical = rocket.assemblyTransactionId().orElse(null);
        Watch watch = logical == null ? null : watches.remove(logical);
        if (watch != null) { invalidate(watch.claim()); }
    }

    private void invalidate(BayClaim claim) {
        claim.group().ifPresent(BayGroup::invalidate);
        offer.accept(claim.logical());
    }

    /** D6 at stage PROVE. */
    BayStepQueue.Result prove(BayClaim claim) {
        UUID logical = claim.logical();
        if (claim.hold() == BayClaim.Hold.REPAIR) { forget(logical); return BayStepQueue.Result.IDLE; }
        if (capturing.containsKey(logical)) { return capturing.get(logical).step(port, server.getTickCount()); }
        if (stall.test(claim) || server.getTickCount() < retryAt.getOrDefault(logical, Long.MIN_VALUE)) { return BayStepQueue.Result.NEXT_TICK; }
        BayGroup group = claim.group().orElseThrow();
        var journal = RocketTransferSavedData.get(server);
        RocketTransferRecord record = journal.find(claim.transfer()).orElse(null);
        if (BayConsumptionSteps.half(record, group) != BayGroup.Half.AFTER) { return repair(claim, "TRANSFER_AUTHORITY_CONFLICT"); }
        if (!journal.actionable(logical)) { return BayStepQueue.Result.NEXT_TICK; } // T' is not acknowledged yet
        RocketEntity source = BayConsumptionSteps.exactSource(server, record, claim.source().orElseThrow(), group);
        if (source == null) { return BayStepQueue.Result.NEXT_TICK; }
        if (!endsLoaded(server, claim.origin(), record)) { claim.waiting("END_LOADING"); return BayStepQueue.Result.NEXT_TICK; }
        String copies = BaySourceResume.copies(server, record, group, source.getUUID());
        if (copies != null) {
            claim.waiting(copies);
            return copies.equals("DUPLICATE_PENDING") ? BayStepQueue.Result.NEXT_TICK : repair(claim, copies);
        }
        claim.waiting(null);
        var slots = BayAbsenceCensus.slots(record);
        if (slots == null) { claim.waiting("END_UNAVAILABLE"); return BayStepQueue.Result.NEXT_TICK; }
        String waiting = BayAbsenceCensus.waiting(port, slots);
        if (waiting != null) { claim.waiting(waiting); return BayStepQueue.Result.NEXT_TICK; }
        var slot = slots.get(0);
        long now = server.getTickCount();
        if (!port.captureSupported(slot)) { claim.waiting("BARRIER_UNSUPPORTED"); return BayStepQueue.Result.NEXT_TICK; }
        if (!port.captureAvailable(slot, now)) { return retryNextTick(logical); }
        // W185 / W170-R62: waiting needs no frozen image. Save only once a capture can actually start.
        CompoundTag frozen = projection(source, record);
        if (frozen == null) { return repair(claim, "SOURCE_AUTHORITY_CONFLICT"); }
        var lease = journal.logicalLease(logical, record.transferId(), 0);
        var token = group.token();
        var attempt = new BaySlotProofs(slots, logical, source.getUUID(), frozen, () -> lease.getAsBoolean() && token.getAsBoolean(),
                () -> offer.accept(logical), result -> captured(claim, result, lease, token, frozen));
        capturing.put(logical, attempt);
        watch(claim, source);
        return attempt.step(port, now);
    }

    /**
     * W27d3: D6 awaits every checked column at both ends, including the in-session unticketed margins. It never
     * interprets an unloaded portion as absence or acquires extra tickets to make this predicate true.
     */
    static boolean endsLoaded(MinecraftServer server, BayClaim.Origin origin, RocketTransferRecord record) {
        RocketStructureSnapshot source = record.sourceSnapshot(), destination = record.destinationSnapshot();
        return RocketTransferEntities.endAlreadyLoaded(server, source) && RocketTransferEntities.endAlreadyLoaded(server, destination);
    }

    /**
     * Whether the claim's renewed tickets cover every checked column of one end. A restart group renews both ends' checked
     * columns, or nothing unless both are admissible (R1). An in-session claim renews the destination's ticket set, which
     * is its checked set when admissible, and the source's origin column alone, which covers the source end only when it
     * is that end's whole checked set.
     */
    static boolean covered(BayClaim.Origin origin, RocketStructureSnapshot source, RocketStructureSnapshot destination,
                           boolean atDestination) {
        var from = RocketDiscoveryColumns.of(source);
        var to = RocketDiscoveryColumns.of(destination);
        if (origin == BayClaim.Origin.RESTART) { return from.admissible() && to.admissible(); }
        return atDestination ? to.admissible() : from.admissible() && from.count() == 1;
    }

    private void captured(BayClaim claim, BaySlotProofs.Result result, BooleanSupplier lease, BooleanSupplier token, CompoundTag frozen) {
        UUID logical = claim.logical();
        capturing.remove(logical);
        if (claim.stage() != BayClaim.Stage.PROVE || claim.hold() == BayClaim.Hold.REPAIR) {
            watches.remove(logical);
            if (result.proof() != null) { result.proof().release(); }
            return;
        }
        var proved = result.confirmed() ? new Proved(result.proof(), lease, token, frozen) : null;
        if (proved != null && proved.valid()) {
            proofs.put(logical, proved);
            claim.advance(BayClaim.Stage.RETIRE);
        } else {
            watches.remove(logical);
            if (result.proof() != null) { result.proof().release(); }
            int delay = result.reason() == EscrowReason.SUPERSEDING_SAVE ? 0 : RETRY_TICKS;
            retryAt.put(logical, (long) server.getTickCount() + delay);
            // W170-R43: one line per refused capture, at W98's pacing; the bay menu shows no runtime hold
            String why = !result.confirmed() ? String.valueOf(result.reason()) : lease.getAsBoolean() ? "INVALIDATED" : "LEASE_LOST";
            LOGGER.info("ARCE_BAY_PROOF_REFUSED transfer={} logical={} reason={} retry={}", claim.transfer(), logical, why, delay);
        }
        offer.accept(logical);
    }

    /** D7 at stage RETIRE: the guarded group removal, issued only to a quiet store. */
    BayStepQueue.Result retire(BayClaim claim) {
        UUID logical = claim.logical();
        if (retiring.contains(logical)) { return BayStepQueue.Result.IDLE; }
        Proved proved = proofs.get(logical);
        if (claim.hold() == BayClaim.Hold.REPAIR) { forget(logical); return BayStepQueue.Result.IDLE; }
        if (proved == null || !proved.valid() || !watches.containsKey(logical) || !current(claim, proved)) { return again(claim); }
        BayGroup group = claim.group().orElseThrow();
        beforeRemoval.accept(claim, group.operations().get(0));
        var admission = session.removeGroup(group.operations(), guardCut.apply(claim, proved::valid), outcome -> retired(claim, group, outcome));
        if (admission.status() == BayReceiptStore.Status.BUSY) { return BayStepQueue.Result.NEXT_TICK; }
        if (!admission.queued()) {
            return admission.status() == BayReceiptStore.Status.STORE_UNAVAILABLE ? BayStepQueue.Result.NEXT_TICK
                    : repair(claim, "BAY_CONFLICT");
        }
        retiring.add(logical);
        return BayStepQueue.Result.IDLE;
    }

    private void retired(BayClaim claim, BayGroup group, BayReceiptWriter.Outcome outcome) {
        if (outcome == BayReceiptWriter.Outcome.UNKNOWN) { return; } // the writer retries the exact candidate under its guard
        UUID logical = claim.logical();
        retiring.remove(logical);
        watches.remove(logical);
        Proved proved = proofs.remove(logical);
        if (proved != null) { proved.proof().release(); }
        boolean removed = outcome == BayReceiptWriter.Outcome.COMMITTED
                && group.operations().stream().allMatch(op -> session.current(op).isEmpty());
        if (removed) {
            group.members().forEach(BayMember::retired);
            claim.end(BayClaim.Handoff.READY_SOURCE);
        } else {
            LOGGER.info("ARCE_BAY_RETIRE_REFUSED transfer={} logical={} outcome={}", claim.transfer(), logical, outcome);
            // GUARD_REFUSED or a definite failure: the receipts are back; D6 repeats with a fresh proof
            if (claim.stage() == BayClaim.Stage.RETIRE) { claim.advance(BayClaim.Stage.PROVE); }
        }
        offer.accept(logical);
    }

    /** B16 in RAM: the proved survivor is still the same image (same position, flight and payload), and T' is unchanged. */
    private boolean current(BayClaim claim, Proved proved) {
        var record = RocketTransferSavedData.get(server).find(claim.transfer()).orElse(null);
        var source = record == null ? null : BayConsumptionSteps.exactSource(server, record, claim.source().orElseThrow(), claim.group().orElseThrow());
        CompoundTag now = source == null ? null : projection(source, record);
        return now != null && RocketRetirementProof.matches(now, proved.frozen());
    }

    private BayStepQueue.Result again(BayClaim claim) {
        watches.remove(claim.logical());
        Proved stale = proofs.remove(claim.logical());
        if (stale != null) { stale.proof().release(); }
        claim.advance(BayClaim.Stage.PROVE);
        return BayStepQueue.Result.AGAIN;
    }

    /**
     * The frozen altitude projection of the exact source, or null unless it is T's loaded source identity with the
     * record's consumed snapshot, in TRANSIT for T at the bound altitude position with T's source fuel, and saved as a
     * world entity. W98's stationary {@code RocketRetirementGuards.projection} does not apply at altitude.
     */
    @Nullable
    static CompoundTag projection(RocketEntity source, RocketTransferRecord record) {
        if (!RocketTransferEntities.loadedEntity(source) || !source.operational() || source.isPassenger()
                || !source.level().dimension().location().equals(record.sourceSnapshot().sourceDimension())
                || !source.getUUID().equals(record.sourceEntityId())
                || source.snapshot().filter(value -> value.snapshotId().equals(record.sourceSnapshot().snapshotId())
                        && value.contentHash().equals(record.sourceSnapshot().contentHash())).isEmpty()
                || !BayConsumptionSteps.inTransit(source, record)
                || !source.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel())) { return null; }
        var tag = new CompoundTag();
        return source.save(tag) ? tag : null;
    }

    private BayStepQueue.Result retryNextTick(UUID logical) {
        port.retryNextTick(() -> offer.accept(logical));
        return BayStepQueue.Result.IDLE;
    }

    private static BayStepQueue.Result repair(BayClaim claim, String reason) {
        claim.repair(reason);
        return BayStepQueue.Result.IDLE;
    }

    /** A claim's end, or its gated forget: release any held proof. */
    void forget(UUID logical) {
        Proved proved = proofs.remove(logical);
        if (proved != null) { proved.proof().release(); }
        BaySlotProofs attempt = capturing.remove(logical);
        if (attempt != null) { attempt.close(); }
        retryAt.remove(logical); retiring.remove(logical); watches.remove(logical);
    }

    void clear() {
        proofs.values().forEach(proved -> proved.proof().release());
        capturing.values().forEach(BaySlotProofs::close);
        proofs.clear(); retryAt.clear(); capturing.clear(); retiring.clear(); watches.clear();
    }
}

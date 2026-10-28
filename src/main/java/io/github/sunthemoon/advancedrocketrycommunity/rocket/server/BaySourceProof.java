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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayProofTurns;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
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
 * R154: one claim at a time takes D6's captures to D7's outcome ({@link BayProofTurns}); a claim ready to capture waits
 * for its turn in line with BAY_PROOF_TURN. The holder keeps the turn through W98-R11's retries and a proof invalidated
 * before D7 queues its removal; it passes the turn on when D7 ends, when D7 parks it, when it holds for repair or ends, and
 * when, holding no proof, it must wait for anything other than its retry or the barrier's quota.
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
    /** W185-R9: the session's quiet-store read, which D7 makes before it serializes the source. */
    private final BooleanSupplier storeWaits;
    private final Map<UUID, Proved> proofs = new HashMap<>();
    private final Map<UUID, Watch> watches = new HashMap<>();
    private final Map<UUID, Long> retryAt = new HashMap<>();
    private final Map<UUID, BaySlotProofs> capturing = new HashMap<>();
    private final Set<UUID> retiring = new HashSet<>();
    /** W185-R5: claims whose D7 found the store busy; their next D6 waits for a quiet store. */
    private final Set<UUID> parked = new HashSet<>();
    private final BayProofTurns turns = new BayProofTurns();

    /** {@code beforeRemoval}, {@code stall} and {@code guardCut} are the gated native cut seams (no-ops outside release tests). */
    BaySourceProof(MinecraftServer server, BayDeploymentSession session, Consumer<UUID> offer,
                   java.util.function.BiConsumer<BayClaim, UUID> beforeRemoval, java.util.function.Predicate<BayClaim> stall,
                   java.util.function.BiFunction<BayClaim, BooleanSupplier, BooleanSupplier> guardCut) {
        this.server = server; this.session = session; this.offer = offer; this.beforeRemoval = beforeRemoval; this.stall = stall;
        this.guardCut = guardCut;
        port = new RocketRetirementWorld(server, null);
        storeWaits = () -> session.removalWaits();
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
    boolean stalled(BayClaim claim) { return stall.test(claim); }

    BayStepQueue.Result prove(BayClaim claim) {
        UUID logical = claim.logical();
        if (claim.hold() == BayClaim.Hold.REPAIR) { forget(logical); return BayStepQueue.Result.IDLE; }
        if (capturing.containsKey(logical)) { return capturing.get(logical).step(port, server.getTickCount()); }
        // R154: the holder keeps its turn through W98-R11's retry after a refused capture.
        if (stall.test(claim) || server.getTickCount() < retryAt.getOrDefault(logical, Long.MIN_VALUE)) { return BayStepQueue.Result.NEXT_TICK; }
        BayGroup group = claim.group().orElseThrow();
        var journal = RocketTransferSavedData.get(server);
        RocketTransferRecord record = journal.find(claim.transfer()).orElse(null);
        if (BayConsumptionSteps.half(record, group) != BayGroup.Half.AFTER) { return repair(claim, "TRANSFER_AUTHORITY_CONFLICT"); }
        if (!journal.actionable(logical)) { return await(claim, null); } // T' is not acknowledged yet
        RocketEntity source = BayConsumptionSteps.exactSource(server, record, claim.source().orElseThrow(), group);
        if (source == null) { return await(claim, null); }
        if (!endsLoaded(server, record)) { return await(claim, "END_LOADING"); }
        // W185-R11: the one census and rule set D5 normalized with; a copy seen now waits for its next consumption pass.
        var copies = BayCopyCensus.inspect(server, record, group);
        if (copies.refusal() != null) { claim.waiting(copies.refusal()); return repair(claim, copies.refusal()); }
        if (!copies.copies().isEmpty()) { return await(claim, "DUPLICATE_PENDING"); }
        claim.waiting(null);
        var slots = BayAbsenceCensus.slots(record);
        if (slots == null) { return await(claim, "END_UNAVAILABLE"); }
        String waiting = BayAbsenceCensus.waiting(port, slots);
        if (waiting != null) { return await(claim, waiting); }
        var slot = slots.get(0);
        long now = server.getTickCount();
        if (!port.captureSupported(slot)) { return await(claim, "BARRIER_UNSUPPORTED"); }
        // W185-R5: a claim D7 parked proves again only once the store is quiet, holding no slot proof meanwhile, and then
        // queues again behind the claims already waiting (R154). Other D6 starts do not wait for the store.
        if (parked.contains(logical) && storeWaits.getAsBoolean()) { return await(claim, "BAY_RECEIPT_WRITE"); }
        // R154: one claim at a time captures and holds census proofs through D7; the others wait in line, in claim order.
        if (!turns.take(logical)) { claim.waiting("BAY_PROOF_TURN"); return BayStepQueue.Result.NEXT_TICK; }
        parked.remove(logical);
        if (!port.captureAvailable(slot, now)) { return retryNextTick(logical); } // the holder keeps its turn
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
     * W185-R2: from CONSUME on, a held in-session claim renews each admissible end's census columns (at most 16 each), as
     * a restart group's R1 renews both ends, because D6 awaits every column of them, the low strip among them (R150).
     * REPAIR adds no ticket.
     */
    static boolean ticketsCensus(BayClaim claim) {
        return claim.origin() == BayClaim.Origin.CHECKPOINT && claim.hold() != BayClaim.Hold.REPAIR
                && (claim.stage() == BayClaim.Stage.CONSUME || claim.stage() == BayClaim.Stage.PROVE
                        || claim.stage() == BayClaim.Stage.RETIRE);
    }

    /**
     * W27d3: D6 awaits every census column at both ends, W170-R65's low strip among them (R150). It never interprets an
     * unloaded portion as absence; the claim's own renewed tickets cover both ends of a restart group and, from CONSUME
     * on, of an in-session claim (W185-R2).
     */
    static boolean endsLoaded(MinecraftServer server, RocketTransferRecord record) {
        return BayCensusColumns.loaded(server, record.sourceSnapshot()) && BayCensusColumns.loaded(server, record.destinationSnapshot());
    }

    private void captured(BayClaim claim, BaySlotProofs.Result result, BooleanSupplier lease, BooleanSupplier token, CompoundTag frozen) {
        UUID logical = claim.logical();
        capturing.remove(logical);
        if (claim.stage() != BayClaim.Stage.PROVE || claim.hold() == BayClaim.Hold.REPAIR) {
            watches.remove(logical);
            if (result.proof() != null) { result.proof().release(); }
            turns.leave(logical); // R154: a claim held for repair passes its turn on
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

    /** D7 at stage RETIRE: the guarded group removal, issued only to a quiet store; a busy one parks the claim (W185-R5). */
    BayStepQueue.Result retire(BayClaim claim) {
        UUID logical = claim.logical();
        if (retiring.contains(logical)) { return BayStepQueue.Result.IDLE; }
        Proved proved = proofs.get(logical);
        if (claim.hold() == BayClaim.Hold.REPAIR) { forget(logical); return BayStepQueue.Result.IDLE; }
        if (proved == null || !proved.valid() || !watches.containsKey(logical)) { return again(claim); }
        // W185-R9: a removal the store would refuse now waits before the source is serialized, so the wait saves nothing.
        if (storeWaits.getAsBoolean()) { return park(claim); }
        if (!current(claim, proved)) { return again(claim); }
        BayGroup group = claim.group().orElseThrow();
        beforeRemoval.accept(claim, group.operations().get(0));
        var admission = session.removeGroup(group.operations(), guardCut.apply(claim, proved::valid), outcome -> retired(claim, group, outcome));
        if (admission.status() == BayReceiptStore.Status.BUSY || admission.status() == BayReceiptStore.Status.STORE_UNAVAILABLE) {
            return park(claim);
        }
        if (!admission.queued()) { return repair(claim, "BAY_CONFLICT"); }
        retiring.add(logical);
        return BayStepQueue.Result.IDLE;
    }

    /**
     * W185-R5: D7 waits for a quiet receipt store without its proofs. It releases every slot proof and the watch, and this
     * claim's D6 proves again once the store is quiet, so a parked claim holds none of the save monitor's shared slots.
     * R154: the turn passes on, and the parked claim queues again behind the claims already waiting.
     */
    private BayStepQueue.Result park(BayClaim claim) {
        parked.add(claim.logical());
        claim.waiting("BAY_RECEIPT_WRITE");
        turns.leave(claim.logical());
        return again(claim);
    }

    private void retired(BayClaim claim, BayGroup group, BayReceiptWriter.Outcome outcome) {
        if (outcome == BayReceiptWriter.Outcome.UNKNOWN) { return; } // the writer retries the exact candidate under its guard
        UUID logical = claim.logical();
        turns.leave(logical); // R154: D7 ended; a refused claim asks again behind the claims already waiting
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

    /** R154: a wait before D6's capture holds no proof, so the claim leaves the line or passes its turn on. */
    private BayStepQueue.Result await(BayClaim claim, @Nullable String reason) {
        turns.leave(claim.logical());
        if (reason != null) { claim.waiting(reason); }
        return BayStepQueue.Result.NEXT_TICK;
    }

    /** R154: a claim held for repair cannot go on this session; it passes its turn on, with any proof it held. */
    private BayStepQueue.Result repair(BayClaim claim, String reason) {
        claim.repair(reason);
        forget(claim.logical());
        return BayStepQueue.Result.IDLE;
    }

    /**
     * R154: D5's copy pass, or one of its waits, ran at PROVE instead of D6, so the claim's census does not stand now. An
     * attempt still capturing ends as its next step would end it, BARRIER_UNSTABLE with W98's retry; then the claim, holding
     * no proof, leaves the line or passes its turn on.
     */
    void yieldTurn(BayClaim claim) {
        UUID logical = claim.logical();
        if (proofs.containsKey(logical) || retiring.contains(logical)) { return; }
        BaySlotProofs attempt = capturing.get(logical);
        if (attempt != null) {
            attempt.close();
            captured(claim, new BaySlotProofs.Result(null, EscrowReason.BARRIER_UNSTABLE), () -> false, () -> false, null);
        }
        turns.leave(logical);
    }

    /** A claim's end, or its gated forget: release any held proof, and pass its turn on (R154). */
    void forget(UUID logical) {
        Proved proved = proofs.remove(logical);
        if (proved != null) { proved.proof().release(); }
        BaySlotProofs attempt = capturing.remove(logical);
        if (attempt != null) { attempt.close(); }
        retryAt.remove(logical); retiring.remove(logical); watches.remove(logical); parked.remove(logical);
        turns.leave(logical);
    }

    void clear() {
        proofs.values().forEach(proved -> proved.proof().release());
        capturing.values().forEach(BaySlotProofs::close);
        proofs.clear(); retryAt.clear(); capturing.clear(); retiring.clear(); watches.clear(); parked.clear(); turns.clear();
    }

    /** The R154 turn and its line, for unit observations. */
    BayProofTurns turnsForTests() { return turns; }
}

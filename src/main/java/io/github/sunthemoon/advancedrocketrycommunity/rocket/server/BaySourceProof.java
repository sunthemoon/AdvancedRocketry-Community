package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

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
import net.minecraft.server.MinecraftServer;

/**
 * W170's D6 and D7 (W27A-DESIGN sections 4, 4.1 R4 and 5). D6: once the journal's acknowledged version for L is T', one
 * capture of the source's entity slot through W98's port and the barrier's ordinary sub-quota, validated by W98's
 * {@code survivorSlot} against a frozen altitude projection (S', TRANSIT for T, T's fuel, the bound position). D7: the
 * group's REGISTERED receipts leave in one guarded generation; the guard reads only the barrier proof, the journal lease
 * and the group's invalidation counter. A refused guard or a definite failure repeats D6; the exact acknowledgement ends
 * the claim with READY_SOURCE. Another recognized copy of L holds the claim as DUPLICATE_PENDING (W27d3 resolves it).
 */
final class BaySourceProof {
    /** W98's retry after an IO, force, bounds or veto failure; never after a superseding save (W98-R11). */
    static final int RETRY_TICKS = RocketOrdinaryRetirement.RETRY_TICKS;
    private record Proved(EscrowBarrier.Proof proof, BooleanSupplier lease, BooleanSupplier token, CompoundTag frozen) {
        boolean valid() { return proof.valid() && lease.getAsBoolean() && token.getAsBoolean(); }
    }

    private final MinecraftServer server;
    private final BayDeploymentSession session;
    private final Consumer<UUID> offer;
    /** W98's port; the coordinator uses only its slot and barrier calls, so it is built without a transfer dispatch. */
    private final RocketRetirementWorld port;
    private final Map<UUID, Proved> proofs = new HashMap<>();
    private final Map<UUID, Long> retryAt = new HashMap<>();
    private final Set<UUID> capturing = new HashSet<>(), retiring = new HashSet<>();

    BaySourceProof(MinecraftServer server, BayDeploymentSession session, Consumer<UUID> offer) {
        this.server = server; this.session = session; this.offer = offer; port = new RocketRetirementWorld(server, null);
    }

    /** D6 at stage PROVE. */
    BayStepQueue.Result prove(BayClaim claim) {
        UUID logical = claim.logical();
        if (claim.hold() == BayClaim.Hold.REPAIR || capturing.contains(logical)) { return BayStepQueue.Result.IDLE; }
        if (server.getTickCount() < retryAt.getOrDefault(logical, Long.MIN_VALUE)) { return BayStepQueue.Result.NEXT_TICK; }
        BayGroup group = claim.group().orElseThrow();
        var journal = RocketTransferSavedData.get(server);
        RocketTransferRecord record = journal.find(claim.transfer()).orElse(null);
        if (BayConsumptionSteps.half(record, group) != BayGroup.Half.AFTER) { return repair(claim, "TRANSFER_AUTHORITY_CONFLICT"); }
        if (!journal.actionable(logical)) { return BayStepQueue.Result.NEXT_TICK; } // T' is not acknowledged yet
        RocketEntity source = BayConsumptionSteps.exactSource(server, record, claim.source().orElseThrow(), group);
        if (source == null) { return BayStepQueue.Result.NEXT_TICK; }
        CompoundTag frozen = projection(source, record);
        if (frozen == null) { return repair(claim, "SOURCE_AUTHORITY_CONFLICT"); }
        String copies = BaySourceResume.copies(server, record, group, source.getUUID());
        if (copies != null) {
            claim.waiting(copies);
            return copies.equals("DUPLICATE_PENDING") ? BayStepQueue.Result.NEXT_TICK : repair(claim, copies);
        }
        claim.waiting(null);
        var slot = RocketRetirementProof.Slot.origin(record.sourceSnapshot());
        long now = server.getTickCount();
        if (!port.captureSupported(slot)) { claim.waiting("BARRIER_UNSUPPORTED"); return BayStepQueue.Result.NEXT_TICK; }
        if (!port.captureAvailable(slot, now)) { return retryNextTick(logical); }
        var lease = journal.logicalLease(logical, record.transferId(), 0);
        var token = group.token();
        capturing.add(logical);
        boolean begun = port.capture(slot, root -> RocketRetirementProof.survivorSlot(root, logical, frozen), now, label -> { },
                result -> captured(claim, result, lease, token, frozen));
        if (!begun) { capturing.remove(logical); return retryNextTick(logical); }
        return BayStepQueue.Result.IDLE;
    }

    private void captured(BayClaim claim, EscrowBarrier.Result result, BooleanSupplier lease, BooleanSupplier token, CompoundTag frozen) {
        UUID logical = claim.logical();
        capturing.remove(logical);
        if (claim.stage() != BayClaim.Stage.PROVE || claim.hold() == BayClaim.Hold.REPAIR) {
            if (result.proof() != null) { result.proof().release(); }
            return;
        }
        var proved = result.confirmed() ? new Proved(result.proof(), lease, token, frozen) : null;
        if (proved != null && proved.valid()) {
            proofs.put(logical, proved);
            claim.advance(BayClaim.Stage.RETIRE);
        } else {
            if (result.proof() != null) { result.proof().release(); }
            retryAt.put(logical, (long) server.getTickCount() + (result.reason() == EscrowReason.SUPERSEDING_SAVE ? 0 : RETRY_TICKS));
        }
        offer.accept(logical);
    }

    /** D7 at stage RETIRE: the guarded group removal, issued only to a quiet store. */
    BayStepQueue.Result retire(BayClaim claim) {
        UUID logical = claim.logical();
        if (retiring.contains(logical)) { return BayStepQueue.Result.IDLE; }
        Proved proved = proofs.get(logical);
        if (claim.hold() == BayClaim.Hold.REPAIR) { return BayStepQueue.Result.IDLE; }
        if (proved == null || !proved.valid() || !current(claim, proved)) { return again(claim); }
        BayGroup group = claim.group().orElseThrow();
        var admission = session.removeGroup(group.operations(), proved::valid, outcome -> retired(claim, group, outcome));
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
        Proved proved = proofs.remove(logical);
        if (proved != null) { proved.proof().release(); }
        boolean removed = outcome == BayReceiptWriter.Outcome.COMMITTED
                && group.operations().stream().allMatch(op -> session.current(op).isEmpty());
        if (removed) {
            group.members().forEach(BayMember::retired);
            claim.end(BayClaim.Handoff.READY_SOURCE);
        } else if (claim.stage() == BayClaim.Stage.RETIRE) {
            claim.advance(BayClaim.Stage.PROVE); // GUARD_REFUSED or a definite failure: the receipts are back; D6 repeats
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
        retryAt.remove(logical); capturing.remove(logical); retiring.remove(logical);
    }

    void clear() {
        proofs.values().forEach(proved -> proved.proof().release());
        proofs.clear(); retryAt.clear(); capturing.clear(); retiring.clear();
    }
}

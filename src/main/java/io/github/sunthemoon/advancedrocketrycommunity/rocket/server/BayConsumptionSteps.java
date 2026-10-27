package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferConsumption;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;

/**
 * W170's D5 (W27A-DESIGN sections 4 and 4.1, R3): in one server-thread step, publish S' on the exact source and T' in
 * the journal under scoped permits, then request the journal's checked write. Each half is published only if it is
 * still the before half, so a mixed restart image {@code (S',T)} or {@code (S,T')} completes only what is missing; the
 * consumed snapshots come from D4 in session, from T' when the journal already holds it, or are recomputed through the
 * runtime budget and must match the group's recorded after hashes (else REPAIR with CONSUMPTION_MISMATCH).
 */
final class BayConsumptionSteps {
    private final MinecraftServer server;
    private final BayGroupPublication publication;
    private final Map<UUID, BayConsumedSnapshots.Job> jobs = new HashMap<>();

    BayConsumptionSteps(MinecraftServer server, BayGroupPublication publication) {
        this.server = server; this.publication = publication;
    }

    BayStepQueue.Result consume(BayClaim claim) {
        if (claim.hold() == BayClaim.Hold.REPAIR) { return BayStepQueue.Result.IDLE; }
        BayGroup group = claim.group().orElseThrow();
        var journal = RocketTransferSavedData.get(server);
        RocketTransferRecord record = journal.find(claim.transfer()).orElse(null);
        BayGroup.Half journalHalf = half(record, group);
        if (journalHalf == null) { return repair(claim, "TRANSFER_AUTHORITY_CONFLICT"); }
        if (!journal.actionable(claim.logical())) { return BayStepQueue.Result.NEXT_TICK; } // T's latest change is not acknowledged yet
        RocketEntity source = exactSource(server, record, claim.source().orElseThrow(), group);
        if (source == null) { return BayStepQueue.Result.NEXT_TICK; } // not loaded: R1's tickets keep its end loading
        BayGroup.Half sourceHalf = group.sourceHalf(source.snapshot().orElseThrow().contentHash());
        if (!inTransit(source, record) || !RocketEscrowGate.bayNeutral(source, record.transferId())) {
            return repair(claim, "BAY_SOURCE_PROTECTED");
        }
        RocketTransferRecord consumed = consumed(claim, group, record, journalHalf);
        if (consumed == null) { return claim.hold() == BayClaim.Hold.REPAIR ? BayStepQueue.Result.IDLE : BayStepQueue.Result.NEXT_TICK; }
        try {
            if (sourceHalf == BayGroup.Half.BEFORE) {
                source.applyBayConsumption(new BaySourceConsumption(source.getUUID(), claim.logical(), record.ownerId(),
                        source.snapshot().orElseThrow(), consumed.sourceSnapshot()));
            }
            if (journalHalf == BayGroup.Half.BEFORE) {
                var result = RocketTransferConsumption.apply(journal, record, consumed, group.srcBefore(), group.dstBefore(),
                        group.srcAfter(), group.dstAfter());
                if (result == RocketTransferConsumption.Result.UNAVAILABLE) { return BayStepQueue.Result.NEXT_TICK; }
                if (result != RocketTransferConsumption.Result.CONSUMED && result != RocketTransferConsumption.Result.ALREADY_CONSUMED) {
                    return repair(claim, "CONSUMPTION_MISMATCH");
                }
            }
        } catch (RuntimeException refused) {
            return repair(claim, "CONSUMPTION_MISMATCH");
        }
        journal.flush(server); // a request: the checked writer persists T' off-thread and D6 waits for its acknowledgement
        jobs.remove(claim.logical());
        claim.advance(BayClaim.Stage.PROVE);
        return BayStepQueue.Result.AGAIN;
    }

    /** T' for this group: D4's in this session, the journal's own when it already holds T', else recomputed and checked. */
    @Nullable
    private RocketTransferRecord consumed(BayClaim claim, BayGroup group, RocketTransferRecord record, BayGroup.Half journalHalf) {
        if (journalHalf == BayGroup.Half.AFTER) { return record; }
        var prepared = publication.prepared(claim.logical()).orElse(null);
        if (prepared != null && prepared.before() == record) { return prepared.after(); }
        var job = jobs.get(claim.logical());
        if (job == null) {
            job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(),
                    BayGroupPublication.consumed(group.members())).orElse(null);
            if (job == null) { repair(claim, "CONSUMPTION_MISMATCH"); return null; }
            jobs.put(claim.logical(), job);
        }
        if (!job.step(RocketRuntime.cargoPreparations(server).budget(), server.getTickCount())) { return null; }
        jobs.remove(claim.logical());
        var result = job.result().orElseThrow();
        if (!result.source().contentHash().equals(group.srcAfter()) || !result.destination().contentHash().equals(group.dstAfter())) {
            repair(claim, "CONSUMPTION_MISMATCH"); return null;
        }
        try { return record.withConsumedSnapshots(result.source(), result.destination()); }
        catch (RuntimeException invalid) { repair(claim, "CONSUMPTION_MISMATCH"); return null; }
    }

    /** The journal record's half for the group: both ends BEFORE or both AFTER, PREPARED for exact (L, T); else null. */
    @Nullable
    static BayGroup.Half half(@Nullable RocketTransferRecord record, BayGroup group) {
        if (record == null || record.phase() != RocketTransferPhase.PREPARED || !record.logicalRocketId().equals(group.logical())
                || !record.transferId().equals(group.transfer()) || record.nativeTransfer().isPresent()) { return null; }
        var source = group.sourceHalf(record.sourceSnapshot().contentHash());
        return source != BayGroup.Half.OUTSIDE && source == group.destinationHalf(record.destinationSnapshot().contentHash()) ? source : null;
    }

    /** The loaded exact source of (L, T): its UUID, owner, snapshot ID, and a content hash of the group's source end. */
    @Nullable
    static RocketEntity exactSource(MinecraftServer server, RocketTransferRecord record, UUID uuid, BayGroup group) {
        var level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        if (level == null || !(level.getEntity(uuid) instanceof RocketEntity source) || !RocketTransferEntities.loadedEntity(source)
                || !source.operational() || source.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()
                || source.ownerId().filter(record.ownerId()::equals).isEmpty()) { return null; }
        var snapshot = source.snapshot().orElseThrow();
        return snapshot.snapshotId().equals(record.sourceSnapshot().snapshotId())
                && group.sourceHalf(snapshot.contentHash()) != BayGroup.Half.OUTSIDE ? source : null;
    }

    /** TRANSIT for T at the bound altitude position (W27A-DESIGN section 4.1's source reference). */
    static boolean inTransit(RocketEntity source, RocketTransferRecord record) {
        var flight = source.flightData().orElse(null);
        var origin = record.sourceSnapshot().sourceOrigin();
        return flight != null && flight.state() == RocketFlightState.TRANSIT
                && flight.activeTransferId().filter(record.transferId()::equals).isPresent() && !source.isPassenger()
                && source.getX() == origin.x() + 0.5D && source.getZ() == origin.z() + 0.5D
                && source.getY() == origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS;
    }

    private static BayStepQueue.Result repair(BayClaim claim, String reason) {
        claim.repair(reason);
        return BayStepQueue.Result.IDLE;
    }

    void forget(UUID logical) { jobs.remove(logical); }
    void clear() { jobs.clear(); }
}

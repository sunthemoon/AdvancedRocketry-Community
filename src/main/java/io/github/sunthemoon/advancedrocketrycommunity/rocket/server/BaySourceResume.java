package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * W170's restart resume for one copy (W27A-DESIGN section 4.1, R1 and R2). R1: while held, the claim renews the existing
 * flight tickets on T's checked columns of both ends (at most 16 per end), loading nothing else, and waits until the
 * source end's columns are entity-loaded and ticking; one group step per tick. R2: the exact source UUID, present and
 * neutral, is kept (a lagging COUNTDOWN or ASCENT image gets TRANSIT from T at the altitude position, with no launch
 * effect); absent, the bay-resume constructor rebuilds it with T's source UUID, the journal's source snapshot and T's
 * TRANSIT flight data restamped to the recovery tick, at altitude with zero velocity, never {@code rebuildSource}. Any
 * other recognized neutral copy at either end holds the claim at runtime as DUPLICATE_PENDING; a protected one, any
 * unexpected image naming L (W170-R21), an unexpected source, or {@value RocketFlightLimits#MAX_TRANSFER_ENTITY_MATCHES}
 * identities naming L hold it for repair.
 */
final class BaySourceResume {
    private final MinecraftServer server;
    private long stepTick = Long.MIN_VALUE;

    BaySourceResume(MinecraftServer server) { this.server = server; }

    /** R1's ticket renewal for a held restart group: false when an end is not admissible (no ticket is added then). */
    boolean renew(RocketTransferRecord record) {
        return RocketTransferEntities.refreshBayMargins(server, record) > 0;
    }

    /** R1 and R2 at stage RESUME. */
    BayStepQueue.Result resume(BayClaim claim) {
        if (claim.hold() == BayClaim.Hold.REPAIR) { return BayStepQueue.Result.IDLE; }
        long now = server.getTickCount();
        if (stepTick == now) { return BayStepQueue.Result.NEXT_TICK; } // one group per tick
        stepTick = now;
        BayGroup group = claim.group().orElseThrow();
        var journal = RocketTransferSavedData.get(server);
        if (!journal.operational()) { claim.waiting("TRANSFER_JOURNAL_UNAVAILABLE"); return BayStepQueue.Result.NEXT_TICK; }
        RocketTransferRecord record = journal.find(claim.transfer()).orElse(null);
        if (BayConsumptionSteps.half(record, group) == null) { return repair(claim, "TRANSFER_AUTHORITY_CONFLICT"); }
        ServerLevel level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        if (level == null || RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension()) == null
                || !RocketDiscoveryColumns.of(record.sourceSnapshot()).admissible()
                || !RocketDiscoveryColumns.of(record.destinationSnapshot()).admissible()) {
            claim.waiting("END_UNAVAILABLE"); // wider regions or missing Levels wait, with no additional ticket
            return BayStepQueue.Result.NEXT_TICK;
        }
        if (!RocketTransferEntities.endAlreadyLoaded(server, record.sourceSnapshot())) {
            claim.waiting("END_LOADING");
            return BayStepQueue.Result.NEXT_TICK;
        }
        if (RocketReleaseAccess.fenced(server, claim.logical())) { claim.waiting("RELEASE_FENCED"); return BayStepQueue.Result.NEXT_TICK; }
        UUID uuid = record.sourceEntityId();
        String copies = copies(server, record, group, uuid);
        if (copies != null) {
            claim.waiting(copies);
            return copies.equals("DUPLICATE_PENDING") ? BayStepQueue.Result.NEXT_TICK : repair(claim, copies);
        }
        Entity found = level.getEntity(uuid);
        if (found != null) {
            RocketEntity source = BayConsumptionSteps.exactSource(server, record, uuid, group);
            if (source == null || !RocketEscrowGate.bayNeutral(source, record.transferId()) || !establishTransit(source, record, level)) {
                return repair(claim, "SOURCE_AUTHORITY_CONFLICT");
            }
        } else if (!rebuild(level, record)) {
            return repair(claim, "SOURCE_REBUILD_REFUSED"); // never a second random source
        }
        claim.waiting(null);
        claim.establishSource(uuid);
        claim.advance(BayClaim.Stage.CONSUME);
        return BayStepQueue.Result.AGAIN;
    }

    /**
     * TRANSIT for T at the bound altitude: kept when already there; a lagging COUNTDOWN or ASCENT image of this flight is
     * set to T's TRANSIT flight data restamped to now, at altitude with zero velocity. Anything else refuses.
     */
    private static boolean establishTransit(RocketEntity source, RocketTransferRecord record, ServerLevel level) {
        if (BayConsumptionSteps.inTransit(source, record)) { return true; }
        var flight = source.flightData().orElseThrow();
        boolean lagging = (flight.state() == RocketFlightState.COUNTDOWN || flight.state() == RocketFlightState.ASCENT)
                && flight.logicalRocketId().equals(record.logicalRocketId())
                && flight.plan().filter(plan -> plan.requestId().equals(record.transferId())).isPresent()
                && flight.fuel().sameResources(record.sourceFlightData().fuel()) && !source.isPassenger();
        if (!lagging) { return false; }
        try { source.updateFlightData(restamped(record, level.getGameTime())); }
        catch (RuntimeException refused) { return false; }
        RocketTransferEntities.positionAtAltitude(source, record.sourceSnapshot().sourceOrigin());
        source.setDeltaMovement(Vec3.ZERO);
        return BayConsumptionSteps.inTransit(source, record);
    }

    /** The bay-resume constructor: T's source UUID, L, owner and the journal's source snapshot, in TRANSIT at altitude. */
    private boolean rebuild(ServerLevel level, RocketTransferRecord record) {
        RocketEntity source = ModEntities.ROCKET.get().create(level);
        if (source == null) { return false; }
        source.setUUID(record.sourceEntityId());
        try {
            source.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), record.ownerId(),
                    restamped(record, level.getGameTime()));
        } catch (RuntimeException invalid) { return false; }
        RocketTransferEntities.positionAtAltitude(source, record.sourceSnapshot().sourceOrigin());
        source.setDeltaMovement(Vec3.ZERO);
        return level.addFreshEntity(source) && BayConsumptionSteps.inTransit(source, record);
    }

    /** T's source TRANSIT flight data, with its state started at the recovery tick (W27A-DESIGN section 4.1). */
    static RocketFlightData restamped(RocketTransferRecord record, long gameTime) {
        var flight = record.sourceFlightData();
        return RocketFlightData.restore(flight.schemaVersion(), flight.logicalRocketId(), flight.state(), flight.fuel(),
                flight.plan().orElseThrow(), flight.passengers(), flight.currentBody(), flight.currentDimension(),
                flight.currentOrigin(), gameTime, flight.activeTransferId().orElseThrow(), flight.currentTarget().orElse(null));
    }

    /**
     * W170-R21's census of the loaded images of L at both recorded ends, other than {@code exact} at the source end. It
     * first bounds every loaded rocket naming L (its logical ID or its flight data's) in each end's box, at most
     * {@value RocketFlightLimits#MAX_TRANSFER_ENTITY_MATCHES} (reaching the cap is a conflict, never a truncation), then
     * classifies each one. An image is recognized when it names L in both places, has T's owner, is operational and not a
     * passenger, and carries its end's snapshot ID with one of that end's group hashes (a destination copy never matches
     * the source pair). Null when there is no other image; UNEXPECTED_COPY when any image is not recognized, including
     * T's source UUID at the destination end; else PROTECTED_DUPLICATE unless every recognized copy is neutral, which is
     * DUPLICATE_PENDING. The escrow reads come last, so an unexpected image never reaches them. Entity index reads only.
     */
    @Nullable
    static String copies(MinecraftServer server, RocketTransferRecord record, BayGroup group, UUID exact) {
        UUID logical = record.logicalRocketId();
        var source = End.of(server, record.sourceSnapshot(), group.srcBefore(), group.srcAfter());
        var destination = End.of(server, record.destinationSnapshot(), group.dstBefore(), group.dstAfter());
        var named = new java.util.IdentityHashMap<RocketEntity, List<End>>();
        for (End end : new End[] {source, destination}) {
            if (end != null && end.collect(logical, named)) { return "DUPLICATE_LIMIT"; }
        }
        var recognized = new ArrayList<RocketEntity>();
        for (var entry : named.entrySet()) {
            RocketEntity rocket = entry.getKey();
            if (source != null && rocket.getUUID().equals(exact) && entry.getValue().stream().anyMatch(end -> end.level() == source.level())) {
                continue; // T's exact source in the source Level; R2 and D6 check its own image
            }
            if (entry.getValue().stream().noneMatch(end -> end.recognizes(rocket, record))) { return "UNEXPECTED_COPY"; }
            recognized.add(rocket);
        }
        if (recognized.isEmpty()) { return null; }
        return recognized.stream().allMatch(copy -> RocketEscrowGate.bayNeutral(copy, record.transferId())) ? "DUPLICATE_PENDING"
                : "PROTECTED_DUPLICATE";
    }

    /** One recorded end: its Level, its bounded box (the region with the one-block lookup margin, whole column). */
    private record End(ServerLevel level, AABB box, RocketStructureSnapshot snapshot, List<String> hashes) {
        @Nullable
        static End of(MinecraftServer server, RocketStructureSnapshot snapshot, String before, String after) {
            ServerLevel level = RocketTransferEntities.level(server, snapshot.sourceDimension());
            if (level == null) { return null; }
            RocketRegion region = RocketRegion.fromSnapshot(snapshot);
            return new End(level, new AABB(region.minimum().x() - 1.0D, level.getMinBuildHeight(), region.minimum().z() - 1.0D,
                    region.maximum().x() + 2.0D, level.getMaxBuildHeight(), region.maximum().z() + 2.0D), snapshot, List.of(before, after));
        }

        /** Adds every loaded rocket naming L in this box; true when the census reached its cap. */
        boolean collect(UUID logical, java.util.Map<RocketEntity, List<End>> named) {
            level.getEntities().get(EntityTypeTest.forClass(RocketEntity.class), box, rocket -> {
                if (names(rocket, logical)) { named.computeIfAbsent(rocket, ignored -> new ArrayList<>()).add(this); }
                return named.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                        ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;
            });
            return named.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES;
        }

        boolean recognizes(RocketEntity rocket, RocketTransferRecord record) {
            UUID logical = record.logicalRocketId();
            return rocket.operational() && !rocket.isPassenger()
                    && rocket.assemblyTransactionId().filter(logical::equals).isPresent()
                    && rocket.flightData().filter(flight -> flight.logicalRocketId().equals(logical)).isPresent()
                    && rocket.ownerId().filter(record.ownerId()::equals).isPresent()
                    && rocket.snapshot().filter(value -> value.snapshotId().equals(snapshot.snapshotId())
                            && hashes.contains(value.contentHash())).isPresent();
        }
    }

    private static boolean names(RocketEntity rocket, UUID logical) {
        return rocket.assemblyTransactionId().filter(logical::equals).isPresent()
                || rocket.flightData().filter(flight -> logical.equals(flight.logicalRocketId())).isPresent();
    }

    private static BayStepQueue.Result repair(BayClaim claim, String reason) {
        claim.repair(reason);
        return BayStepQueue.Result.IDLE;
    }
}

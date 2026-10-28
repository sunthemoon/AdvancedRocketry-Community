package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
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
import org.slf4j.Logger;

/**
 * W170's restart resume for one copy (W27A-DESIGN section 4.1, R0 to R2). R0: neither R1 nor R2 acts before the exact
 * checked authority of (L, T) (W170-R23). R1: while held, the claim renews the existing
 * flight tickets on T's checked columns of both ends (at most 16 per end), loading nothing else, and waits until both
 * ends' columns are entity-loaded and ticking, so the census never counts a loading end as empty (W170-R41); one group
 * step per tick. R2: the exact source UUID, present and
 * neutral, is kept (a lagging COUNTDOWN or ASCENT image gets TRANSIT from T at the altitude position, with no launch
 * effect); absent, the bay-resume constructor rebuilds it with T's source UUID, the journal's source snapshot and T's
 * TRANSIT flight data restamped to the recovery tick, at altitude with zero velocity, never {@code rebuildSource}. Any
 * other recognized neutral copy at either end holds the claim at runtime as DUPLICATE_PENDING; a protected one, any
 * unexpected image naming L (W170-R21), an unexpected source, or {@value RocketFlightLimits#MAX_TRANSFER_ENTITY_MATCHES}
 * identities naming L hold it for repair.
 */
final class BaySourceResume {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final java.util.Set<UUID> renewing = new java.util.HashSet<>();
    private long stepTick = Long.MIN_VALUE;

    BaySourceResume(MinecraftServer server) { this.server = server; }

    /**
     * R1's ticket renewal for a held restart group: false when an end is not admissible (no ticket is added then). In
     * RESUME it needs the exact checked authority of (L, T) (W170-R23); from CONSUME on, the source authority that R2
     * established on that checked T covers the claim's own T' write. Either way T must be the group's (W170-R25). The
     * first renewal of a claim logs one line.
     */
    boolean renew(BayClaim claim) {
        var journal = RocketTransferSavedData.get(server);
        RocketTransferRecord record = claim.stage() == BayClaim.Stage.RESUME ? checked(journal, claim)
                : journal.find(claim.transfer()).orElse(null);
        if (record == null || BayConsumptionSteps.half(record, claim.group().orElseThrow()) == null) { return false; }
        if (renewing.add(claim.logical())) {
            LOGGER.info("ARCE_BAY_RESUME_TICKETS transfer={} logical={} stage={}", claim.transfer(), claim.logical(), claim.stage());
        }
        return RocketTransferEntities.refreshBayMargins(server, record) > 0;
    }

    /**
     * W170-R23: T under the exact checked authority of (L, T): L's latest change is in the journal's acknowledged
     * generation with no queued same-L effect, and that generation's guard names T with its record digest. Null while it
     * is pending, or when the acknowledged generation names no T or another one. The transfer service's existing baseline
     * request and checked-retry pacing write it; the claim only waits.
     */
    @Nullable
    static RocketTransferRecord checked(RocketTransferSavedData journal, BayClaim claim) {
        var guard = journal.operational() && journal.actionable(claim.logical()) ? journal.logicalGuard(claim.logical()).orElse(null) : null;
        return guard != null && claim.transfer().equals(guard.transfer()) && guard.recordDigest() != null
                ? journal.find(claim.transfer()).orElse(null) : null;
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
        if (!journal.actionable(claim.logical()) || journal.logicalGuard(claim.logical()).isEmpty()) {
            claim.waiting("TRANSFER_JOURNAL_PENDING"); // W170-R23: no R1 or R2 effect before the exact checked T
            return BayStepQueue.Result.NEXT_TICK;
        }
        RocketTransferRecord record = checked(journal, claim);
        if (BayConsumptionSteps.half(record, group) == null) { return repair(claim, "TRANSFER_AUTHORITY_CONFLICT"); }
        ServerLevel level = RocketTransferEntities.level(server, record.sourceSnapshot().sourceDimension());
        if (level == null || RocketTransferEntities.level(server, record.destinationSnapshot().sourceDimension()) == null
                || !RocketDiscoveryColumns.of(record.sourceSnapshot()).admissible()
                || !RocketDiscoveryColumns.of(record.destinationSnapshot()).admissible()) {
            claim.waiting("END_UNAVAILABLE"); // wider regions or missing Levels wait, with no additional ticket
            return BayStepQueue.Result.NEXT_TICK;
        }
        if (!RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record)) {
            claim.waiting("END_LOADING"); // W170-R41: both recorded ends, whose checked columns R1 already tickets
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
        } else {
            String refused = rebuild(level, record);
            if (refused != null) { return repair(claim, refused); } // never a second random source
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

    /**
     * The bay-resume constructor: T's source UUID, L, owner and the journal's source snapshot, in TRANSIT at altitude.
     * The unpublished candidate must pass the present source's eligibility first (W170-R24): its TRANSIT image, and the
     * neutral-wrapper rule for L and T (no escrow logical lock, ledger record, floor, armed flight, authority head or
     * tombstone, and an available escrow root). Null once published; else the repair reason, with nothing added.
     */
    @Nullable
    private String rebuild(ServerLevel level, RocketTransferRecord record) {
        RocketEntity source = ModEntities.ROCKET.get().create(level);
        if (source == null) { return "SOURCE_REBUILD_REFUSED"; }
        source.setUUID(record.sourceEntityId());
        try {
            source.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), record.ownerId(),
                    restamped(record, level.getGameTime()));
        } catch (RuntimeException invalid) { return "SOURCE_REBUILD_REFUSED"; }
        RocketTransferEntities.positionAtAltitude(source, record.sourceSnapshot().sourceOrigin());
        source.setDeltaMovement(Vec3.ZERO);
        if (!BayConsumptionSteps.inTransit(source, record)) { return "SOURCE_REBUILD_REFUSED"; }
        if (!RocketEscrowGate.bayNeutral(source, record.transferId())) { return "BAY_SOURCE_PROTECTED"; }
        return level.addFreshEntity(source) && BayConsumptionSteps.inTransit(source, record) ? null : "SOURCE_REBUILD_REFUSED";
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
     * first bounds every loaded rocket naming L (its logical ID, its flight data's, or a blocked image's preserved one,
     * W170-R42) in each end's box, at most {@value RocketFlightLimits#MAX_TRANSFER_ENTITY_MATCHES} (reaching the cap is
     * a conflict, never a truncation), then classifies each one. An image is recognized when it names L in both places, has T's owner, is operational and not a
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

    /** Its logical ID, its flight data's, or a blocked image's preserved one (W170-R42), never recognized, names L. */
    private static boolean names(RocketEntity rocket, UUID logical) {
        return rocket.assemblyTransactionId().filter(logical::equals).isPresent()
                || rocket.flightData().filter(flight -> logical.equals(flight.logicalRocketId())).isPresent()
                || rocket.preservedAssemblyTransactionId().filter(logical::equals).isPresent();
    }

    private static BayStepQueue.Result repair(BayClaim claim, String reason) {
        claim.repair(reason);
        return BayStepQueue.Result.IDLE;
    }

    /** A claim's end, or its gated forget. */
    void forget(UUID logical) { renewing.remove(logical); }
}

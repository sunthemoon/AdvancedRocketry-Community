package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaimBook;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

/** W27d3 R3: consume before removing, under the group's exact end/hash authority. */
final class BayCopySteps {
    private final MinecraftServer server;
    private final RocketRetirementWorld port;
    private final BayPassengerRouting passengers;

    BayCopySteps(MinecraftServer server) {
        this.server = server; port = new RocketRetirementWorld(server, null); passengers = new BayPassengerRouting(server);
    }

    boolean mountAllowed(Entity passenger, RocketEntity source) { return passengers.mountAllowed(passenger, source); }

    /** Join notifications also arrive before publication; inspect that supplied entity without a world-wide search. */
    void seen(RocketEntity rocket, BayClaimBook claims, java.util.function.Consumer<java.util.UUID> offer) {
        if (rocket.isRemoved()) { return; }
        var names = new java.util.HashSet<java.util.UUID>();
        rocket.assemblyTransactionId().ifPresent(names::add);
        rocket.preservedAssemblyTransactionId().ifPresent(names::add);
        rocket.flightData().ifPresent(flight -> names.add(flight.logicalRocketId()));
        for (var logical : names) {
            claims.get(logical).ifPresent(claim -> {
                if (claim.stage() == BayClaim.Stage.ENDED) { return; }
                claim.group().ifPresent(BayGroup::invalidate);
                var record = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData
                        .get(server).find(claim.transfer()).orElse(null);
                if (record != null && !BayCopyCensus.within(server, record, rocket)) { claim.repair("UNEXPECTED_COPY"); }
                offer.accept(logical);
            });
        }
    }

    /** Null means all recognized copies are consumed and removed; D6 must still prove the complete disk census. */
    BayStepQueue.Result normalize(BayClaim claim, RocketTransferRecord record) {
        return normalize(claim, record, () -> true);
    }

    BayStepQueue.Result normalize(BayClaim claim, RocketTransferRecord record, java.util.function.BooleanSupplier allowed) {
        if (!allowed.getAsBoolean()) { return BayStepQueue.Result.IDLE; }
        var group = claim.group().orElseThrow();
        if (claim.hold() == BayClaim.Hold.REPAIR) { return BayStepQueue.Result.IDLE; }
        if (BayConsumptionSteps.half(record, group) != BayGroup.Half.AFTER) { return repair(claim, "TRANSFER_AUTHORITY_CONFLICT"); }
        var slots = BayAbsenceCensus.slots(record);
        if (slots == null) { claim.waiting("END_UNAVAILABLE"); return BayStepQueue.Result.NEXT_TICK; }
        String wait = BayAbsenceCensus.waiting(port, slots);
        if (wait != null) { claim.waiting(wait); return BayStepQueue.Result.NEXT_TICK; }
        var survivor = BayConsumptionSteps.exactSource(server, record, record.sourceEntityId(), group);
        if (survivor == null) { claim.waiting("SOURCE_LOADING"); return BayStepQueue.Result.NEXT_TICK; }
        if (!BayConsumptionSteps.inTransit(survivor, record) || !RocketEscrowGate.bayNeutral(survivor, record.transferId())) {
            return repair(claim, "SOURCE_AUTHORITY_CONFLICT");
        }
        var scan = BayCopyCensus.inspect(server, record, group);
        if (scan.refusal() != null) { return repair(claim, scan.refusal()); }
        // W185-R4: only a passenger riding a copy that this step removes is moved; nobody else is pulled back.
        for (var copy : scan.copies()) {
            if (!allowed.getAsBoolean()) { return BayStepQueue.Result.IDLE; }
            var rocket = copy.rocket();
            if (!BayCopyCensus.recognizes(server, record, group, copy)) { return repair(claim, "UNEXPECTED_COPY"); }
            if (!RocketTransferEntities.loadedEntity(rocket) || !RocketEscrowGate.bayNeutral(rocket, record.transferId())) {
                return repair(claim, "PROTECTED_DUPLICATE");
            }
            var before = rocket.snapshot().orElseThrow();
            var after = copy.destination() ? record.destinationSnapshot() : record.sourceSnapshot();
            try {
                if (!before.contentHash().equals(after.contentHash())) {
                    if (!reserveCopyWork(RocketRuntime.cargoPreparations(server).budget(), server.getTickCount())) {
                        return BayStepQueue.Result.NEXT_TICK;
                    }
                    rocket.applyBayConsumption(new BaySourceConsumption(rocket.getUUID(), claim.logical(), record.ownerId(), before, after));
                }
            } catch (RuntimeException refused) { return repair(claim, "CONSUMPTION_MISMATCH"); }
            if (!allowed.getAsBoolean()) { return BayStepQueue.Result.IDLE; }
            if (!passengers.emptyCopy(claim, record, rocket, survivor, allowed)) {
                claim.waiting("DUPLICATE_PASSENGERS"); return BayStepQueue.Result.NEXT_TICK;
            }
            if (!allowed.getAsBoolean()) { return BayStepQueue.Result.IDLE; }
            // The scope never discards an unconsumed, foreign or protected image, and never drops its payload.
            if (!BayCopyCensus.recognizes(server, record, group, copy)) { return repair(claim, "UNEXPECTED_COPY"); }
            if (!RocketTransferEntities.loadedEntity(survivor) || !BayConsumptionSteps.inTransit(survivor, record)
                    || !RocketEscrowGate.bayNeutral(survivor, record.transferId())) { return repair(claim, "SOURCE_AUTHORITY_CONFLICT"); }
            if (rocket.snapshot().filter(value -> value.snapshotId().equals(after.snapshotId())
                    && value.contentHash().equals(after.contentHash())).isEmpty()
                    || !RocketEscrowGate.bayNeutral(rocket, record.transferId()) || !rocket.getPassengers().isEmpty()) {
                return repair(claim, "PROTECTED_DUPLICATE");
            }
            rocket.discard();
            if (!allowed.getAsBoolean()) { return BayStepQueue.Result.IDLE; }
            group.invalidate();
        }
        claim.waiting(null);
        return null;
    }

    /** Consumption publishes a visual snapshot too. Reserve the existing whole work allowance, never encode 63 copies in one tick. */
    static boolean reserveCopyWork(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget budget, long tick) {
        return budget.charge(tick, io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget.MAX_BYTES,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget.MAX_VISITS, true);
    }

    private static BayStepQueue.Result repair(BayClaim claim, String reason) {
        claim.group().ifPresent(BayGroup::invalidate);
        claim.repair(reason); return BayStepQueue.Result.IDLE;
    }
}

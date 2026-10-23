package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Gated ordinary journal fixtures over a real cargo rocket's captured pre-mutation snapshot. */
public final class EscrowCargoTransferFixtures {
    private EscrowCargoTransferFixtures() { }
    static RocketEntity create(net.minecraft.gametest.framework.GameTestHelper helper, net.minecraft.core.BlockPos origin,
            java.util.List<net.minecraftforge.fluids.FluidStack> tanks) {
        EscrowTestEndpoints.requireEnabled(helper.getLevel().getServer());
        var base = EscrowCargoPreparationGameTests.snapshot(helper.getLevel().dimension().location(), origin, false, tanks);
        var stats = base.stats();
        var snapshot = RocketStructureSnapshot.create(base.snapshotId(), base.sourceDimension(), base.sourceOrigin(), base.blocks(),
                java.util.List.of(base.blocks().get(base.blocks().size() - 1).position()),
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats(stats.blockCount(), stats.mass(), stats.thrust(),
                        stats.fuelCapacity(), stats.engineCount(), 1, stats.guidanceCount(), stats.blockEntityCount()),
                base.createdAtGameTime(), base.propulsion().orElseThrow());
        var rocket = java.util.Objects.requireNonNull(io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities.ROCKET.get().create(helper.getLevel()));
        rocket.initialize(snapshot, UUID.randomUUID(), UUID.randomUUID());
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1_000), 25));
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Cargo passenger fixture publication failed");
        return rocket;
    }
    public static RocketTransferRecord record(RocketEntity rocket, RocketStructureSnapshot recorded, boolean source) {
        return record(rocket, recorded, source, null);
    }
    public static RocketTransferRecord record(RocketEntity rocket, RocketStructureSnapshot recorded, boolean source, UUID passenger) {
        EscrowTestEndpoints.requireEnabled(rocket.getServer());
        var fuel = RocketFuelState.empty(recorded.propulsion().orElseThrow())
                .withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1_000);
        return record(rocket, recorded, source, passenger, fuel);
    }

    /** A legacy arrival with this fixture rocket's legal native fuel, retaining its pre-cargo snapshot. */
    static RocketTransferRecord landedRecord(RocketEntity rocket, RocketStructureSnapshot recorded) {
        return record(rocket, recorded, false, null, rocket.flightData().orElseThrow().fuel());
    }

    private static RocketTransferRecord record(RocketEntity rocket, RocketStructureSnapshot recorded, boolean source,
                                               UUID passenger, RocketFuelState fuel) {
        EscrowTestEndpoints.requireEnabled(rocket.getServer());
        var at = recorded.sourceOrigin(); var dimension = recorded.sourceDimension();
        var other = recorded.relocated(UUID.randomUUID(), dimension, new RocketPosition(at.x() + 8, at.y(), at.z()), 0);
        var from = source ? recorded : other; var to = source ? other : recorded;
        var transfer = UUID.randomUUID(); var logical = rocket.assemblyTransactionId().orElseThrow();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                dimension, dimension, 500, 0).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W124 native cargo journal fixture")));
        var flight = RocketFlightData.initial(logical, recorded.stats().fuelCapacity(), recorded.stats().seatCount(), plan.sourceBody(), dimension, from.sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan);
        if (passenger != null) { flight = flight.withPassengers(flight.passengers().assign(passenger).orElseThrow()); }
        flight = flight.startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), dimension, to.sourceOrigin(), 160);
        var prepared = RocketTransferRecord.create(transfer, logical, rocket.ownerId().orElseThrow(), source ? rocket.getUUID() : UUID.randomUUID(),
                from, to, flight, arrival, 500, 0);
        return source ? prepared : prepared.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }

    /**
     * W131: a governed (record 3) COMMITTED transfer whose recorded destination is this real rocket's pre-cargo snapshot,
     * with its ARMED entry only inside the native envelope. C-I recognizes the cargo-changed rocket as its authority.
     */
    public static RocketTransferRecord governed(RocketEntity rocket, RocketStructureSnapshot recorded, UUID ledger) {
        return governed(rocket, recorded, ledger, 10);
    }

    /** Native-cut setup names an authority generation covered by its next checked ledger write. */
    static RocketTransferRecord governed(RocketEntity rocket, RocketStructureSnapshot recorded, UUID ledger, long generation) {
        EscrowTestEndpoints.requireEnabled(rocket.getServer());
        UUID transfer = UUID.randomUUID(), logical = rocket.assemblyTransactionId().orElseThrow(), owner = rocket.ownerId().orElseThrow();
        UUID sourcePhysical = UUID.randomUUID();
        var at = recorded.sourceOrigin(); var dimension = recorded.sourceDimension();
        var source = recorded.relocated(UUID.randomUUID(), dimension, new RocketPosition(at.x() + 8, at.y(), at.z()), 0);
        var fuel = RocketFuelState.empty(recorded.propulsion().orElseThrow())
                .withFluidBank(false, io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("rocket_fuel"), null, 1_000);
        var plan = new RocketFlightPlan(4, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(), dimension, dimension,
                null, 300, 1, new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.BodySurface(RocketFlightPlanner.MOON.bodyId()),
                new RocketPropulsionDebit(300, 0, fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(),
                        RocketNativeResources.hash("W131 governed cargo fixture")));
        var idle = RocketFlightData.initial(logical, recorded.stats().fuelCapacity(), recorded.stats().seatCount(), plan.sourceBody(),
                dimension, source.sourceOrigin(), 0).withFuel(fuel, 0);
        var transit = idle.withPlan(plan).startCountdown(1).completeCountdown(61).beginTransit(transfer, 141);
        var descent = transit.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), dimension, recorded.sourceOrigin(), 161);
        var sourceEnvelope = envelope(sourcePhysical, logical, owner, ledger, source, idle, 7);
        var destinationEnvelope = envelope(rocket.getUUID(), logical, owner, ledger, recorded, descent, 0);
        var arm = new EscrowFlightTables.Armed(ledger, logical, owner, sourcePhysical, 7, transfer, 1, generation, "1".repeat(64), "2".repeat(64),
                EscrowGovernedEnvelope.digest(sourceEnvelope), EscrowGovernedEnvelope.digest(destinationEnvelope), rocket.getUUID(),
                new EscrowGuard(logical, null, 1, "3".repeat(64)), null, 0, EscrowFlightTables.Outcome.ARMED);
        var nativeTransfer = EscrowTransferEnvelope.capture(arm, sourceEnvelope, destinationEnvelope, source, recorded, transit, descent);
        return RocketTransferRecord.createGoverned(transfer, logical, owner, sourcePhysical, source, recorded, transit, descent,
                plan.requiredFuel(), 1, nativeTransfer).destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }

    private static net.minecraft.nbt.CompoundTag envelope(UUID physical, UUID logical, UUID owner, UUID ledger,
                                                         RocketStructureSnapshot snapshot, RocketFlightData flight, long epoch) {
        var t = new net.minecraft.nbt.CompoundTag(); t.putInt("schema_version", 1); t.putUUID("physical", physical);
        t.putString("type", EscrowAdapterIds.ROCKET); t.putUUID("logical", logical); t.putUUID("owner", owner);
        t.putString("level", snapshot.sourceDimension().toString());
        t.putIntArray("chunk", new int[] {snapshot.sourceOrigin().x() >> 4, snapshot.sourceOrigin().z() >> 4});
        t.put("pos", vector(snapshot.sourceOrigin().x() + 0.5, snapshot.sourceOrigin().y(), snapshot.sourceOrigin().z() + 0.5));
        t.putUUID("assembly_snapshot", snapshot.snapshotId()); t.putString("content_hash", snapshot.contentHash());
        t.put("snapshot", io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec.encode(snapshot));
        t.put("flight", io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec.encode(flight));
        t.put("wrapper", new RocketEscrowSection(ledger, epoch, java.util.List.of()).encode()); t.put("motion", vector(0, 0, 0));
        t.putBoolean("no_gravity", true); t.putBoolean("no_physics", true); t.putBoolean("on_ground", false);
        t.putBoolean("invulnerable", false); t.putFloat("yaw", 0); t.putFloat("pitch", 0);
        t.put("passengers", new net.minecraft.nbt.ListTag()); return t;
    }

    private static net.minecraft.nbt.ListTag vector(double x, double y, double z) {
        var list = new net.minecraft.nbt.ListTag(); list.add(net.minecraft.nbt.DoubleTag.valueOf(x));
        list.add(net.minecraft.nbt.DoubleTag.valueOf(y)); list.add(net.minecraft.nbt.DoubleTag.valueOf(z)); return list;
    }
}

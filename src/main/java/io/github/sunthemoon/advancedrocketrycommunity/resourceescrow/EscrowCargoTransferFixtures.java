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
        var at = recorded.sourceOrigin(); var dimension = recorded.sourceDimension();
        var other = recorded.relocated(UUID.randomUUID(), dimension, new RocketPosition(at.x() + 8, at.y(), at.z()), 0);
        var from = source ? recorded : other; var to = source ? other : recorded;
        var transfer = UUID.randomUUID(); var logical = rocket.assemblyTransactionId().orElseThrow();
        var fuel = RocketFuelState.empty(recorded.propulsion().orElseThrow())
                .withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1_000);
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
}

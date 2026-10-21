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
        var flight = RocketFlightData.initial(logical, recorded.stats().fuelCapacity(), 1, plan.sourceBody(), dimension, from.sourceOrigin(), 0)
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

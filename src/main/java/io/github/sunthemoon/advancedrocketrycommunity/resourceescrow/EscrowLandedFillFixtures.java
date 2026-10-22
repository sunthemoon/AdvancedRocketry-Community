package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.ROCKET_FUEL;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Test-only landed fixture shared by R52's GameTests and native process-stop probes. */
final class EscrowLandedFillFixtures {
    private EscrowLandedFillFixtures() { }

    static RocketTransferRecord landed(ServerLevel level, RocketTransferSavedData journal, RocketEntity target,
                                       ResourceLocation oldLevel, RocketPosition oldOrigin) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        var to = target.snapshot().orElseThrow(); var current = target.flightData().orElseThrow();
        var from = to.relocated(UUID.randomUUID(), oldLevel, oldOrigin, 0);
        var sourceBody = current.currentBody().equals(RocketFlightPlanner.EARTH.bodyId())
                ? RocketFlightPlanner.MOON.bodyId() : RocketFlightPlanner.EARTH.bodyId();
        UUID transfer = UUID.randomUUID(), logical = target.assemblyTransactionId().orElseThrow();
        var fuel = current.fuel().withFluidBank(false, ROCKET_FUEL, null, 500);
        var plan = new RocketFlightPlan(3, transfer, sourceBody, current.currentBody(), oldLevel, level.dimension().location(), 500, 0)
                .withPropulsionDebit(new RocketPropulsionDebit(500, 0, fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(),
                        RocketNativeResources.hash("R52 landed fuel fixture")));
        var source = RocketFlightData.initial(logical, from.stats().fuelCapacity(), 1, sourceBody, oldLevel, oldOrigin, 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = source.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(), to.sourceOrigin(), 160);
        var record = RocketTransferRecord.create(transfer, logical, target.ownerId().orElseThrow(), UUID.randomUUID(), from, to, source, arrival, 500, 0)
                .destinationSpawned(target.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        // This fixture really landed. W125's ASSEMBLED controls remain unchanged and still exercise F3 separately.
        target.updateFlightData(arrival.land(level.getGameTime()));
        journal.put(record); journal.flushChecked(level.getServer());
        return record;
    }
}

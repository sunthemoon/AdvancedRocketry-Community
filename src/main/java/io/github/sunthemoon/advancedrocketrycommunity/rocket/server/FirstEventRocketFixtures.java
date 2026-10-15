package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Complete FM9 state fixture: two real DESCENT entities and checked COMMITTED transfer records, no players. */
public final class FirstEventRocketFixtures {
    private FirstEventRocketFixtures() { }

    public static void twoDescents(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        if (FirstEventCutSeams.configured().orElseThrow() != FirstEventCutSeams.Cut.FM9) {
            throw new IllegalStateException("The two-rocket fixture is only available for FM9");
        }
        var moon = java.util.Objects.requireNonNull(server.getLevel(CelestialIds.MOON_LEVEL));
        var journal = RocketTransferSavedData.get(server);
        if (!journal.entries().isEmpty() || !journal.firstMoonLandingRam().isEmpty()) {
            throw new IllegalStateException("FM9 requires a fresh empty journal");
        }
        for (int index = 0; index < 2; index++) {
            BlockPos origin = new BlockPos(320 + 32 * index, 100, 320);
            if (!moon.isPositionEntityTicking(origin)) { throw new IllegalStateException("Fixture chunk is not ticking"); }
            long now = moon.getGameTime();
            var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                    new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())),
                    new RocketBlock(new RocketPosition(0, 1, 0),
                            new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
            var stats = new RocketStats(2, 100, 1_000, 1_000, 1, 1, 1, 0);
            var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 1_000, 0, 1, 1,
                    RocketPropulsion.Settings.DEFAULTS);
            var source = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(),
                    new RocketPosition(320 + index * 32, 100, 320), blocks, List.of(new RocketPosition(0, 1, 0)),
                    stats, now, propulsion);
            var destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(),
                    new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), now);
            UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID(), owner = UUID.randomUUID();
            var fuel = RocketFuelState.empty(propulsion).fill(1_000).state();
            var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(), 500, now);
            var initial = RocketFlightData.initial(logical, 1_000, 1, plan.sourceBody(), plan.sourceDimension(),
                    source.sourceOrigin(), now).withFuel(fuel, now);
            var flight = initial.withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
            var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(),
                    plan.destinationDimension(), destination.sourceOrigin(), now);
            var entity = java.util.Objects.requireNonNull(ModEntities.ROCKET.get().create(moon));
            var record = RocketTransferRecord.create(transfer, logical, owner, UUID.randomUUID(), source, destination,
                    flight, arrival, 500, now).destinationSpawned(entity.getUUID())
                    .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                    .advance(RocketTransferPhase.COMMITTED);
            entity.initializeTransferred(destination, logical, owner, arrival);
            RocketTransferEntities.positionAtAltitude(entity, destination.sourceOrigin());
            if (!moon.addFreshEntity(entity)) { throw new IllegalStateException("Fixture entity was not published"); }
            journal.put(record);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_TEST first_event_fixture descent index={} entity={} transfer={}",
                    index, entity.getUUID(), transfer);
        }
        journal.flushChecked(server);
    }
}

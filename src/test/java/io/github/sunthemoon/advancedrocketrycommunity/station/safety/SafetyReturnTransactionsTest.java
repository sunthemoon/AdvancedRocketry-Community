package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-066 section 3.3: a rocket transfer holds its passengers until it commits; a blocked journal holds everyone. */
final class SafetyReturnTransactionsTest {
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(12, 72, 12);
    private static final RocketPosition MOON_ORIGIN = new RocketPosition(72, 80, 8);

    @Test
    void aPassengerOfAnUncommittedTransferIsHeldUntilTheCommit() {
        UUID passenger = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        RocketTransferRecord prepared = record(passenger);
        assertTrue(SafetyReturnTransactions.inRocketTransfer(true, List.of(prepared), passenger));
        assertFalse(SafetyReturnTransactions.inRocketTransfer(true, List.of(prepared), stranger));
        RocketTransferRecord spawned = prepared.destinationSpawned(UUID.randomUUID());
        for (RocketTransferRecord moving : List.of(spawned,
                spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED),
                spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED))) {
            assertTrue(SafetyReturnTransactions.inRocketTransfer(true, List.of(moving), passenger), moving.phase().name());
        }
        RocketTransferRecord committed = spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        assertFalse(SafetyReturnTransactions.inRocketTransfer(true, List.of(committed), passenger),
                "A committed landed reservation's passengers have arrived");
        assertFalse(SafetyReturnTransactions.inRocketTransfer(true, List.of(), passenger));
    }

    @Test
    void aBlockedJournalHoldsEveryPlayer() {
        assertTrue(SafetyReturnTransactions.inRocketTransfer(false, List.of(), UUID.randomUUID()),
                "Unknown transfers are not an absence of transfers");
    }

    private static RocketTransferRecord record(UUID passenger) {
        UUID transfer = UUID.randomUUID();
        UUID logical = UUID.randomUUID();
        ResourceLocation iron = ResourceLocation.tryParse("minecraft:iron_block");
        List<RocketBlock> blocks = List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 2, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(1, 1, 0), new RocketBlockState(iron, Map.of()))
        );
        RocketStats stats = new RocketStats(4, 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        RocketStructureSnapshot source = RocketStructureSnapshot.create(UUID.randomUUID(),
                RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, blocks, List.of(new RocketPosition(1, 1, 0)),
                stats, 0L);
        RocketStructureSnapshot destination = source.relocated(UUID.randomUUID(),
                RocketFlightPlanner.MOON.dimensionId(), MOON_ORIGIN, 160L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        RocketFlightPlan plan = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON, transfer, 0L).plan();
        RocketFlightData sourceFlight = RocketFlightData.initial(logical, stats.fuelCapacity(), stats.seatCount(),
                        RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(), EARTH_ORIGIN, 0L)
                .withFuel(fuel, 0L)
                .withPassengers(RocketPassengerManifest.empty(1).assign(passenger).orElseThrow())
                .withPlan(plan)
                .startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                sourceFlight.fuel().debit(transfer, plan.requiredFuel()).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), MOON_ORIGIN, RocketFlightLimits.COUNTDOWN_TICKS
                        + RocketFlightLimits.ASCENT_TICKS + RocketFlightLimits.TRANSIT_TICKS);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), source,
                destination, sourceFlight, destinationFlight, plan.requiredFuel(), 0L);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * ADR-044 §5 in-motion rule, journal only. The rule is generic over the Level; these records fly
 * Earth -> Moon, so "station" regions are placed around their source and destination pads.
 */
final class RocketStationMotionRuleTest {
    private static final RocketPosition EARTH_ORIGIN = new RocketPosition(12, 72, 12);
    private static final RocketPosition MOON_ORIGIN = new RocketPosition(72, 80, 8);
    private static final long ARRIVAL = RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS
            + RocketFlightLimits.TRANSIT_TICKS;
    /** Around the Moon pad (the rocket spans x 72..73, z 8). */
    private static final RocketStationMotionRule.Region AT_DESTINATION = new RocketStationMotionRule.Region(
            RocketFlightPlanner.MOON.dimensionId(), 0, 0, 511, 511);
    /** Around the Earth pad the rocket departs from. */
    private static final RocketStationMotionRule.Region AT_SOURCE = new RocketStationMotionRule.Region(
            RocketFlightPlanner.EARTH.dimensionId(), -100, -100, 100, 100);
    private static final RocketStationMotionRule.Region ELSEWHERE = new RocketStationMotionRule.Region(
            RocketFlightPlanner.MOON.dimensionId(), 1_000, 1_000, 1_511, 1_511);

    @Test
    void anUnavailableJournalOrAnUnclassifiedRecordBlocksEveryRegion() {
        assertTrue(blocks(false, List.of(), Set.of(), Set.of(), 0, ELSEWHERE));
        assertFalse(blocks(true, List.of(), Set.of(), Set.of(), 0, ELSEWHERE));
        RocketTransferRecord far = record();
        assertTrue(blocks(true, List.of(far), Set.of(), Set.of(), 0, ELSEWHERE),
                "Recovery has not classified every record yet");
        assertFalse(blocks(true, List.of(committed(far)), Set.of(far.transferId()), Set.of(), ARRIVAL + 1_000,
                ELSEWHERE));
    }

    @Test
    void preparedRecordsBlockOnlyWhileLive() {
        RocketTransferRecord prepared = record();
        UUID id = prepared.transferId();
        assertTrue(blocks(true, List.of(prepared), Set.of(id), Set.of(id), 0, AT_SOURCE), "A live launch moves");
        assertTrue(blocks(true, List.of(prepared), Set.of(id), Set.of(id), 0, AT_DESTINATION), "An inbound rocket");
        assertFalse(blocks(true, List.of(prepared), Set.of(id), Set.of(), 0, AT_SOURCE),
                "A source returned by recovery (waiting for passengers) is stationary");
        assertFalse(blocks(true, List.of(prepared), Set.of(id), Set.of(id), 0, ELSEWHERE));
    }

    @Test
    void recordsBetweenSpawnAndCommitAlwaysBlock() {
        RocketTransferRecord spawned = record().destinationSpawned(UUID.randomUUID());
        for (RocketTransferRecord moving : List.of(spawned,
                spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED),
                spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED))) {
            UUID id = moving.transferId();
            for (Set<UUID> live : List.of(Set.of(id), Set.<UUID>of())) {
                assertTrue(blocks(true, List.of(moving), Set.of(id), live, ARRIVAL + 10_000, AT_DESTINATION),
                        moving.phase() + " at the destination");
                assertTrue(blocks(true, List.of(moving), Set.of(id), live, ARRIVAL + 10_000, AT_SOURCE),
                        moving.phase() + " at the source");
            }
            assertFalse(blocks(true, List.of(moving), Set.of(id), Set.of(id), 0, ELSEWHERE));
        }
    }

    @Test
    void aCommittedArrivalBlocksUntilItHasLandedWithMargin() {
        RocketTransferRecord committed = committed(record());
        UUID id = committed.transferId();
        long landed = committed.destinationFlightData().stateStartedGameTime() + RocketFlightLimits.DESCENT_TICKS
                + RocketStationMotionRule.LANDED_MARGIN_TICKS;
        assertTrue(blocks(true, List.of(committed), Set.of(id), Set.of(id), landed - 1, AT_DESTINATION));
        assertFalse(blocks(true, List.of(committed), Set.of(id), Set.of(id), landed, AT_DESTINATION),
                "A landed reservation does not block, even while it waits for passengers");
        assertFalse(blocks(true, List.of(committed), Set.of(id), Set.of(), landed, AT_DESTINATION));
        assertFalse(blocks(true, List.of(committed), Set.of(id), Set.of(id), landed - 1, ELSEWHERE));
    }

    @Test
    void regionOverlapIsInclusiveAndPerLevel() {
        RocketStructureSnapshot destination = record().destinationSnapshot();
        ResourceLocation moon = RocketFlightPlanner.MOON.dimensionId();
        assertTrue(new RocketStationMotionRule.Region(moon, 73, 8, 80, 8).overlaps(destination), "Touching edge");
        assertTrue(new RocketStationMotionRule.Region(moon, 60, 0, 72, 8).overlaps(destination), "Touching edge");
        assertFalse(new RocketStationMotionRule.Region(moon, 74, 8, 80, 8).overlaps(destination));
        assertFalse(new RocketStationMotionRule.Region(moon, 60, 9, 80, 20).overlaps(destination));
        assertFalse(new RocketStationMotionRule.Region(RocketFlightPlanner.EARTH.dimensionId(), 0, 0, 511, 511)
                .overlaps(destination), "Another Level never overlaps");
        assertThrows(IllegalArgumentException.class, () -> new RocketStationMotionRule.Region(moon, 5, 0, 4, 0));
    }

    private static boolean blocks(boolean operational, List<RocketTransferRecord> records, Set<UUID> classified,
                                  Set<UUID> live, long gameTime, RocketStationMotionRule.Region region) {
        return RocketStationMotionRule.blocks(operational, records, classified::contains, live::contains, gameTime,
                region);
    }

    private static RocketTransferRecord committed(RocketTransferRecord prepared) {
        return prepared.destinationSpawned(UUID.randomUUID())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
    }

    private static RocketTransferRecord record() {
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
                .withPassengers(RocketPassengerManifest.empty(1))
                .withPlan(plan)
                .startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        RocketFlightData destinationFlight = sourceFlight.arriveAtDestination(
                sourceFlight.fuel().debit(transfer, plan.requiredFuel()).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), MOON_ORIGIN, ARRIVAL);
        return RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), source,
                destination, sourceFlight, destinationFlight, plan.requiredFuel(), 0L);
    }
}

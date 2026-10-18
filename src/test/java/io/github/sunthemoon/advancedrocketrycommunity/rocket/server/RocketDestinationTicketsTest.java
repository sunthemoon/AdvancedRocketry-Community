package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W83b (Root decision R32 item 2): the destination flight-ticket set, its release and the read-only column count. */
class RocketDestinationTicketsTest {
    private static final ResourceLocation EARTH = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static RocketStructureSnapshot snapshot(ResourceLocation level, int x, int z, int... relativeX) {
        return RocketMarginTicketsTest.snapshot(level, x, z, relativeX);
    }

    @Test void aBorderCrossingDestinationTicketsItsWholeCheckedSetInItsOwnLevelAndReleaseRemovesExactlyThat() {
        var transfer = UUID.randomUUID(); var tickets = new RocketMarginTicketsTest.Tickets(EARTH, MOON);
        // Blocks at x 31..32 cross the border between columns 1 and 2; the checked set is 1..2 x 1..2.
        var source = snapshot(EARTH, 8, 8, 0); var destination = snapshot(MOON, 31, 31, 0, 1);
        assertEquals(4, RocketTransferEntities.addDestinationTickets(transfer, destination, tickets));
        var expected = Set.of(new ChunkPos(1, 1), new ChunkPos(2, 1), new ChunkPos(1, 2), new ChunkPos(2, 2));
        assertEquals(expected, tickets.columns(MOON, transfer));
        assertTrue(tickets.columns(EARTH, transfer).isEmpty(), "A destination ticket went to the source Level");
        assertEquals(expected, new HashSet<>(RocketDiscoveryColumns.of(destination).columns()), "Not the checked column set");
        RocketTransferEntities.releaseMargins(transfer, source, destination, tickets);
        assertEquals(0, tickets.count(transfer), "Release left a destination ticket");
    }

    @Test void theAddedSetIsTheDestinationReleaseSetForAdmissibleAndInadmissibleEnds() {
        var random = new Random(8_332L);
        var destinations = new ArrayList<RocketStructureSnapshot>();
        for (int i = 0; i < 2_000; i++) {
            int x = random.nextInt(4_000) - 2_000, z = random.nextInt(4_000) - 2_000, width = 1 + random.nextInt(i % 5 == 0 ? 300 : 60);
            destinations.add(width == 1 ? snapshot(MOON, x, z, 0) : snapshot(MOON, x, z, 0, width - 1));
        }
        destinations.add(snapshot(MOON, 15, 8, 5, 6)); // origin outside its own checked set
        int admissible = 0, originOnly = 0;
        for (var destination : destinations) {
            var transfer = UUID.randomUUID(); var tickets = new RocketMarginTicketsTest.Tickets(EARTH, MOON);
            int added = RocketTransferEntities.addDestinationTickets(transfer, destination, tickets);
            var released = new ArrayList<ChunkPos>();
            var recording = new RocketMarginTicketsTest.Tickets(EARTH, MOON) {
                @Override public void remove(ResourceLocation dimension, ChunkPos column, UUID key) {
                    if (dimension.equals(MOON)) { released.add(column); }
                }
            };
            RocketTransferEntities.releaseMargins(transfer, snapshot(EARTH, 8, 8, 0), destination, recording);
            assertEquals(new HashSet<>(released), tickets.columns(MOON, transfer), destination.sourceOrigin().toString());
            assertEquals(added, released.size());
            assertTrue(added >= 1 && added <= RocketFlightLimits.MAX_LANDING_CHUNKS, "Outside the 16-column budget: " + added);
            var origin = new ChunkPos(destination.sourceOrigin().x() >> 4, destination.sourceOrigin().z() >> 4);
            assertTrue(tickets.columns(MOON, transfer).contains(origin), "The origin column lost its ticket");
            if (RocketDiscoveryColumns.of(destination).admissible()) { admissible++; } else {
                assertEquals(Set.of(origin), tickets.columns(MOON, transfer), "An inadmissible destination changed today's ticket");
                originOnly++;
            }
        }
        assertTrue(admissible > 1_000 && originOnly > 10, admissible + " admissible, " + originOnly + " origin only");
    }

    @Test void aOneColumnDestinationKeepsTodaysSingleOriginTicketAndRefreshesAreIdempotent() {
        var transfer = UUID.randomUUID(); var tickets = new RocketMarginTicketsTest.Tickets(EARTH, MOON);
        var destination = snapshot(MOON, 8, 8, 0);
        for (int visit = 0; visit < 5; visit++) { assertEquals(1, RocketTransferEntities.addDestinationTickets(transfer, destination, tickets)); }
        assertEquals(Set.of(new ChunkPos(0, 0)), tickets.columns(MOON, transfer));
        assertEquals(1, tickets.count(transfer));
    }

    @Test void anAbsentDestinationLevelGetsNoCall() {
        var tickets = new RocketMarginTicketsTest.Tickets(EARTH);
        assertEquals(0, RocketTransferEntities.addDestinationTickets(UUID.randomUUID(), snapshot(MOON, 31, 31, 0, 1), tickets));
        assertEquals(0, tickets.adds);
    }

    @Test void sixtyFourLargestDestinationsHoldExactlyOneThousandTwentyFourTickets() {
        var tickets = new RocketMarginTicketsTest.Tickets(MOON);
        var transfers = new ArrayList<UUID>();
        for (int i = 0; i < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; i++) {
            // The largest admissible set: blocks x 0..245 from origin x 8 span columns 0..15 in one row.
            var destination = snapshot(MOON, 8 + i * 4_096, 8, 0, 245);
            var transfer = UUID.randomUUID(); transfers.add(transfer);
            assertEquals(RocketFlightLimits.MAX_LANDING_CHUNKS, RocketTransferEntities.addDestinationTickets(transfer, destination, tickets));
        }
        assertEquals(1_024, transfers.stream().mapToInt(tickets::count).sum());
        assertFalse(RocketDiscoveryColumns.of(snapshot(MOON, 8, 8, 0, 246)).admissible(), "A 17th column was admissible");
        assertEquals(1, RocketTransferEntities.addDestinationTickets(UUID.randomUUID(), snapshot(MOON, 8, 8, 0, 246),
                new RocketMarginTicketsTest.Tickets(MOON)), "An over-budget destination did not keep today's origin ticket");
    }

    @Test void aGovernedDestinationsMarginRefreshEqualsItsTicketSet() {
        var transfer = UUID.randomUUID(); var margins = new RocketMarginTicketsTest.Tickets(EARTH, MOON);
        var source = snapshot(EARTH, 15, 15, 0); var destination = snapshot(MOON, 31, 31, 0, 1);
        RocketTransferEntities.refreshMargins(transfer, source, destination, margins);
        assertEquals(new HashSet<>(RocketTransferEntities.destinationTicketSet(destination)), margins.columns(MOON, transfer),
                "The F2 callback and W90a's refresh ticket different destination columns");
    }

    @Test void theEntityColumnCountReadsEachColumnOnceAndNothingElse() {
        var columns = RocketTransferEntities.destinationTicketSet(snapshot(MOON, 31, 31, 0, 1));
        var asked = new ArrayList<Long>();
        int loaded = RocketTransferEntities.entitiesLoadedColumns(columns, key -> { asked.add(key); return key != new ChunkPos(2, 2).toLong(); });
        assertEquals(3, loaded);
        assertEquals(columns.stream().map(ChunkPos::toLong).toList(), asked);
        assertEquals(4, RocketTransferEntities.entitiesLoadedColumns(columns, key -> true));
        assertEquals(0, RocketTransferEntities.entitiesLoadedColumns(List.of(), key -> { throw new AssertionError("read"); }));
    }
}

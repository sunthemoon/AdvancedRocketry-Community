package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.TRANSFER;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.record;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W170-R65 under Root decision R150: the low-edge regression, moved from the shared set to the bay's own census. A
 * 1.5-block-wide image of L that meets the census box's low edge has its home column in the bay's absence census, its
 * end-loading wait and both claim renewals; the shared checked set that every flight uses stays at {@code min - 1}.
 */
final class BayCensusColumnsTest {
    private static final ResourceLocation EARTH = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyLowEdgeIntersectingImageIsInTheBayCensusItsWaitAndBothRenewals() {
        for (int origin : new int[] {-31, -15, 1, 17, 33}) { // origin - 1 starts a column in x and in z
            var source = RocketMarginTicketsTest.snapshot(EARTH, origin, origin, 0);
            var destination = RocketMarginTicketsTest.snapshot(MOON, origin, origin, 0);
            var shared = RocketDiscoveryColumns.of(source);
            assertEquals(new ChunkPos((origin - 1) >> 4, (origin - 1) >> 4), new ChunkPos(shared.minColumnX(), shared.minColumnZ()),
                    "the shared set keeps its low edge at min - 1");
            var bay = BayCensusColumns.of(source);
            assertTrue(bay.admissible(), "control: " + bay.columns());
            var slots = BayAbsenceCensus.slots(source, destination);
            var restart = new RocketMarginTicketsTest.Tickets(EARTH, MOON);
            BayCensusColumns.renewBoth(TRANSFER, source, destination, restart);
            var inSession = new RocketMarginTicketsTest.Tickets(EARTH, MOON);
            BayCensusColumns.renewEach(TRANSFER, List.of(source, destination), inSession);
            var census = new AABB(origin - 1D, -64, origin - 1D, origin + 2D, 320, origin + 2D);
            for (double x : new double[] {origin - 1.74, origin - 1.25, origin + .5}) {
                for (double z : new double[] {origin - 1.74, origin - 1.25, origin + .5}) {
                    var image = new AABB(x - .75, 80, z - .75, x + .75, 84, z + .75);
                    assertTrue(census.intersects(image), "control: the image intersects the actual census box");
                    var home = new ChunkPos((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
                    assertEquals(x > origin - 1 && z > origin - 1, shared.columns().contains(home),
                            "control: only the strip lies outside the shared set, at " + home);
                    assertTrue(bay.columns().contains(home), "the bay census missed " + home + " from " + origin);
                    assertTrue(slots.contains(new RocketRetirementProof.Slot(EARTH, home)), "D5 and D6 must prove " + home);
                    assertFalse(bay.ready((cx, cz) -> !home.equals(new ChunkPos(cx, cz))), "D6 and R2 must wait for " + home);
                    assertTrue(restart.columns(EARTH, TRANSFER).contains(home), "R1 must ticket " + home);
                    assertTrue(inSession.columns(EARTH, TRANSFER).contains(home), "the in-session renewal must ticket " + home);
                }
            }
            // The flight's own release removes the shared columns only; the strip's tickets end with the ticket timeout.
            RocketTransferEntities.releaseMargins(TRANSFER, source, destination, restart);
            assertEquals(new HashSet<>(bay.strip()), restart.columns(EARTH, TRANSFER));
            assertEquals(new HashSet<>(BayCensusColumns.of(destination).strip()), restart.columns(MOON, TRANSFER));
        }
    }

    @Test void anEndWithoutAStripIsExactlyTheSharedSet() {
        var record = record(1);
        for (var end : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
            assertEquals(RocketDiscoveryColumns.of(end).columns(), BayCensusColumns.of(end).columns());
            assertTrue(BayCensusColumns.of(end).strip().isEmpty());
        }
    }

    @Test void aStripThatWouldPassSixteenColumnsHoldsWithNoTicketAndNoTruncatedCensus() {
        // 1 x 16 shared columns from x = 1 (blocks 0..252, checked 0..255); x - 2 = -1 would make it 17.
        var line = RocketMarginTicketsTest.snapshot(EARTH, 1, 8, IntStream.rangeClosed(0, 252).toArray());
        var other = RocketMarginTicketsTest.snapshot(MOON, 72, 8, 0);
        assertTrue(RocketDiscoveryColumns.of(line).admissible() && RocketDiscoveryColumns.of(line).count() == 16, "control");
        var bay = BayCensusColumns.of(line);
        assertFalse(bay.valid()); assertFalse(bay.admissible()); assertTrue(bay.columns().isEmpty());
        assertFalse(bay.ready((x, z) -> true), "an over-budget end is never ready");
        assertNull(BayAbsenceCensus.slots(line, other), "a hold, never a truncated census");
        var tickets = new RocketMarginTicketsTest.Tickets(EARTH, MOON);
        assertEquals(0, BayCensusColumns.renewBoth(TRANSFER, line, other, tickets));
        assertEquals(1, BayCensusColumns.renewEach(TRANSFER, List.of(line, other), tickets), "only the other end");
        assertTrue(tickets.columns(EARTH, TRANSFER).isEmpty());
        // The same 16 columns one block east have no strip and stay valid: the bound is the shared one, unchanged.
        var east = RocketMarginTicketsTest.snapshot(EARTH, 2, 8, IntStream.rangeClosed(0, 251).toArray());
        assertTrue(BayCensusColumns.of(east).admissible() && BayCensusColumns.of(east).count() == 16);
    }

    /** W185-R25: D0 refuses every bay, with an existing reason, when either end has no bay census; else it passes. */
    @Test void d0RefusesAFlightWithAnEndThatTheStripTakesPastSixteenColumns() {
        var line = RocketMarginTicketsTest.snapshot(EARTH, 1, 8, IntStream.rangeClosed(0, 252).toArray());
        var moonLine = RocketMarginTicketsTest.snapshot(MOON, 1, 8, IntStream.rangeClosed(0, 252).toArray());
        var east = RocketMarginTicketsTest.snapshot(EARTH, 2, 8, IntStream.rangeClosed(0, 251).toArray());
        var other = RocketMarginTicketsTest.snapshot(MOON, 72, 8, 0);
        assertEquals(java.util.Optional.of("DEPLOYMENT_UNAVAILABLE"), BayDeploymentChecks.censusRefusal(line, other), "the source end");
        assertEquals(java.util.Optional.of("DEPLOYMENT_UNAVAILABLE"), BayDeploymentChecks.censusRefusal(east, moonLine), "the destination end");
        assertEquals(java.util.Optional.empty(), BayDeploymentChecks.censusRefusal(east, other), "16 columns with no strip pass");
        assertEquals(java.util.Optional.empty(), BayDeploymentChecks.censusRefusal(record(1)), "the ordinary fixture passes");
    }

    @Test void overflowAtTheStripIsAHold() {
        var edge = RocketMarginTicketsTest.snapshot(EARTH, Integer.MIN_VALUE + 1, 8, 0);
        assertTrue(RocketDiscoveryColumns.of(edge).valid(), "control: the shared set is still valid");
        assertFalse(BayCensusColumns.of(edge).valid());
        assertNull(BayAbsenceCensus.slots(edge, RocketMarginTicketsTest.snapshot(MOON, 72, 8, 0)));
    }
}

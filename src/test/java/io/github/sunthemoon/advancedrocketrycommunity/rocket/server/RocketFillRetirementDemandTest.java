package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W130F (Root decision R59, W130-R1): the W130 review's probe {@code W130ReviewTicketProbeTest} as a regression test.
 * One loader demand, then none, gives a bounded number of ticket steps in 12,000 ticks: none for the three holds that no
 * ticket can end, and {@link RocketFillRetirementPacing#DEMAND_TICKETS} for a hold found after the ticket step, after which
 * the entry ends with T kept and its tickets released. The probe measured 61 ticket steps for each of the three holds.
 */
class RocketFillRetirementDemandTest {
    private static final int TICKS = 12_000;
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void anOrdinaryFootprintOverTheDiscoveryLimitPlacesNoTicket() {
        // The review's example: bounds 0..46 by 0..31 at an aligned origin check 5 by 4 = 20 columns, over the 16 allowed.
        var f = new Fixture(wide(new RocketPosition(64, 80, 0)));
        assertFalse(RocketDiscoveryColumns.of(f.record.destinationSnapshot()).admissible());
        f.begin();
        assertEquals(List.of(), f.port.order, "A discovery-bounds hold must precede the ticket and the slot checks");
        f.run(TICKS);
        assertEquals(0, f.port.tickets);
        assertEquals("DISCOVERY_BOUNDS", f.retirements.reason(f.logical()));
        f.assertKept();
    }

    @Test void unsupportedStoragePlacesNoTicket() {
        var f = new Fixture(record()); f.port.supported = false; f.begin(); f.run(TICKS);
        assertEquals(0, f.port.tickets);
        assertEquals("UNSUPPORTED_ENTITY_STORAGE", f.retirements.reason(f.logical()));
        f.assertKept();
    }

    @Test void aSaveOffLevelPlacesNoTicketEvenWhileTheOldSiteIsUnloaded() {
        // Only the Level's flag, read without loading, can see the save-off of an unloaded old site.
        var f = new Fixture(record()); f.port.saveOff = true; f.port.loaded = false; f.begin(); f.run(TICKS);
        assertEquals(0, f.port.tickets);
        assertEquals("SAVE_OFF", f.retirements.reason(f.logical()));
        f.assertKept();
    }

    @Test void aProjectionConflictPlacesNoTicket() {
        var f = new Fixture(record()); f.port.projectionMissing = true; f.begin(); f.run(TICKS);
        assertEquals(0, f.port.tickets, "A survivor conflict before the ticket step places no ticket");
        f.assertKept();
    }

    @Test void aHoldFoundAfterTheTicketStopsAtTheDemandsTicketStepsUntilANewDemand() {
        var f = new Fixture(record()); f.port.scanReason = "DISCARD_HELD"; f.begin();
        assertEquals(List.of("ticket", "slot", "slot", "prepare"), f.port.order);
        f.run(TICKS);
        assertEquals(RocketFillRetirementPacing.DEMAND_TICKETS, f.port.tickets, "One demand allows a fixed number of ticket steps");
        assertEquals(List.of(100L, 300L, 500L), f.port.ticketTicks, "The steps keep R52's 200-tick spacing");
        assertFalse(f.retirements.pending(f.logical()), "The spent entry kept running");
        assertEquals(1, f.port.released, "The spent entry kept its tickets");
        f.assertKept();
        f.begin(); f.run(TICKS);
        assertEquals(2 * RocketFillRetirementPacing.DEMAND_TICKETS, f.port.tickets, "A new demand allows the same number again");
        assertEquals(2, f.port.released);
        f.assertKept();
    }

    @Test void aNewDemandRightAfterTheLastStepKeepsTheRocketsSpacing() {
        var f = new Fixture(record()); f.port.scanReason = "DISCARD_HELD"; f.begin();
        f.run(400);
        assertFalse(f.retirements.pending(f.logical()));
        for (int tick = 0; tick < 199; tick++) { f.begin(); f.run(1); }
        assertEquals(RocketFillRetirementPacing.DEMAND_TICKETS, f.port.tickets, "A demand reset the 200-tick spacing");
        f.begin(); f.run(1);
        assertEquals(RocketFillRetirementPacing.DEMAND_TICKETS + 1, f.port.tickets);
        assertEquals(700L, f.port.ticketTicks.get(RocketFillRetirementPacing.DEMAND_TICKETS));
    }

    @Test void theEntrysOwnTicketsCannotKeepItGoing() {
        // D is loaded by the demanding player only at the demand, then only by the entry's own ticket refresh.
        var f = new Fixture(record()); f.port.scanReason = "DISCARD_HELD"; f.begin(); f.port.external = false;
        f.run(TICKS);
        assertEquals(RocketFillRetirementPacing.DEMAND_TICKETS, f.port.tickets);
        assertFalse(f.port.ticketed, "The entry left its own tickets behind");
        // Without its own tickets the rocket is no longer loaded, so a later demand places none.
        f.begin(); f.run(TICKS);
        assertEquals(RocketFillRetirementPacing.DEMAND_TICKETS, f.port.tickets);
        f.assertKept();
    }

    private static RocketTransferRecord record() { return committed(RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8))); }

    /** A landed ordinary transfer record T, as the probe's. */
    private static RocketTransferRecord committed(RocketTransferRecord prepared) {
        return prepared.destinationSpawned(UUID.randomUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }

    /** An ordinary rocket whose footprint spans 0..46 by 0..31, landed at {@code origin}. */
    private static RocketTransferRecord wide(RocketPosition origin) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var blocks = new ArrayList<RocketBlock>();
        for (var at : List.of(new RocketPosition(0, 0, 0), new RocketPosition(0, 1, 0), new RocketPosition(0, 2, 0),
                new RocketPosition(1, 1, 0), new RocketPosition(46, 0, 31))) { blocks.add(new RocketBlock(at, iron)); }
        var earthOrigin = new RocketPosition(12, 72, 12);
        long arrival = RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS + RocketFlightLimits.TRANSIT_TICKS;
        UUID transfer = UUID.randomUUID(), logical = UUID.randomUUID();
        var stats = new RocketStats(blocks.size(), 200L, 1_000L, 1_000L, 1, 1, 1, 0);
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(), earthOrigin, blocks,
                List.of(new RocketPosition(1, 1, 0)), stats, 0L);
        var destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(), origin, 160L);
        var fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        var plan = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, transfer, 0L).plan();
        var sourceFlight = RocketFlightData.initial(logical, stats.fuelCapacity(), stats.seatCount(), RocketFlightPlanner.EARTH.bodyId(),
                        RocketFlightPlanner.EARTH.dimensionId(), earthOrigin, 0L)
                .withFuel(fuel, 0L).withPassengers(RocketPassengerManifest.empty(1)).withPlan(plan).startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        var destinationFlight = sourceFlight.arriveAtDestination(sourceFlight.fuel().debit(transfer, plan.requiredFuel()).state(),
                RocketFlightPlanner.MOON.bodyId(), RocketFlightPlanner.MOON.dimensionId(), origin, arrival);
        return committed(RocketTransferRecord.create(transfer, logical, UUID.randomUUID(),
                UUID.randomUUID(), source, destination, sourceFlight, destinationFlight, plan.requiredFuel(), 0L));
    }

    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferRecord record;
        final Port port = new Port();
        final RocketOrdinaryRetirement retirements = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), HashSet::new, server -> port);
        Fixture(RocketTransferRecord record) {
            this.record = record;
            journal.put(record);
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            RocketJournalTestAccess.write(journal, directory.resolve(type.fileName()), CheckedSavedDataPolicy.atomic(type), 1000);
        }
        UUID logical() { return record.logicalRocketId(); }
        /** One loader demand, as the recovery service begins it. */
        void begin() {
            try (var held = RocketOrdinaryRetirement.hold(journal, record.logicalRocketId())) {
                retirements.begin(port, journal, RocketOrdinaryRetirement.Site.BEFORE_FILL, record,
                        record.destinationEntityId().orElseThrow(), List.of(), false, held, RocketOrdinaryRetirement.Effects.of(() -> true));
            }
        }
        void run(int ticks) { for (int tick = 0; tick < ticks; tick++) { port.now++; retirements.tick(); } }
        void assertKept() {
            assertSame(record, journal.find(record.transferId()).orElseThrow(), "T changed without proof");
            assertTrue(port.captures.isEmpty(), "A held retirement reached capture");
            assertTrue(journal.clean(), "A held retirement kept a reservation");
        }
    }

    private static final class Port implements RocketOrdinaryRetirement.Port {
        long now = 100;
        int tickets, released;
        boolean supported = true, saveOff, projectionMissing, loaded = true, external = true, ticketed;
        String scanReason;
        final List<String> order = new ArrayList<>();
        final List<Long> ticketTicks = new ArrayList<>();
        final List<RocketRetirementProof.Slot> captures = new ArrayList<>();
        public long now() { return now; }
        public RocketOrdinaryRetirement.Presence survivor(RocketTransferRecord r, UUID id, RocketRetirementProof.Slot slot) {
            return external || ticketed ? RocketOrdinaryRetirement.Presence.LOADED : RocketOrdinaryRetirement.Presence.UNLOADED;
        }
        public boolean crewReady(RocketTransferRecord r, UUID id, boolean offline, Set<UUID> delivered) { return true; }
        public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
            order.add("slot");
            if (!loaded) { return RocketOrdinaryRetirement.SlotState.UNLOADED; }
            return saveOff ? RocketOrdinaryRetirement.SlotState.SAVE_OFF : RocketOrdinaryRetirement.SlotState.LOADED;
        }
        @Override public boolean saveOff(RocketRetirementProof.Slot slot) { return saveOff; }
        public boolean captureSupported(RocketRetirementProof.Slot slot) { return supported; }
        public boolean captureAvailable(RocketRetirementProof.Slot slot, long tick) { return true; }
        public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long tick, Consumer<String> sink,
                               Consumer<EscrowBarrier.Result> callback) { captures.add(slot); return true; }
        public void retryNextTick(Runnable retry) { fail("A destination retry bypassed its demand pacing"); }
        public boolean bayAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferBayRecoveryGate.Action action) { return true; }
        public boolean bayClaimed(UUID id) { return false; }
        public boolean escrowAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferSavedData.Reservation own) {
            return own == null ? j.actionable(r.logicalRocketId()) : j.actionable(r.logicalRocketId(), own);
        }
        public CompoundTag projection(RocketTransferRecord r, UUID survivor) {
            if (projectionMissing || !external && !ticketed) { return null; }
            var tag = new CompoundTag(); tag.putUUID("UUID", survivor); return tag;
        }
        public void keepLoaded(RocketTransferRecord r, List<RocketRetirementProof.Slot> slots) {
            tickets++; ticketTicks.add(now); ticketed = true; order.add("ticket");
        }
        @Override public String prepare(RocketTransferRecord r, UUID survivor, List<RocketRetirementProof.Slot> slots) {
            order.add("prepare"); return scanReason;
        }
        @Override public void releaseTickets(RocketTransferRecord r) { released++; ticketed = false; }
        public Consumer<String> sink() { return label -> { }; }
        public boolean afterAcknowledged(RocketTransferSavedData j, RocketTransferSavedData.Reservation token, BooleanSupplier effect, Runnable discarded) {
            return RocketJournalTestAccess.queue(j, token, effect, discarded);
        }
    }
}

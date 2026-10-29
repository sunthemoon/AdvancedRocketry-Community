package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayAbsenceCensusTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void theSurvivorComesFirstAndBothEndMarginsIncludeTheLowStrip() {
        var record = relocate(17, 17, 65, 65);
        var slots = BayAbsenceCensus.slots(record);
        assertNotNull(slots); assertEquals(RocketRetirementProof.Slot.origin(record.sourceSnapshot()), slots.get(0));
        assertEquals(8, slots.size()); assertEquals(slots.size(), new HashSet<>(slots).size());
        assertTrue(slots.contains(new RocketRetirementProof.Slot(record.sourceSnapshot().sourceDimension(), new ChunkPos(0, 0))));
        assertTrue(slots.contains(new RocketRetirementProof.Slot(record.destinationSnapshot().sourceDimension(), new ChunkPos(3, 3))));
    }

    @Test void anUnticketedColdMarginCannotCountAsAbsentAndSaveOffAlsoWaits() {
        var record = relocate(17, 17, 65, 65);
        var slots = BayAbsenceCensus.slots(record); var port = new Port();
        port.loaded.add(slots.get(0));
        assertEquals("END_LOADING", BayAbsenceCensus.waiting(port, slots));
        port.loaded.addAll(slots); assertNull(BayAbsenceCensus.waiting(port, slots));
        port.saveOff = true; assertEquals("SAVE_OFF", BayAbsenceCensus.waiting(port, slots));
        port.saveOff = false; port.supported = false;
        assertEquals("BARRIER_UNSUPPORTED", BayAbsenceCensus.waiting(port, slots));
    }

    @Test void arithmeticOverflowIsAHoldNotAnEmptyCensus() {
        assertNull(BayAbsenceCensus.slots(relocate(Integer.MIN_VALUE, 8, 72, 8)));
    }

    @Test void validRectanglesOutsideTheirOriginsStillProveBothRecordedOriginSlots() {
        var old = record(1);
        var source = RocketMarginTicketsTest.snapshot(old.sourceSnapshot().sourceDimension(), 15, 8, 5, 6);
        var destination = RocketMarginTicketsTest.snapshot(old.destinationSnapshot().sourceDimension(), 79, 8, 5, 6);
        var slots = BayAbsenceCensus.slots(source, destination);
        assertEquals(4, slots.size());
        assertEquals(RocketRetirementProof.Slot.origin(source), slots.get(0));
        assertTrue(slots.contains(RocketRetirementProof.Slot.origin(destination)));
    }

    @Test void overlappingEndsShareTheSameSlotAndNeverRequireAnAbsenceProofForTheSurvivorSlot() {
        var old = record(1); var source = old.sourceSnapshot();
        var destination = old.destinationSnapshot().relocated(old.destinationSnapshot().snapshotId(), source.sourceDimension(), source.sourceOrigin(), 0);
        var slots = BayAbsenceCensus.slots(source, destination);
        assertEquals(1, slots.size()); assertEquals(RocketRetirementProof.Slot.origin(source), slots.get(0));
    }

    static RocketTransferRecord relocate(int sx, int sz, int dx, int dz) { return relocate(sx, 80, sz, dx, 80, dz); }

    static RocketTransferRecord relocate(int sx, int sy, int sz, int dx, int dy, int dz) {
        var old = record(1);
        var source = old.sourceSnapshot().relocated(old.sourceSnapshot().snapshotId(), old.sourceSnapshot().sourceDimension(),
                new RocketPosition(sx, sy, sz), 0);
        var destination = old.destinationSnapshot().relocated(old.destinationSnapshot().snapshotId(), old.destinationSnapshot().sourceDimension(),
                new RocketPosition(dx, dy, dz), 0);
        return RocketTransferRecord.create(TRANSFER, LOGICAL, OWNER, SOURCE, source, destination,
                at(old.sourceFlightData(), source), at(old.destinationFlightData(), destination), old.requiredFuel(), 0);
    }

    private static RocketFlightData at(RocketFlightData flight, RocketStructureSnapshot snapshot) {
        return RocketFlightData.restore(flight.schemaVersion(), flight.logicalRocketId(), flight.state(), flight.fuel(),
                flight.plan().orElseThrow(), flight.passengers(), flight.currentBody(), snapshot.sourceDimension(),
                snapshot.sourceOrigin(), flight.stateStartedGameTime(), flight.activeTransferId().orElseThrow(),
                flight.currentTarget().orElse(null));
    }

    private static final class Port extends RocketRetirementWorld {
        final Set<RocketRetirementProof.Slot> loaded = new HashSet<>();
        boolean saveOff, supported = true;
        Port() { super(null, null); }
        @Override public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
            return !loaded.contains(slot) ? RocketOrdinaryRetirement.SlotState.UNLOADED
                    : saveOff ? RocketOrdinaryRetirement.SlotState.SAVE_OFF : RocketOrdinaryRetirement.SlotState.LOADED;
        }
        @Override public boolean captureSupported(RocketRetirementProof.Slot slot) { return supported; }
    }
}

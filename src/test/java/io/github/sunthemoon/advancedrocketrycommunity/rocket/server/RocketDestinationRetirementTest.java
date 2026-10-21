package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketDestinationRetirementTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void ticketsPrecedeTheUnloadedSlotCheckAndRetriesCannotNudgeBackoff() {
        var f = new Fixture(); f.port.loaded = false; f.begin();
        assertEquals(List.of("ticket", "slot"), f.port.order);
        assertEquals("SLOT_UNLOADED", f.retirements.reason(f.logical()));
        assertTrue(f.journal.clean());
        for (int tick = 1; tick < 200; tick++) {
            f.port.now++; f.begin(); f.retirements.tick();
            assertEquals(1, f.port.tickets, "Loader demand reset the 200-tick backoff");
        }
        f.port.now++; f.retirements.tick();
        assertEquals(2, f.port.tickets);
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
    }

    @Test void supportedDestinationUsesItsOwnCensusFirstAndScansOnEveryAttempt() {
        var f = new Fixture(); f.begin();
        assertEquals(RocketRetirementProof.Slot.origin(f.record.destinationSnapshot()), f.port.captures.get(0));
        assertEquals(1, f.port.scans);
        f.port.callbacks.get(0).accept(new EscrowBarrier.Result(null, EscrowReason.BARRIER_IO));
        for (int i = 0; i < 199; i++) { f.port.now++; f.retirements.tick(); }
        assertEquals(1, f.port.scans);
        f.port.now++; f.retirements.tick();
        assertEquals(2, f.port.scans, "A late-loaded stale copy needs a fresh scan");
        assertEquals(List.of(RocketRetirementProof.Slot.origin(f.record.destinationSnapshot()),
                RocketRetirementProof.Slot.origin(f.record.sourceSnapshot())), f.port.census);
    }

    @Test void unsupportedStorageKeepsTheRecordAcrossTwoBackoffsWithoutCaptureOrRemoval() {
        var f = new Fixture(); f.port.supported = false; f.begin();
        for (int tick = 0; tick < 401; tick++) { f.port.now++; f.retirements.tick(); }
        assertEquals(3, f.port.tickets);
        assertTrue(f.port.captures.isEmpty());
        assertEquals("UNSUPPORTED_ENTITY_STORAGE", f.retirements.reason(f.logical()));
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
        assertTrue(f.journal.clean());
    }

    @Test void aDiscardHoldCannotReachCaptureAndIsPaced() {
        var f = new Fixture(); f.port.scanReason = "CANDIDATE_OFF_COLUMN"; f.begin();
        for (int tick = 0; tick < 401; tick++) { f.port.now++; f.retirements.tick(); }
        assertEquals(3, f.port.scans);
        assertEquals(3, f.port.tickets);
        assertTrue(f.port.captures.isEmpty());
        assertTrue(f.journal.clean());
        assertSame(f.record, f.journal.find(f.record.transferId()).orElseThrow());
    }

    @Test void aBusyOrSupersededDestinationAttemptStillRespectsTheDemandRate() {
        for (var failure : List.of(EscrowReason.SUPERSEDING_SAVE, EscrowReason.BARRIER_IO)) {
            var f = new Fixture(); f.begin();
            f.port.callbacks.get(0).accept(new EscrowBarrier.Result(null, failure));
            for (int i = 0; i < 199; i++) { f.port.now++; f.retirements.tick(); }
            assertEquals(1, f.port.tickets);
            assertTrue(f.journal.clean());
            f.port.now++; f.retirements.tick();
            assertEquals(2, f.port.tickets);
        }
    }

    @Test void atMostFourDestinationTicketStepsRunPerTickAndStartupDoesNone() {
        var f = new Fixture(); f.port.loaded = false;
        f.retirements.tick(); assertEquals(0, f.port.tickets);
        var records = new ArrayList<RocketTransferRecord>(); records.add(f.record);
        for (int i = 0; i < 5; i++) { var record = record(200 + i * 32); records.add(record); f.journal.put(record); }
        f.acknowledge();
        for (var record : records) { f.begin(record); }
        assertEquals(4, f.port.tickets);
        assertTrue(f.journal.clean(), "A start waiting for its per-tick budget kept a reservation");
        f.port.now++; f.retirements.tick();
        assertEquals(6, f.port.tickets);
        f.retirements.clear(); assertEquals(6, f.port.released);
        assertEquals(6, f.journal.entries().size());
    }

    private static RocketTransferRecord record() { return record(72); }
    private static RocketTransferRecord record(int x) {
        return RocketStationMotionRuleTest.record(new RocketPosition(x, 80, 8)).destinationSpawned(UUID.randomUUID())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
    }

    private final class Fixture {
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final RocketTransferRecord record = record();
        final Port port = new Port();
        final RocketOrdinaryRetirement retirements = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of, server -> port);
        long writes;
        Fixture() { port.scanGuard = () -> assertFalse(journal.actionable(logical()), "Discard scan ran without its reservation"); journal.put(record); acknowledge(); }
        UUID logical() { return record.logicalRocketId(); }
        void acknowledge() {
            var type = ManagedSavedDataType.ROCKET_TRANSFERS;
            RocketJournalTestAccess.write(journal, directory.resolve(type.fileName()), CheckedSavedDataPolicy.atomic(type), ++writes * 1000);
        }
        void begin() { begin(record); }
        void begin(RocketTransferRecord record) {
            try (var held = RocketOrdinaryRetirement.hold(journal, record.logicalRocketId())) {
                retirements.begin(port, journal, RocketOrdinaryRetirement.Site.BEFORE_FILL, record,
                        record.destinationEntityId().orElseThrow(), List.of(), false, held, RocketOrdinaryRetirement.Effects.of(() -> true));
            }
        }
    }

    private static final class Port implements RocketOrdinaryRetirement.Port {
        long now = 100;
        int tickets, scans, released;
        boolean loaded = true, supported = true;
        String scanReason;
        Runnable scanGuard = () -> { };
        final List<String> order = new ArrayList<>();
        final List<RocketRetirementProof.Slot> captures = new ArrayList<>();
        final List<Consumer<EscrowBarrier.Result>> callbacks = new ArrayList<>();
        List<RocketRetirementProof.Slot> census;
        public long now() { return now; }
        public RocketOrdinaryRetirement.Presence survivor(RocketTransferRecord r, UUID id, RocketRetirementProof.Slot slot) {
            return RocketOrdinaryRetirement.Presence.LOADED;
        }
        public boolean crewReady(RocketTransferRecord r, UUID id, boolean offline, Set<UUID> delivered) { return true; }
        public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) {
            order.add("slot"); return loaded ? RocketOrdinaryRetirement.SlotState.LOADED : RocketOrdinaryRetirement.SlotState.UNLOADED;
        }
        public boolean captureSupported(RocketRetirementProof.Slot slot) { return supported; }
        public boolean captureAvailable(RocketRetirementProof.Slot slot, long tick) { return true; }
        public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long tick, Consumer<String> sink,
                               Consumer<EscrowBarrier.Result> callback) { captures.add(slot); callbacks.add(callback); return true; }
        public void retryNextTick(Runnable retry) { fail("Destination retry bypassed its demand backoff"); }
        public boolean bayAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferBayRecoveryGate.Action action) { return true; }
        public boolean bayClaimed(UUID id) { return false; }
        public boolean escrowAllows(RocketTransferSavedData j, RocketTransferRecord r, RocketTransferSavedData.Reservation own) {
            return own == null ? j.actionable(r.logicalRocketId()) : j.actionable(r.logicalRocketId(), own);
        }
        public CompoundTag projection(RocketTransferRecord r, UUID survivor) { var tag = new CompoundTag(); tag.putUUID("UUID", survivor); return tag; }
        public void keepLoaded(RocketTransferRecord r, List<RocketRetirementProof.Slot> slots) { tickets++; order.add("ticket"); }
        public String prepare(RocketTransferRecord r, UUID survivor, List<RocketRetirementProof.Slot> slots) {
            scanGuard.run(); scans++; census = List.copyOf(slots); return scanReason;
        }
        public void releaseTickets(RocketTransferRecord r) { released++; }
        public Consumer<String> sink() { return label -> { }; }
        public boolean afterAcknowledged(RocketTransferSavedData j, RocketTransferSavedData.Reservation token, BooleanSupplier effect, Runnable discarded) {
            return RocketJournalTestAccess.queue(j, token, effect, discarded);
        }
    }
}

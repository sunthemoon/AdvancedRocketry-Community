package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketReleaseCoordinatorTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static UUID id(long n) { return GovernedTransferFixture.id(n); }
    static RocketTransferRecord legacy() {
        var g = GovernedTransferFixture.create(1, 3, false).record();
        return RocketTransferRecord.create(g.transferId(), g.logicalRocketId(), g.ownerId(), g.sourceEntityId(),
                g.sourceSnapshot(), g.destinationSnapshot(), g.sourceFlightData(), g.destinationFlightData(), g.requiredFuel(), g.createdAtGameTime());
    }
    static RocketReleasePlan plan(RocketTransferRecord t) {
        return RocketReleasePlan.create(RocketRejoinRoot.empty(), id(900), t, Hold.H1, t.sourceEntityId(),
                t.sourceSnapshot(), t.sourceFlightData().markFailed(10).recover(true, 10), p -> p.equals(id(100)), Set.of());
    }
    static final class Proof implements RocketReleaseCoordinator.Proof {
        boolean valid = true, closed; final int copies;
        Proof(int copies) { this.copies = copies; }
        public boolean valid() { return valid && !closed; }
        public int copies() { return copies; }
        public void close() { closed = true; }
    }
    final class World implements RocketReleaseCoordinator.Port {
        long tick; boolean capture = true, delay, crew, boundary = true, loaded = true, gone, missing;
        int captures, scrubs, completed, tickets, peakTickets;
        final List<Proof> proofs = new ArrayList<>();
        final List<String> events = new ArrayList<>();
        final Map<Long, Set<UUID>> visited = new HashMap<>();
        RocketTransferSavedData journal;
        Consumer<RocketReleaseCoordinator.Proof> delayed;
        public long now() { return tick; }
        public boolean boundary(Batch batch) { visited.computeIfAbsent(tick, n -> new HashSet<>()).add(batch.release()); return boundary; }
        public boolean applySeats(Batch batch) { scrubs++; return true; }
        public boolean crewReady(Batch batch) { return crew; }
        public RocketReleaseCoordinator.SlotState slot(Slot slot) {
            return missing ? RocketReleaseCoordinator.SlotState.MISSING : loaded ? RocketReleaseCoordinator.SlotState.READY : RocketReleaseCoordinator.SlotState.UNLOADED;
        }
        public void ticket(UUID release, Slot slot) { tickets++; peakTickets = Math.max(peakTickets, tickets); }
        public void unticket(UUID release, Slot slot) { tickets--; }
        public boolean capture(Batch batch, Slot slot, RocketPassengerManifest b0, Consumer<RocketReleaseCoordinator.Proof> callback) {
            captures++; if (!capture) { return false; } if (delay) { delayed = callback; return true; }
            var proof = new Proof(gone ? 0 : slot.equals(batch.slots().get(0)) ? 1 : 0); proofs.add(proof); callback.accept(proof); return true;
        }
        public void flushJournal() { journal.save(directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()).toFile()); journal.pollWriter(tick); }
        public void completed(Batch batch) { completed++; }
        public void notice(Batch batch, String state) { events.add(state); }
    }
    final class Session implements AutoCloseable {
        final RocketRejoinSavedData sidecar = RocketRejoinSavedData.open(directory.resolve(RocketRejoinSavedData.TYPE.fileName()));
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final World world = new World(); final RocketReleaseCoordinator release;
        Session(RocketTransferRecord record) throws Exception {
            world.journal = journal; if (record != null) { journal.put(record); } else { journal.setDirty(); }
            world.flushJournal(); waitFor(() -> journal.acknowledgedCurrent(), () -> journal.pollWriter(world.tick));
            release = new RocketReleaseCoordinator(sidecar, journal, world);
        }
        void step() { sidecar.tick(world.tick); world.flushJournal(); release.tick(); }
        void finish() throws Exception { waitFor(() -> world.completed > 0, () -> { world.tick++; step(); }); }
        public void close() { release.close(); sidecar.close(); journal.stop(); }
    }
    static void waitFor(java.util.function.BooleanSupplier done, Runnable step) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!done.getAsBoolean() && System.nanoTime() < deadline) { step.run(); Thread.sleep(2); }
        assertTrue(done.getAsBoolean(), "Owner-thread completion did not arrive");
    }

    @Test void b0TimeoutDropsOnlyRamAdmissionAndLateCaptureCannotApplySeats() throws Exception {
        var t = legacy(); var p = plan(t);
        try (var s = new Session(t)) {
            s.world.delay = true; assertTrue(s.release.admit(p)); assertTrue(s.release.fenced(t.logicalRocketId()));
            s.step(); assertEquals(1, s.world.captures); assertEquals(0, s.world.scrubs);
            s.world.tick = 199; s.step(); assertTrue(s.release.fenced(t.logicalRocketId()));
            s.world.tick = 200; s.step(); assertFalse(s.release.fenced(t.logicalRocketId()));
            assertTrue(s.sidecar.root().batches().isEmpty()); assertSame(t, s.journal.find(t.transferId()).orElseThrow());
            assertFalse(java.nio.file.Files.exists(directory.resolve(RocketRejoinSavedData.TYPE.fileName())));
            var late = new Proof(1); s.world.delayed.accept(late);
            assertTrue(late.closed); assertEquals(0, s.world.scrubs);
            assertTrue(s.sidecar.root().batches().isEmpty());
        }
    }

    @Test void closedSidecarRefusesBeforeTheRamFenceOrNativeCapture() throws Exception {
        var t = legacy();
        try (var s = new Session(t)) {
            s.sidecar.close();
            assertFalse(s.release.admit(plan(t)));
            assertFalse(s.release.fenced(t.logicalRocketId()));
            assertEquals(0, s.world.captures);
            assertTrue(s.world.events.isEmpty());
            assertSame(t, s.journal.find(t.transferId()).orElseThrow());
        }
    }

    @Test void repeatStillFindsPlacementOnlyBatchAfterSeatsComplete() throws Exception {
        var t = legacy(); var p = plan(t);
        try (var s = new Session(t)) {
            assertTrue(s.release.admit(p)); s.finish();
            assertFalse(s.release.fenced(t.logicalRocketId()));
            assertEquals(p.batch().release(), s.release.release(t.transferId()).orElseThrow());
            assertEquals("PLACEMENT", s.release.stage(p.batch().release()));
            assertEquals(1, s.sidecar.root().nextSequence());
        }
    }

    @Test void completesOnlyAfterBothAcknowledgementsAndDoesNotRevokeALaterSameSeatAssignment() throws Exception {
        var t = legacy(); var p = plan(t);
        try (var s = new Session(t)) {
            assertTrue(s.release.admit(p)); s.step(); assertEquals(0, s.world.scrubs);
            assertEquals(p.manifest(), s.release.filter(t.logicalRocketId(), p.manifest()));
            s.finish(); assertFalse(s.release.fenced(t.logicalRocketId()));
            assertEquals(p.batch().scrubbed(), RocketReleasePlan.version(s.journal.find(t.transferId()).orElseThrow()));
            assertTrue(s.sidecar.acknowledgedRoot().entries().stream().allMatch(e -> e.seat() == Seat.VERIFIED));
            assertEquals(p.manifest(), s.release.filter(t.logicalRocketId(), p.manifest()));
            assertTrue(s.world.proofs.stream().allMatch(proof -> proof.closed)); assertEquals(0, s.world.tickets);
        }
    }

    @Test void b1FailureWaitsTwoHundredTicksAndKeepsTheDurableFence() throws Exception {
        var t = legacy();
        try (var s = new Session(t)) {
            assertTrue(s.release.admit(plan(t))); s.step();
            waitFor(() -> s.sidecar.releasePermit(id(900)).isPresent(), () -> s.sidecar.tick(s.world.tick));
            s.world.capture = false; s.step(); int before = s.world.captures; assertTrue(before >= 2);
            s.world.tick = 199; s.step(); assertEquals(before, s.world.captures);
            assertTrue(s.release.fenced(t.logicalRocketId())); s.world.capture = true; s.world.tick = 200; s.step();
            assertTrue(s.world.captures > before); s.finish();
        }
    }

    @Test void restartAfterRemovalUsesTheSidecarCensusWithoutTAndCanFinishGone() throws Exception {
        var p = plan(legacy());
        try (var data = RocketRejoinSavedData.open(directory.resolve(RocketRejoinSavedData.TYPE.fileName()))) {
            assertTrue(data.submit(p.admit(data.root()))); waitFor(data::acknowledgedCurrent, () -> data.tick(0));
        }
        try (var s = new Session(null)) {
            s.world.gone = true; assertTrue(s.release.fenced(p.batch().rocket()));
            assertTrue(s.release.filter(p.batch().rocket(), p.manifest()).assignment(id(101)).isEmpty());
            s.finish(); assertTrue(s.journal.entries().isEmpty());
            assertEquals(1, s.world.events.stream().filter("GONE"::equals).count());
            assertEquals(p.batch().slots().size(), s.world.captures);
        }
    }

    @Test void unrecordedVersionKeepsTheFenceWithoutCapturingOrScrubbingTheJournal() throws Exception {
        var t = legacy(); var p = plan(t);
        try (var data = RocketRejoinSavedData.open(directory.resolve(RocketRejoinSavedData.TYPE.fileName()))) {
            assertTrue(data.submit(p.admit(data.root()))); waitFor(data::acknowledgedCurrent, () -> data.tick(0));
        }
        var changed = t.destinationSpawned(id(999));
        try (var s = new Session(changed)) {
            s.step(); s.world.tick = 500; s.step();
            assertTrue(s.release.fenced(t.logicalRocketId())); assertEquals(0, s.world.captures);
            assertSame(changed, s.journal.find(t.transferId()).orElseThrow()); assertEquals(0, s.world.completed);
        }
    }

    private void persist(RocketRejoinRoot root) throws Exception {
        try (var data = RocketRejoinSavedData.open(directory.resolve(RocketRejoinSavedData.TYPE.fileName()))) {
            assertTrue(data.submit(root)); waitFor(data::acknowledgedCurrent, () -> data.tick(0));
        }
    }
    @Test void aStartupBoundaryHoldDoesNotStrandTheSessionS1Acknowledgement() throws Exception {
        var p = plan(legacy()); persist(p.admit(RocketRejoinRoot.empty()));
        try (var s = new Session(null)) {
            s.world.boundary = false; s.step(); assertEquals(0, s.world.captures);
            s.world.boundary = true; s.world.gone = true; s.world.tick = 200;
            s.finish(); assertEquals(1, s.world.completed);
        }
    }

    @Test void proofInvalidationAfterJ1ReprovesWithoutRollingBackTheRemovalOrRevivingItsLease() throws Exception {
        var t = legacy();
        try (var s = new Session(t)) {
            var old = s.journal.logicalLease(t.logicalRocketId(), t.transferId(), 0);
            s.world.crew = true; assertTrue(s.release.admit(plan(t)));
            waitFor(() -> s.journal.find(t.transferId()).isEmpty(), () -> { s.world.tick++; s.step(); });
            assertFalse(old.getAsBoolean()); int captures = s.world.captures;
            s.world.proofs.stream().filter(p -> !p.closed).findFirst().orElseThrow().valid = false;
            s.world.tick++; s.step(); long failedAt = s.world.tick;
            assertTrue(s.release.fenced(t.logicalRocketId())); assertEquals(0, s.world.completed);
            s.world.tick = failedAt + 199; s.step(); assertEquals(captures, s.world.captures);
            s.world.tick++; s.finish(); assertTrue(s.journal.find(t.transferId()).isEmpty());
            assertTrue(s.world.captures > captures); assertFalse(old.getAsBoolean());
        }
    }

    @Test void ticketCapsAndTwoBatchSchedulingAreGlobalAndMissingLevelsNeverAcquireTickets() throws Exception {
        var root = RocketRejoinRoot.empty();
        for (int i = 1; i <= 3; i++) {
            var b = RocketRejoinCodecTest.batch(i, i - 1, List.of(id(100 + i)));
            b = new Batch(b.release(), b.sequence(), b.transfer(), b.rocket(), b.authority(), b.hold(), b.dimension(),
                    b.pad(), b.minimum(), b.maximum(), b.resources(), b.before(), b.scrubbed(), b.released(),
                    List.of(new Slot(b.dimension(), 0, 0), new Slot(b.dimension(), 1, 1), new Slot(b.dimension(), 2, 2)));
            root = root.admit(b, List.of(RocketRejoinCodecTest.entry(i, id(100 + i))));
        }
        persist(root);
        try (var s = new Session(null)) {
            s.world.missing = true;
            for (int i = 0; i < 4; i++) { s.world.tick++; s.step(); Thread.sleep(2); }
            assertEquals(0, s.world.tickets); assertEquals(0, s.world.captures);
            s.world.missing = false; s.world.loaded = false; s.world.tick = 205;
            waitFor(() -> s.world.tickets == 6, () -> { s.world.tick++; s.step(); });
            assertEquals(6, s.release.tickets()); assertEquals(6, s.world.peakTickets);
            for (int i = 0; i < 20; i++) { s.world.tick++; s.step(); }
            assertEquals(6, s.world.peakTickets); assertEquals(0, s.world.captures);
            assertTrue(s.world.visited.values().stream().allMatch(ids -> ids.size() <= 2));
            s.world.loaded = true; s.world.gone = true;
            waitFor(() -> s.world.completed == 3, () -> { s.world.tick++; s.step(); });
            assertEquals(0, s.world.tickets); assertTrue(s.world.peakTickets <= 6);
        }
    }

    @Test void s2RetiresNotRequiredEntriesAndTheirLastBatchAtomically() throws Exception {
        var b = RocketRejoinCodecTest.batch(1, 0, List.of(id(100)));
        var root = RocketRejoinRoot.empty().admit(b, List.of(new Entry(b.release(), id(100), 0, Placement.NOT_REQUIRED, Seat.PENDING, null)));
        persist(root);
        try (var s = new Session(null)) {
            s.world.gone = true; s.finish();
            assertTrue(s.sidecar.acknowledgedRoot().entries().isEmpty()); assertTrue(s.sidecar.acknowledgedRoot().batches().isEmpty());
            assertEquals(1, s.sidecar.acknowledgedRoot().nextSequence());
        }
    }

    @Test void coldTicketsExpireAtFourHundredTicksAndWaitTwoHundredBeforeReacquisition() throws Exception {
        var t = legacy(); var p = plan(t); persist(p.admit(RocketRejoinRoot.empty()));
        try (var s = new Session(t)) {
            s.world.loaded = false;
            waitFor(() -> s.world.tickets > 0, () -> { s.world.tick++; s.step(); });
            long started = s.world.tick; int count = p.batch().slots().size();
            assertEquals(count, s.world.tickets);
            s.world.tick = started + 399; s.step(); assertEquals(count, s.world.tickets);
            s.world.tick++; s.step(); assertEquals(0, s.world.tickets);
            assertTrue(s.world.events.contains("TICKET_EXPIRED")); assertTrue(s.release.fenced(t.logicalRocketId()));
            s.world.tick += 199; s.step(); assertEquals(0, s.world.tickets);
            s.world.tick++; s.step(); assertEquals(count, s.world.tickets);
            s.world.loaded = true; s.finish(); assertEquals(0, s.world.tickets);
        }
    }

    @Test void b0AdmissionRefusalAndZeroCopiesNeverPublishSidecarOrScrubT() throws Exception {
        var t = legacy();
        try (var s = new Session(t)) {
            s.world.capture = false; assertTrue(s.release.admit(plan(t))); s.step();
            assertFalse(s.release.fenced(t.logicalRocketId())); assertTrue(s.sidecar.root().batches().isEmpty());
            s.world.capture = true; s.world.gone = true; assertTrue(s.release.admit(plan(t))); s.step();
            assertFalse(s.release.fenced(t.logicalRocketId())); assertTrue(s.sidecar.root().batches().isEmpty());
            assertSame(t, s.journal.find(t.transferId()).orElseThrow()); assertEquals(0, s.world.scrubs);
            assertFalse(java.nio.file.Files.exists(directory.resolve(RocketRejoinSavedData.TYPE.fileName())));
        }
    }
}

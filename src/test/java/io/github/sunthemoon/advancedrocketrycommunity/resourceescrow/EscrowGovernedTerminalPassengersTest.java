package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class EscrowGovernedTerminalPassengersTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;

    @BeforeAll static void bootstrap() {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap.initialize();
    }

    @Test void launchBoundaryWaitsAreTransientButThePassengerPrerequisiteStaysSticky() {
        var boundary = new AtomicBoolean(false); var passengers = new AtomicBoolean(true);
        var rocket = EscrowFlightSourceTest.liveSource(() -> true).rocket;
        var source = EscrowGovernedTerminal.waitSource(rocket, passengers::get, boundary::get);
        long captures = rocket.governedCaptures();
        for (int visit = 0; visit < 2_000; visit++) {
            assertFalse(source.observe()); assertTrue(source.unchanged.get());
        }
        boundary.set(true); assertTrue(source.observe());
        passengers.set(false); assertFalse(source.observe()); assertFalse(source.unchanged.get());
        passengers.set(true); assertFalse(source.observe(), "A restored passenger prerequisite resurrected the lease");
        assertEquals(captures, rocket.governedCaptures());
    }

    @Test void explicitPassengerDepartureInvalidatesEvenWhileTheLaunchBoundaryRefuses() throws Exception {
        var record = io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture.create().record();
        var boundary = new AtomicBoolean(false);
        var source = EscrowGovernedTerminal.waitSource(EscrowFlightSourceTest.liveSource(() -> true).rocket, () -> true, boundary::get);
        var type = Class.forName(EscrowGovernedTerminal.class.getName() + "$Work");
        var constructor = type.getDeclaredConstructor(record.getClass(), EscrowFlightTables.Outcome.class,
                EscrowFlightSource.class, EscrowGovernedTerminal.JournalHold.class);
        constructor.setAccessible(true);
        var work = constructor.newInstance(record, EscrowFlightTables.Outcome.CANCELLED, source, null);
        try (var terminal = new EscrowGovernedTerminal()) {
            java.util.Map<UUID, Object> states = io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.get(
                    EscrowGovernedTerminal.class, terminal, "work");
            states.put(record.transferId(), work);
            assertFalse(source.observe()); assertTrue(source.unchanged.get());
            terminal.passengerLeft(record.sourceFlightData().passengers().assignments().get(0).passengerId());
            assertFalse(source.unchanged.get());
            boundary.set(true); assertFalse(source.observe(), "Departure was hidden by a temporary launch-boundary refusal");
        }
    }

    @Test void nativeOrPassengerChangesDuringARefusalRevokeTheQueuedF7LeasePermanently() throws Exception {
        for (boolean passengerChange : new boolean[] { false, true }) {
            var fixture = io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture.create();
            var record = fixture.record();
            var boundary = new AtomicBoolean(true); var passengers = new AtomicBoolean(true);
            var rocket = EscrowFlightSourceTest.liveSource(() -> true).rocket;
            var source = EscrowGovernedTerminal.waitSource(rocket, passengers::get, boundary::get);
            var original = rocket.position();
            var journal = new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData();
            try (var terminal = new EscrowGovernedTerminal()) {
                journal.put(record);
                var base = java.nio.file.Files.createDirectory(directory.resolve("journal-" + passengerChange));
                EscrowArmedRestartBoundaryTest.acknowledge(journal, base.resolve(
                        io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
                var slots = new java.util.LinkedHashMap<EscrowEndpoint, EscrowBarrier.Proof>();
                for (var endpoint : EscrowGovernedTerminalDisk.slots(record, EscrowFlightTables.Outcome.CANCELLED)) {
                    var capture = new EscrowChunkStore.Capture(java.util.concurrent.CompletableFuture.completedFuture(null), null,
                            directory, new net.minecraft.world.level.ChunkPos(endpoint.x(), endpoint.z()),
                            new java.util.concurrent.atomic.AtomicLong(1), 1, 64, () -> { }, () -> { });
                    slots.put(endpoint, new EscrowBarrier.Proof(new EscrowPrefix.State(
                            record.nativeTransfer().orElseThrow().armed().ledger(), endpoint.beforeEpoch(), endpoint.beforeSignature(), java.util.List.of()),
                            capture, new AtomicBoolean(true), new AtomicBoolean(true)));
                }
                var survivor = EscrowGovernedTerminalDiskTest.returned(fixture);
                // This is the real off-thread F7 guard; neither native nor passenger objects are read there.
                try (var proof = new EscrowGovernedHandoff.Proof(journal, record, EscrowFlightTables.Outcome.CANCELLED,
                        survivor, slots, () -> survivor, source::observe, source.unchanged::get)) {
                    var type = Class.forName(EscrowGovernedTerminal.class.getName() + "$Work");
                    var constructor = type.getDeclaredConstructor(record.getClass(), EscrowFlightTables.Outcome.class,
                            EscrowFlightSource.class, EscrowGovernedTerminal.JournalHold.class);
                    constructor.setAccessible(true);
                    var work = constructor.newInstance(record, EscrowFlightTables.Outcome.CANCELLED, source, null);
                    var queued = type.getDeclaredField("queued"); queued.setAccessible(true); queued.setBoolean(work, true);
                    java.util.Map<UUID, Object> states = io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer.get(
                            EscrowGovernedTerminal.class, terminal, "work");
                    states.put(record.transferId(), work);
                    assertTrue(java.util.concurrent.CompletableFuture.supplyAsync(proof::valid).join());
                    boundary.set(false);
                    if (passengerChange) { passengers.set(false); }
                    else { EscrowFlightSourceTest.position(rocket, original.add(1, 0, 0)); }
                    terminal.observe(rocket);
                    assertFalse(source.unchanged.get(), "The refusal hid a native/passenger prerequisite failure");
                    assertFalse(java.util.concurrent.CompletableFuture.supplyAsync(proof::valid).join(), "F7 kept its queued write lease");
                    passengers.set(true); EscrowFlightSourceTest.position(rocket, original); boundary.set(true);
                    assertFalse(source.observe(), "Restoring the prerequisite resurrected the terminal capture");
                    assertFalse(java.util.concurrent.CompletableFuture.supplyAsync(proof::valid).join());
                }
            } finally { journal.stop(); }
        }
    }

    @Test void cancellationAcceptsOnlyOnlineSourceLevelPassengersWithoutAnotherVehicle() {
        var outcome = EscrowFlightTables.Outcome.CANCELLED;
        assertTrue(EscrowGovernedTerminalPassengers.placement(outcome, true, true, true, false));
        assertTrue(EscrowGovernedTerminalPassengers.placement(outcome, true, true, false, true));
        assertFalse(EscrowGovernedTerminalPassengers.placement(outcome, true, true, false, false));
        for (boolean aboard : java.util.List.of(false, true)) {
            assertFalse(EscrowGovernedTerminalPassengers.placement(outcome, false, true, aboard, !aboard));
            assertFalse(EscrowGovernedTerminalPassengers.placement(outcome, true, false, aboard, !aboard));
        }
    }

    @Test void handoffStillRequiresEveryoneAboardAndArmedIsNotATerminalOutcome() {
        assertTrue(EscrowGovernedTerminalPassengers.placement(EscrowFlightTables.Outcome.HANDED_OFF, true, true, true, false));
        assertFalse(EscrowGovernedTerminalPassengers.placement(EscrowFlightTables.Outcome.HANDED_OFF, true, true, false, true));
        assertFalse(EscrowGovernedTerminalPassengers.placement(EscrowFlightTables.Outcome.HANDED_OFF, false, true, true, false));
        assertFalse(EscrowGovernedTerminalPassengers.placement(EscrowFlightTables.Outcome.HANDED_OFF, true, false, true, false));
        assertFalse(EscrowGovernedTerminalPassengers.placement(EscrowFlightTables.Outcome.ARMED, true, true, true, false));
    }

    @Test void unresolvedPollingDoesNotCaptureAndReconnectRepeatsTheFullCheck() {
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var manifest = RocketPassengerManifest.empty(2).assign(first).orElseThrow().assign(second).orElseThrow();
        var online = new HashSet<UUID>(); online.add(first);
        var captures = new AtomicInteger(); var valid = new AtomicBoolean(false);
        java.util.function.BooleanSupplier observe = () -> EscrowGovernedTerminalPassengers.afterPassengers(
                () -> EscrowGovernedTerminalPassengers.resolved(manifest, online::contains),
                () -> { captures.incrementAndGet(); return valid.get(); });
        for (int tick = 0; tick < 2_000; tick++) { assertFalse(observe.getAsBoolean()); }
        assertEquals(0, captures.get(), "Offline polling rebuilt the expensive survivor state");
        online.add(second);
        assertFalse(observe.getAsBoolean(), "Reconnection is not a survivor proof");
        valid.set(true); assertTrue(observe.getAsBoolean());
        assertEquals(2, captures.get(), "Ready passengers skipped final validation");
        online.remove(first);
        for (int tick = 0; tick < 2_000; tick++) { assertFalse(observe.getAsBoolean()); }
        assertEquals(2, captures.get(), "A later departure reused or rebuilt a capture");
    }

    @Test void offlineOrUnresolvedAssignmentRetainsTheHandoffWait() {
        var first = UUID.randomUUID(); var second = UUID.randomUUID();
        var manifest = RocketPassengerManifest.empty(2).assign(first).orElseThrow().assign(second).orElseThrow();
        var resolved = new HashSet<UUID>();
        assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
        resolved.add(first); assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
        resolved.add(second); assertTrue(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
        resolved.remove(first); assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, resolved::contains));
    }

    @Test void allSixteenAssignmentsAreCheckedAndAnEmptyManifestNeedsNoPlayer() {
        assertTrue(EscrowGovernedTerminalPassengers.resolved(RocketPassengerManifest.empty(0), id -> {
            fail("Empty manifest performed a player lookup"); return false;
        }));
        var manifest = RocketPassengerManifest.empty(16); var passengers = new HashSet<UUID>();
        for (int i = 0; i < 16; i++) { var id = new UUID(30, i); passengers.add(id); manifest = manifest.assign(id).orElseThrow(); }
        var observed = new HashSet<UUID>();
        assertTrue(EscrowGovernedTerminalPassengers.resolved(manifest, id -> { observed.add(id); return true; }));
        assertEquals(passengers, observed);
        for (var missing : passengers) {
            assertFalse(EscrowGovernedTerminalPassengers.resolved(manifest, id -> !id.equals(missing)));
        }
    }
}

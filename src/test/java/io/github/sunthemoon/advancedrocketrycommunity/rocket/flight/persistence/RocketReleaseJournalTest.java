package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketReleaseJournalTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static UUID id(long n) { return GovernedTransferFixture.id(n); }
    private static RocketTransferRecord legacy() {
        var g = RocketTransferSavedDataTest.record(id(8000), id(8001), id(8002),
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(400, 80, 8));
        var passengers = RocketPassengerManifest.empty(1).assign(id(101)).orElseThrow();
        return RocketTransferRecord.create(g.transferId(), g.logicalRocketId(), g.ownerId(), g.sourceEntityId(),
                g.sourceSnapshot(), g.destinationSnapshot(), g.sourceFlightData().withPassengers(passengers),
                g.destinationFlightData().withPassengers(passengers), g.requiredFuel(), g.createdAtGameTime());
    }
    private static RocketReleasePlan plan(RocketTransferRecord t) {
        boolean source = t.phase() == RocketTransferPhase.PREPARED;
        return RocketReleasePlan.create(RocketRejoinRoot.empty(), id(900), t,
                source ? RocketRejoinRoot.Hold.H1 : RocketRejoinRoot.Hold.H2,
                source ? t.sourceEntityId() : t.destinationEntityId().orElseThrow(),
                source ? t.sourceSnapshot() : t.destinationSnapshot(),
                source ? t.sourceFlightData().markFailed(10).recover(true, 10) : t.destinationFlightData().land(10),
                p -> p.equals(id(100)), Set.of());
    }
    private void acknowledge(RocketTransferSavedData journal) {
        journal.setDirty(); journal.saveChecked(directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()),
                CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS));
        assertTrue(journal.acknowledgedCurrent());
    }
    private static void await(RocketRejoinSavedData sidecar) throws Exception {
        long end = System.nanoTime() + 10_000_000_000L;
        while (!sidecar.acknowledgedCurrent() && System.nanoTime() < end) { sidecar.tick(0); Thread.sleep(1); }
        assertTrue(sidecar.acknowledgedCurrent());
    }
    private RocketRejoinSavedData open() { return RocketRejoinSavedData.open(directory.resolve(RocketRejoinSavedData.TYPE.fileName())); }

    @Test void exactS1IsRequiredAndLoadedStateAloneIsNotAWriteAcknowledgement() throws Exception {
        var p = plan(legacy());
        try (var sidecar = open()) {
            assertTrue(sidecar.releasePermit(p.batch().release()).isEmpty());
            assertTrue(sidecar.submit(p.admit(sidecar.root())));
            assertTrue(sidecar.releasePermit(p.batch().release()).isEmpty());
            await(sidecar); assertTrue(sidecar.releasePermit(p.batch().release()).orElseThrow().valid());
        }
        try (var loaded = open()) {
            assertTrue(loaded.releasePermit(p.batch().release()).isEmpty());
            assertTrue(loaded.submit(loaded.root())); await(loaded);
            assertTrue(loaded.releasePermit(p.batch().release()).orElseThrow().valid());
        }
    }

    @Test void casPreservesOutboxAndOtherGovernedRecordAndRevokesBothHistories() throws Exception {
        var journal = new RocketTransferSavedData(); var t = legacy(); var p = plan(t);
        var governed = GovernedTransferFixture.create(50, 1, false).record();
        try (var sidecar = open()) {
            journal.put(t); journal.put(governed); acknowledge(journal);
            var old = journal.logicalLease(t.logicalRocketId(), t.transferId(), 0);
            var transferHistory = journal.logicalLease(id(999), t.transferId(), 0);
            assertFalse(transferHistory.getAsBoolean());
            var other = journal.logicalLease(governed.logicalRocketId(), governed.transferId(), 0);
            var elected = FirstEvent.MoonLanding.create(id(910), t.transferId(), t.logicalRocketId(), t.ownerId(),
                    id(911), t.checksum(), 0, 80, 0, 10, List.of(id(100)));
            assertTrue(journal.electFirstMoonLanding(elected));
            assertTrue(sidecar.submit(p.admit(sidecar.root()))); await(sidecar);
            var permit = sidecar.releasePermit(p.batch().release()).orElseThrow();
            assertThrows(IllegalArgumentException.class, () -> journal.put(t.releasePassengers(Set.copyOf(p.batch().released()))));
            assertFalse(journal.releasePassengers(permit, false, () -> false));
            long before = journal.mutationSequence();
            assertTrue(journal.releasePassengers(permit, false, () -> true));
            assertEquals(before + 1, journal.mutationSequence());
            assertFalse(old.getAsBoolean()); assertTrue(other.getAsBoolean());
            assertEquals(p.batch().scrubbed(), RocketReleasePlan.version(journal.find(t.transferId()).orElseThrow()));
            assertSame(governed, journal.find(governed.transferId()).orElseThrow());
            assertTrue(journal.deliverFirstMoonLanding(elected)); acknowledge(journal);
            assertEquals(elected, journal.durableFirstMoonLanding().outbox().event());
            assertFalse(old.getAsBoolean()); assertTrue(journal.logicalLease(t.logicalRocketId(), t.transferId(), 0).getAsBoolean());
            before = journal.mutationSequence(); assertTrue(journal.releasePassengers(permit, false, () -> true));
            assertEquals(before, journal.mutationSequence());
            assertTrue(journal.releasePassengers(permit, true, () -> true)); assertTrue(journal.find(t.transferId()).isEmpty());
            assertFalse(journal.logicalLease(id(999), t.transferId(), 0).getAsBoolean());
            acknowledge(journal); assertTrue(journal.logicalLease(t.logicalRocketId(), t.transferId(), 0).getAsBoolean());
        } finally { journal.stop(); }
    }

    @Test void h2KeepsItsReservationAndAnUnexpectedVersionIsNotScrubbed() throws Exception {
        var journal = new RocketTransferSavedData(); var t = legacy().destinationSpawned(id(800))
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED); var p = plan(t);
        try (var sidecar = open()) {
            journal.put(t); acknowledge(journal); assertTrue(sidecar.submit(p.admit(sidecar.root()))); await(sidecar);
            var permit = sidecar.releasePermit(p.batch().release()).orElseThrow();
            assertTrue(journal.releasePassengers(permit, true, () -> true)); acknowledge(journal);
            assertTrue(journal.find(t.transferId()).isPresent());
            var different = journal.find(t.transferId()).orElseThrow().rebindDestination(id(801));
            journal.put(different); acknowledge(journal);
            assertFalse(journal.releasePassengers(permit, false, () -> true));
            assertSame(different, journal.find(t.transferId()).orElseThrow());
        } finally { journal.stop(); }
    }

    @Test void acknowledgedS2ExpiresThePermitEvenWithPendingPlacement() throws Exception {
        var p = plan(legacy());
        try (var sidecar = open()) {
            assertTrue(sidecar.submit(p.admit(sidecar.root()))); await(sidecar);
            var permit = sidecar.releasePermit(p.batch().release()).orElseThrow(); var s2 = sidecar.root();
            for (var entry : s2.entries()) { s2 = s2.update(new RocketRejoinRoot.Entry(entry.release(), entry.player(),
                    entry.seatIndex(), entry.placement(), RocketRejoinRoot.Seat.VERIFIED, null)); }
            assertTrue(sidecar.submit(s2)); await(sidecar); assertFalse(permit.valid());
            assertTrue(sidecar.releasePermit(p.batch().release()).isEmpty());
        }
    }
}

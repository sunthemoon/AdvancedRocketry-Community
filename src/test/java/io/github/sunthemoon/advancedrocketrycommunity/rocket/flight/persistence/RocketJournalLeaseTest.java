package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R17 item 3: the checked journal lease is per logical rocket. */
class RocketJournalLeaseTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    private void acknowledge(RocketTransferSavedData data) {
        data.setDirty(); data.saveChecked(directory.resolve(TYPE.fileName()), CheckedSavedDataPolicy.atomic(TYPE));
        assertTrue(data.acknowledgedCurrent());
    }

    private static UUID id(long value) { return new UUID(4_707, value); }

    private static RocketTransferRecord other(long value, int x) {
        return RocketTransferSavedDataTest.record(id(value), id(value + 1), id(value + 2), new RocketPosition(x, 80, 8));
    }

    private static boolean offThread(BooleanSupplier lease) throws InterruptedException {
        var result = new boolean[1];
        var thread = new Thread(() -> result[0] = lease.getAsBoolean(), "lease-reader"); thread.start(); thread.join();
        return result[0];
    }

    @Test void anotherRocketsRecordChangeDoesNotRevokeAnAbsenceLeaseButLsOwnRecordDoes() throws Exception {
        var data = new RocketTransferSavedData(); var other = other(10, 200);
        UUID logical = id(800), transfer = id(801);
        try {
            data.put(other); acknowledge(data);
            var lease = data.logicalLease(logical, transfer, 2); var world = data.acknowledgedLease();
            assertTrue(lease.getAsBoolean() && world.getAsBoolean());
            data.put(other.destinationSpawned(id(19)));
            assertFalse(data.acknowledgedCurrent()); assertFalse(world.getAsBoolean(), "The world-wide lease still sees the change");
            assertTrue(lease.getAsBoolean(), "Another rocket's unacknowledged phase change revoked L's lease");
            assertTrue(offThread(lease), "The writer thread must read the same per-rocket answer");
            data.remove(other.transferId());
            assertTrue(lease.getAsBoolean(), "Another rocket's removal revoked L's lease");
            assertTrue(data.logicalAcknowledged(logical) && data.actionAuthorityAvailable(logical));
            assertTrue(data.logicalLease(id(802), id(803), 2).getAsBoolean(), "A new lease waited for another rocket's acknowledgement");
            assertFalse(data.logicalAcknowledged(other.logicalRocketId()), "The other rocket's own change is still pending");
            assertFalse(data.logicalLease(other.logicalRocketId(), null, 0).getAsBoolean());

            var own = RocketTransferSavedDataTest.record(id(30), logical, id(32), new RocketPosition(400, 80, 8));
            data.put(own);
            assertFalse(lease.getAsBoolean(), "L's own record must revoke its checked absence");
            acknowledge(data);
            assertFalse(lease.getAsBoolean(), "An acknowledged record of L is not L's checked absence");
            assertFalse(offThread(lease));
        } finally { data.stop(); }
    }

    @Test void anExactRecordLeaseSurvivesOtherRocketsButNotLsOwnOrUnacknowledgedChange() throws Exception {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); var other = other(40, 200);
        UUID logical = record.logicalRocketId();
        try {
            data.put(record); data.put(other); acknowledge(data);
            var lease = data.logicalLease(logical, record.transferId(), 0);
            assertTrue(lease.getAsBoolean());
            data.put(other.destinationSpawned(id(49))); assertTrue(lease.getAsBoolean());
            acknowledge(data); assertTrue(lease.getAsBoolean(), "An unchanged record stays leased across another generation");

            var spawned = record.destinationSpawned(id(50)); data.put(spawned);
            assertFalse(lease.getAsBoolean(), "L's own unacknowledged change revoked nothing");
            assertFalse(data.logicalAcknowledged(logical));
            assertFalse(data.logicalLease(logical, record.transferId(), 0).getAsBoolean(), "A lease was acquired over L's pending change");
            assertTrue(data.logicalGuard(logical).isEmpty());
            acknowledge(data);
            assertFalse(lease.getAsBoolean(), "The superseded record instance and guard came back after acknowledgement");
            var next = data.logicalLease(logical, record.transferId(), 0); assertTrue(next.getAsBoolean());
            data.remove(spawned.transferId()); assertFalse(next.getAsBoolean(), "L's removal revoked nothing");
        } finally { data.stop(); }
    }

    @Test void aChangedGuardOrAnyRecordNamingTOrTheSerialRevokesTheLease() throws Exception {
        var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record();
        UUID logical = id(800), transfer = id(60);
        try {
            data.put(record); acknowledge(data);
            var before = data.logicalGuard(record.logicalRocketId()).orElseThrow();
            var exact = data.logicalLease(record.logicalRocketId(), record.transferId(), 0);
            data.put(record.destinationSpawned(id(65)));
            acknowledge(data);
            assertNotEquals(before.recordDigest(), data.logicalGuard(record.logicalRocketId()).orElseThrow().recordDigest());
            assertFalse(exact.getAsBoolean(), "A changed acknowledged guard of L kept its lease");

            var named = data.logicalLease(logical, transfer, 2); assertTrue(named.getAsBoolean());
            data.put(other(60, 200)); // Its transfer ID is the operation's T.
            assertFalse(named.getAsBoolean(), "A record naming T kept the absence lease");
            data.remove(transfer); acknowledge(data);

            var governed = GovernedTransferFixture.create().record(); // Another rocket, ARMED serial 1.
            var serial = data.logicalLease(logical, id(70), 1); var unrelated = data.logicalLease(logical, id(70), 2);
            assertTrue(serial.getAsBoolean() && unrelated.getAsBoolean());
            data.remove(record.transferId()); data.put(governed);
            assertFalse(serial.getAsBoolean(), "Another rocket naming the serial kept the lease");
            assertTrue(unrelated.getAsBoolean(), "A different serial is not named");
            assertFalse(data.logicalLease(logical, id(70), 1).getAsBoolean());
        } finally { data.stop(); }
    }

    @Test void aStoppedOrUnacknowledgedJournalGrantsNoLease() {
        var data = new RocketTransferSavedData(); UUID logical = id(900);
        assertFalse(data.logicalLease(logical, null, 0).getAsBoolean(), "RAM absence is not checked absence");
        assertFalse(data.logicalAcknowledged(logical)); assertTrue(data.logicalGuard(logical).isEmpty());
        acknowledge(data);
        var lease = data.logicalLease(logical, null, 0); assertTrue(lease.getAsBoolean());
        data.stop();
        assertFalse(lease.getAsBoolean()); assertFalse(data.actionAuthorityAvailable(logical));
    }

    private Path file() { return directory.resolve(TYPE.fileName()); }

    private RocketTransferSavedData persisted() {
        return RocketTransferSavedData.load(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow());
    }

    private static RocketJournalCheckpoint.Acknowledgement acknowledgement(long sequence) {
        return new RocketJournalCheckpoint.Acknowledgement(sequence, "0".repeat(64), java.util.Map.of(), java.util.Set.of(),
                java.util.Map.of(), io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox.empty());
    }

    /** Lease review LR1: another rocket's record names T, or the serial, and is removed only in RAM. */
    private void pendingRemovalOfAConflict(RocketTransferRecord other, boolean serial) throws Exception {
        var data = new RocketTransferSavedData(); UUID logical = id(990);
        UUID transfer = serial ? id(991) : other.transferId();
        long claimed = serial ? other.nativeTransfer().orElseThrow().armed().serial() : 0;
        try {
            data.put(other); acknowledge(data);
            assertFalse(data.logicalLease(logical, transfer, claimed).getAsBoolean());
            data.remove(other.transferId());
            assertFalse(data.acknowledgedCurrent()); assertTrue(data.actionAuthorityAvailable(logical));
            assertTrue(persisted().find(other.transferId()).isPresent(), "The conflicting record must still be on disk");
            var pending = data.logicalLease(logical, transfer, claimed);
            assertFalse(pending.getAsBoolean(), "Checked exclusion must not trust another rocket's unacknowledged removal");
            assertFalse(offThread(pending));
            acknowledge(data);
            assertFalse(pending.getAsBoolean(), "A lease refused at acquisition came back");
            var next = data.logicalLease(logical, transfer, claimed);
            assertTrue(next.getAsBoolean(), "An acknowledged removal should permit a new lease");
            assertTrue(offThread(next));
        } finally { data.stop(); }
    }

    @Test void pendingRemovalStillNamesTransferInAcknowledgedJournal() throws Exception {
        pendingRemovalOfAConflict(RocketTransferSavedDataTest.record(), false);
    }

    @Test void pendingRemovalStillNamesSerialInAcknowledgedJournal() throws Exception {
        pendingRemovalOfAConflict(GovernedTransferFixture.create().record(), true);
    }

    @Test void ownAppearAndDisappearDoesNotReviveAnOldAbsenceLease() {
        var data = new RocketTransferSavedData(); var own = RocketTransferSavedDataTest.record();
        try {
            acknowledge(data);
            var lease = data.logicalLease(own.logicalRocketId(), own.transferId(), 0);
            assertTrue(lease.getAsBoolean());
            data.put(own); data.remove(own.transferId());
            assertFalse(lease.getAsBoolean());
            acknowledge(data);
            assertFalse(lease.getAsBoolean());
        } finally { data.stop(); }
    }

    @Test void aFailedRemovalKeepsTheConflictUntilARetryIsAcknowledged() throws Exception {
        var data = new RocketTransferSavedData(); var other = GovernedTransferFixture.create().record();
        long serial = other.nativeTransfer().orElseThrow().armed().serial(); UUID logical = id(995);
        var failing = new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("Injected removal failure"); });
        try {
            data.put(other); acknowledge(data);
            var unrelated = data.logicalLease(logical, id(996), serial + 1); assertTrue(unrelated.getAsBoolean());
            data.remove(other.transferId());
            data.saveChecked(file(), failing);
            assertEquals(RocketTransferSavedData.WriteOutcome.NOT_COMMITTED, data.checkedWriteResult().outcome());
            assertTrue(persisted().find(other.transferId()).isPresent(), "The failed removal left the record on disk");
            assertFalse(data.logicalLease(logical, id(996), serial).getAsBoolean(), "A failed removal checked the serial's absence");
            assertFalse(data.logicalLease(logical, other.transferId(), 0).getAsBoolean(), "A failed removal checked T's absence");
            assertFalse(data.actionAuthorityAvailable(logical), "A failed write still refuses action authority");
            assertTrue(unrelated.getAsBoolean(), "A removal that names neither T nor the serial revoked an unrelated lease");

            data.saveChecked(file(), CheckedSavedDataPolicy.atomic(TYPE));
            assertTrue(data.acknowledgedCurrent()); assertTrue(persisted().find(other.transferId()).isEmpty());
            assertTrue(data.logicalLease(logical, id(996), serial).getAsBoolean());
            assertTrue(data.logicalLease(logical, other.transferId(), 0).getAsBoolean());
            assertTrue(data.actionAuthorityAvailable(logical));
        } finally { data.stop(); }
    }

    @Test void aConflictRefusesALeaseUntilItsRemovalIsAcknowledged() throws Exception {
        var data = new RocketTransferSavedData(); var unrelated = other(1_020, 400);
        UUID logical = id(1_000), transfer = id(1_010);
        try {
            data.put(unrelated); acknowledge(data);
            var lease = data.logicalLease(logical, transfer, 2); assertTrue(lease.getAsBoolean());
            data.put(other(1_010, 200)); // Another rocket's record whose transfer ID is the operation's T.
            assertFalse(lease.getAsBoolean());
            acknowledge(data); assertFalse(lease.getAsBoolean());
            data.remove(transfer); data.remove(unrelated.transferId());
            assertFalse(lease.getAsBoolean(), "A pending removal of the record naming T released the lease");
            assertFalse(offThread(lease));
            assertTrue(data.logicalLease(logical, id(1_030), 2).getAsBoolean(), "An unrelated pending removal refused a lease");
            acknowledge(data);
            assertTrue(lease.getAsBoolean(), "The acknowledged removal did not end the refusal");
            assertTrue(offThread(lease));
            assertTrue(data.logicalLease(logical, transfer, 2).getAsBoolean());
        } finally { data.stop(); }
    }

    @Test void aReplacedRecordNamesItsTransferUntilTheReplacementIsAcknowledged() {
        var data = new RocketTransferSavedData(); var landed = RocketTransferSavedDataTest.committed(other(1_100, 200));
        var returning = RocketTransferSavedDataTest.record(id(1_110), landed.logicalRocketId(), id(1_112), new RocketPosition(200, 80, 8));
        UUID logical = id(1_200);
        try {
            data.put(landed); acknowledge(data);
            data.replace(landed.transferId(), returning);
            assertFalse(data.logicalLease(logical, landed.transferId(), 0).getAsBoolean(), "The replaced record is still acknowledged");
            assertFalse(data.logicalLease(logical, returning.transferId(), 0).getAsBoolean());
            acknowledge(data);
            assertTrue(data.logicalLease(logical, landed.transferId(), 0).getAsBoolean());
            assertFalse(data.logicalLease(logical, returning.transferId(), 0).getAsBoolean());
        } finally { data.stop(); }
    }

    @Test void unacknowledgedRemovalsBeyondTheBoundFoldIntoAConservativeFloor() {
        var leases = new RocketJournalLeases(); var none = java.util.List.<RocketTransferRecord>of();
        var removed = RocketTransferSavedDataTest.record(); // Names neither T nor a serial of the leases below.
        leases.publish(true, acknowledgement(1), none);
        var plain = leases.lease(id(1), null, 0, 1); var named = leases.lease(id(1), id(2), 0, 1);
        var serial = leases.lease(id(1), null, 3, 1);
        assertTrue(plain.getAsBoolean() && named.getAsBoolean() && serial.getAsBoolean());
        for (int i = 0; i < RocketJournalLeases.MAX_HISTORY; i++) { leases.changed(id(10), 2 + i, removed); }
        leases.publish(true, acknowledgement(1), none);
        assertTrue(named.getAsBoolean() && serial.getAsBoolean(), "Retained removals that name neither revoked a lease");
        leases.changed(id(10), 5_000, removed); leases.publish(true, acknowledgement(1), none);
        assertFalse(named.getAsBoolean(), "A dropped removal must be treated as naming every T");
        assertFalse(serial.getAsBoolean(), "A dropped removal must be treated as naming every serial");
        assertTrue(plain.getAsBoolean(), "A lease that checks no T or serial is not a conflict lease");
        leases.publish(true, acknowledgement(5_000), none);
        assertTrue(named.getAsBoolean() && serial.getAsBoolean(), "An acknowledgement after every removal ends the floor");
    }

    @Test void historyBeyondItsBoundFoldsIntoAConservativeFloor() {
        var leases = new RocketJournalLeases(); var none = java.util.List.<RocketTransferRecord>of();
        var acknowledgement = new RocketJournalCheckpoint.Acknowledgement(1, "0".repeat(64), java.util.Map.of(),
                java.util.Set.of(), java.util.Map.of(), io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox.empty());
        leases.publish(true, acknowledgement, none);
        var lease = leases.lease(id(1), null, 0, 1); assertTrue(lease.getAsBoolean());
        for (int i = 0; i < RocketJournalLeases.MAX_HISTORY; i++) { leases.changed(id(10_000 + i), 2 + i); }
        leases.publish(true, acknowledgement, none);
        assertTrue(lease.getAsBoolean(), "Retained history of other rockets revoked an unrelated lease");
        leases.changed(id(20_000), 5_000); leases.publish(true, acknowledgement, none);
        assertFalse(lease.getAsBoolean(), "A dropped history must be treated as a possible change of every rocket");
        assertFalse(leases.acknowledged(id(1)));
    }
}

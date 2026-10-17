package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
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

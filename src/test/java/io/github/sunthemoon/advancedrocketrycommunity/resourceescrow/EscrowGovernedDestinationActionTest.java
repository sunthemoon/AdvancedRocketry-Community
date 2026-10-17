package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedDestinationActionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void preparedRecordAndLoadedAbsenceAreNotPublicationAuthority() throws Exception {
        var fixture = GovernedTransferFixture.create(); var journal = new RocketTransferSavedData();
        try {
            journal.put(fixture.record());
            var authority = acknowledge(journal, fixture.record()); var source = transit(fixture);
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedDestinationAction.issue(authority,
                    source, 161, 161, source::copy, () -> true, () -> true));
            var spawned = spawned(fixture); journal.put(spawned);
            assertNull(EscrowGovernedDispatch.acquire(() -> EscrowGovernedDispatchTest.root(spawned), () -> true, journal, spawned));
            var checked = acknowledge(journal, spawned);
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedDestinationAction.issue(checked,
                    source, 161, 161, source::copy, () -> true, () -> false));
        } finally { journal.stop(); }
    }

    @Test void publicationUsesTheFixedDestinationAndFrozenDebitExactlyOnce() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); var authority = acknowledge(journal, record); var source = transit(fixture); var before = source.copy();
            var permit = EscrowGovernedDestinationAction.issue(authority, source, 161, 7, source::copy, () -> true, () -> true);
            var result = permit.consume(() -> true);
            assertSame(record, result); assertEquals(before, source);
            assertEquals(record.nativeTransfer().orElseThrow().armed().destination(), result.destinationEntityId().orElseThrow());
            assertTrue(result.destinationFlightData().fuel().committedDebits().contains(result.transferId()));
            assertFalse(result.sourceFlightData().fuel().committedDebits().contains(result.transferId()));
            assertEquals(0, result.nativeTransfer().orElseThrow().destinationEnvelope(result).getCompound("wrapper").getLong("resource_epoch"));
            assertThrows(IllegalArgumentException.class, () -> permit.consume(() -> true));
        } finally { journal.stop(); }
    }

    @Test void acknowledgedF4CanRecoverWithOnlyTheUnchangedF1SourceCheckpoint() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); var authority = acknowledge(journal, record); var source = fixture.source();
            assertFalse(EscrowGovernedDestinationAction.sourceDue(record, source, 200));
            var permit = EscrowGovernedDestinationAction.issue(authority, source, 200, 200, source::copy, () -> true, () -> true);
            assertSame(record, permit.consume(() -> true));
            source.getCompound("wrapper").putLong("resource_epoch", 8);
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedDestinationAction.issue(authority,
                    source, 200, 200, source::copy, () -> true, () -> true));
        } finally { journal.stop(); }
    }

    @Test void finalProofBoundarySourceAndTargetAreRecheckedOnEveryConsumption() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); var authority = acknowledge(journal, record);
            for (int fault = 0; fault < 4; fault++) {
                var proof = new AtomicBoolean(true); var boundary = new AtomicBoolean(true); var source = transit(fixture);
                var permit = EscrowGovernedDestinationAction.issue(authority, source, 161, 161, source::copy, boundary::get, proof::get);
                if (fault == 0) { proof.set(false); }
                if (fault == 1) { boundary.set(false); }
                if (fault == 2) { source.putBoolean("invulnerable", true); }
                boolean target = fault != 3;
                assertThrows(IllegalArgumentException.class, () -> permit.consume(() -> target));
                proof.set(true); boundary.set(true);
                assertThrows(IllegalArgumentException.class, () -> permit.consume(() -> true));
            }
        } finally { journal.stop(); }
    }

    @Test void supersededCallbacksAndTerminalArmsCannotPublish() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); var authority = acknowledge(journal, record); var source = transit(fixture);
            var permit = EscrowGovernedDestinationAction.issue(authority, source, 161, 161, source::copy, () -> true, () -> true);
            var cancelled = EscrowGovernedDispatchTest.arm(EscrowGovernedDispatchTest.root(record), tag -> tag.putString("outcome", "CANCELLED"));
            var terminal = EscrowGovernedDispatch.acquire(() -> cancelled, () -> true, journal, record);
            assertEquals(EscrowGovernedDispatch.Route.CLEANUP, terminal.decision().route());
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedDestinationAction.issue(terminal,
                    source, 161, 161, source::copy, () -> true, () -> true));
            journal.put(record.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED));
            assertThrows(IllegalArgumentException.class, () -> permit.consume(() -> true));
        } finally { journal.stop(); }
    }

    @Test void preparedAdvanceUsesLogicalClockAndRejectsIncompleteTransit() {
        var fixture = GovernedTransferFixture.create(); var source = transit(fixture); var record = fixture.record();
        assertFalse(EscrowGovernedDestinationAction.sourceDue(record, source, 160));
        assertTrue(EscrowGovernedDestinationAction.sourceDue(record, source, 161));
        var clock = EscrowGovernedSourceClock.resume(record, source, 3);
        assertFalse(EscrowGovernedDestinationAction.sourceDue(record, source, clock.at(3)));
        assertTrue(EscrowGovernedDestinationAction.sourceDue(record, source, clock.at(3 + RocketFlightLimits.TRANSIT_TICKS)));
        source.putUUID("owner", GovernedTransferFixture.id(999));
        assertFalse(EscrowGovernedDestinationAction.sourceDue(record, source, 161));
    }

    private EscrowGovernedDispatch.Authority acknowledge(RocketTransferSavedData journal, RocketTransferRecord record) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
        var root = EscrowGovernedDispatchTest.root(record);
        var result = EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, record); assertNotNull(result); return result;
    }
    private static RocketTransferRecord spawned(GovernedTransferFixture.Fixture fixture) {
        return fixture.record().destinationSpawned(fixture.record().nativeTransfer().orElseThrow().armed().destination());
    }
    static CompoundTag transit(GovernedTransferFixture.Fixture fixture) {
        var source = fixture.source().copy(); var record = fixture.record();
        source.put("flight", RocketFlightNbtCodec.encode(record.sourceFlightData()));
        var origin = record.sourceSnapshot().sourceOrigin();
        source.put("pos", GovernedTransferFixture.vector(origin.x() + 0.5, origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS, origin.z() + 0.5));
        return source;
    }
}

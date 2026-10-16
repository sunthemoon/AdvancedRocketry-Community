package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedPhaseActionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void phaseAcknowledgementsPrecedeEffectsAndAConsumedPermitCannotReplay() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); var checked = acknowledge(journal, record);
            assertThrows(IllegalArgumentException.class, () -> issue(checked, fixture.source(), fixture.destination(), () -> true));
            record = record.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED); journal.put(record);
            var root = EscrowGovernedDispatchTest.root(record); var pending = record;
            assertNull(EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, pending));
            var passengers = acknowledge(journal, record);
            var permit = issue(passengers, fixture.source(), fixture.destination(), () -> true);
            permit.consume(); assertThrows(IllegalArgumentException.class, permit::consume);
        } finally { journal.stop(); }
    }

    @Test void partialTransfersPreserveEveryNonRiderFieldAndCannotDuplicateAnAssignment() {
        var fixture = GovernedTransferFixture.create(1, 2, false); var record = spawned(fixture).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        var source = fixture.source(); var destination = fixture.destination();
        assertTrue(EscrowGovernedPhaseAction.matches(record, source, destination, 1));
        var first = source.getList("passengers", 10).getCompound(0).copy(); destination.getList("passengers", 10).add(first);
        assertFalse(EscrowGovernedPhaseAction.matches(record, source, destination, 1));
        source = EscrowGovernedPhaseAction.withoutRider(source, first.getUUID("physical"));
        assertTrue(EscrowGovernedPhaseAction.matches(record, source, destination, 1));
        var changed = source.copy(); changed.getCompound("wrapper").putLong("resource_epoch", 8);
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedPhaseAction.matches(record, changed, destination, 1));
        var changedDestination = destination.copy(); changedDestination.getCompound("wrapper").putLong("resource_epoch", 1);
        assertFalse(EscrowGovernedPhaseAction.matches(record, source, changedDestination, 1));
        destination.getList("passengers", 10).add(first.copy()); var exactSource = source;
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedPhaseAction.matches(record, exactSource, destination, 1));
    }

    @Test void sourceAbsenceOnlyFollowsItsRemovalAcknowledgementAndNeverCompletesTheArm() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        assertFalse(EscrowGovernedPhaseAction.matches(record, null, fixture.destination(), 200));
        for (var phase : java.util.List.of(RocketTransferPhase.SOURCE_REMOVED, RocketTransferPhase.COMMITTED)) {
            record = record.advance(phase); var journal = new RocketTransferSavedData();
            try {
                journal.put(record); var authority = acknowledge(journal, record);
                issue(authority, null, fixture.destination(), () -> true).consume();
                assertEquals(EscrowGovernedDispatch.Route.ACTIVE, authority.decision().route());
                assertEquals(EscrowFlightTables.Outcome.ARMED, authority.ledger().armedFlights().get(0).outcome());
                assertFalse(EscrowGovernedPhaseAction.matches(record, null, null, 200));
            } finally { journal.stop(); }
        }
    }

    @Test void callbackRechecksAuthorityCensusAndBothCompleteEnvelopes() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED);
        var journal = new RocketTransferSavedData();
        try {
            journal.put(record); var authority = acknowledge(journal, record);
            for (int fault = 0; fault < 3; fault++) {
                var source = fixture.source().copy(); var destination = fixture.destination().copy(); var boundary = new AtomicBoolean(true);
                var permit = issue(authority, source, destination, boundary::get);
                if (fault == 0) { boundary.set(false); }
                if (fault == 1) { source.putBoolean("invulnerable", true); }
                if (fault == 2) { destination.putUUID("owner", GovernedTransferFixture.id(999)); }
                assertThrows(IllegalArgumentException.class, permit::consume);
                assertThrows(IllegalArgumentException.class, permit::consume);
            }
            var permit = issue(authority, fixture.source(), fixture.destination(), () -> true);
            journal.put(record.advance(RocketTransferPhase.SOURCE_REMOVED));
            assertThrows(IllegalArgumentException.class, permit::consume);
        } finally { journal.stop(); }
    }

    @Test void manifestLimitIsAcceptedAndExtraUnknownOrMalformedRidersAreRefused() {
        var fixture = GovernedTransferFixture.create(1, 16, false); var record = spawned(fixture);
        assertEquals(16, EscrowGovernedPhaseAction.riders(record, fixture.source()).size());
        var overflow = fixture.source().copy(); overflow.getList("passengers", 10).add(overflow.getList("passengers", 10).get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedPhaseAction.riders(record, overflow));
        for (int fault = 0; fault < 3; fault++) {
            var source = fixture.source().copy(); var rider = source.getList("passengers", 10).getCompound(0);
            if (fault == 0) { rider.putUUID("physical", GovernedTransferFixture.id(999)); }
            if (fault == 1) { rider.putString("extra", "invalid"); }
            if (fault == 2) { rider.remove("physical"); }
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedPhaseAction.riders(record, source));
        }
    }

    @Test void twoEndpointCapturesPerActiveTransferFitAndOneMoreIsRefusedWithoutMutation() {
        var captures = new java.util.HashMap<java.util.UUID, String>();
        for (int index = 0; index < 128; index++) {
            EscrowGovernedPhaseTracking.reserve(captures, GovernedTransferFixture.id(index), () -> "frozen");
        }
        assertEquals(128, captures.size());
        EscrowGovernedPhaseTracking.reserve(captures, GovernedTransferFixture.id(0), () -> { fail("Existing capture replaced"); return "invalid"; });
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedPhaseTracking.reserve(captures,
                GovernedTransferFixture.id(128), () -> { fail("Overflow capture materialized"); return "invalid"; }));
        assertEquals(128, captures.size()); assertEquals("frozen", captures.get(GovernedTransferFixture.id(0)));
    }

    @Test void authorizedDestinationReconstructionAfterRemovalStillRequiresBothSlotProofs() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = spawned(fixture).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED); var journal = new RocketTransferSavedData();
        try {
            journal.put(record); var authority = acknowledge(journal, record);
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedDestinationAction.issue(authority, null, 200, 200,
                    () -> null, () -> true, () -> false));
            var permit = EscrowGovernedDestinationAction.issue(authority, null, 200, 200, () -> null, () -> true, () -> true);
            assertEquals(record, permit.consume(() -> true));
            for (boolean destination : java.util.List.of(false, true)) {
                var slot = EscrowGovernedDestinationDisk.endpoint(record, destination);
                var validator = EscrowGovernedDestinationDisk.validator(record, slot, null);
                assertTrue(validator.test(EscrowGovernedDestinationDiskTest.slot(slot)));
                assertFalse(validator.test(EscrowGovernedDestinationDiskTest.slot(slot,
                        EscrowGovernedDestinationDiskTest.nativeEntity(fixture.source()))));
                assertFalse(validator.test(EscrowGovernedDestinationDiskTest.slot(slot,
                        EscrowGovernedDestinationDiskTest.nativeEntity(fixture.destination()))));
            }
        } finally { journal.stop(); }
    }

    private EscrowGovernedDispatch.Authority acknowledge(RocketTransferSavedData journal, RocketTransferRecord record) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
        var root = EscrowGovernedDispatchTest.root(record);
        var authority = EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, record); assertNotNull(authority); return authority;
    }
    private static EscrowGovernedPhaseAction.Permit issue(EscrowGovernedDispatch.Authority authority, CompoundTag source,
            CompoundTag destination, java.util.function.BooleanSupplier boundary) {
        return EscrowGovernedPhaseAction.issue(authority, source, destination, 200, () -> source, () -> destination, boundary);
    }
    private static RocketTransferRecord spawned(GovernedTransferFixture.Fixture fixture) {
        return fixture.record().destinationSpawned(fixture.record().nativeTransfer().orElseThrow().armed().destination());
    }
}

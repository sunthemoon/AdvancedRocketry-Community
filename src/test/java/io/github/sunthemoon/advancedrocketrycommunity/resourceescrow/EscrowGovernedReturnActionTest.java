package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedReturnActionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void bothEarlyPhasesReturnExactResourcesWithoutFinishingTheSerial() throws Exception {
        for (boolean f4 : List.of(false, true)) {
            try (var state = state(f4)) {
                var before = EscrowGovernedDestinationActionTest.transit(state.fixture);
                var permit = issue(state, before, () -> true);
                var change = permit.consume(before::copy);
                assertEquals(EscrowGovernedReturnAction.flight(before).fuel(), change.flight().fuel());
                assertEquals(EscrowGovernedReturnAction.flight(before).passengers(), change.flight().passengers());
                var after = before.copy(); after.put("flight", RocketFlightNbtCodec.encode(change.flight()));
                after.put("pos", GovernedTransferFixture.vector(change.position().x, change.position().y, change.position().z));
                assertTrue(EscrowGovernedReturnAction.returned(state.record, after));
                assertFalse(EscrowGovernedReturnAction.returned(state.record, state.fixture.source()));
                assertEquals(EscrowFlightTables.Outcome.ARMED, state.root.get().armedFlights().get(0).outcome());
                assertEquals(state.record, state.journal.find(state.record.transferId()).orElseThrow());
                assertThrows(IllegalArgumentException.class, () -> permit.consume(before::copy));
            }
        }
    }

    @Test void loadedAbsenceWithoutJointStorageProofNeverIssuesAReturn() throws Exception {
        try (var state = state(true)) {
            assertThrows(IllegalArgumentException.class, () -> issue(state, state.fixture.source(), () -> false));
        }
    }

    @Test void supersededStorageTokenConsumesAndRefusesThePermit() throws Exception {
        try (var state = state(true)) {
            var valid = new AtomicBoolean(true);
            var permit = issue(state, state.fixture.source(), valid::get);
            valid.set(false);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture.source()::copy));
            valid.set(true);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture.source()::copy));
        }
    }

    @Test void changedNativeCreditAndUnknownRidersRefuseBeforeMutation() throws Exception {
        try (var state = state(true)) {
            var changed = state.fixture.source().copy();
            changed.getCompound("wrapper").putLong("resource_epoch", 8);
            assertThrows(IllegalArgumentException.class, () -> issue(state, changed, () -> true));
            var foreign = state.fixture.source().copy();
            foreign.getList("passengers", 10).getCompound(0).putUUID("physical", GovernedTransferFixture.id(999));
            assertThrows(IllegalArgumentException.class, () -> issue(state, foreign, () -> true));
            var permit = issue(state, state.fixture.source(), () -> true);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(() -> changed));
        }
    }

    @Test void physicalDismountIsAllowedButAssignmentAndEveryOtherFieldRemainExact() throws Exception {
        try (var state = state(true)) {
            var source = state.fixture.source().copy(); source.getList("passengers", 10).clear();
            var result = issue(state, source, () -> true).consume(source::copy);
            var returned = source.copy(); returned.put("flight", RocketFlightNbtCodec.encode(result.flight()));
            assertTrue(EscrowGovernedReturnAction.returned(state.record, returned));
            assertEquals(1, result.flight().passengers().assignments().size());
            returned.putBoolean("invulnerable", true);
            assertFalse(EscrowGovernedReturnAction.returned(state.record, returned));
        }
    }

    @Test void laterAuthorityPhasesCannotCancelEvenWithAnAbsencePredicate() throws Exception {
        try (var state = state(true)) {
            var previous = state.record;
            for (var phase : List.of(RocketTransferPhase.PASSENGERS_TRANSFERRED, RocketTransferPhase.SOURCE_REMOVED,
                    RocketTransferPhase.COMMITTED)) {
                var record = previous.advance(phase); previous = record; state.journal.put(record); acknowledge(state.journal);
                var authority = EscrowGovernedDispatch.acquire(state.root::get, () -> true, state.journal, record);
                assertThrows(IllegalArgumentException.class, () -> EscrowGovernedReturnAction.issue(authority,
                        state.fixture.source(), 200, 200, state.fixture.source()::copy, () -> true, () -> true));
            }
        }
    }

    @Test void supersededJournalAndLedgerInvalidateDeferredReturns() throws Exception {
        try (var state = state(true)) {
            var first = issue(state, state.fixture.source(), () -> true);
            state.journal.setDirty();
            assertThrows(IllegalArgumentException.class, () -> first.consume(state.fixture.source()::copy));
            acknowledge(state.journal);
            var second = issue(state, state.fixture.source(), () -> true);
            state.root.set(state.root.get().generation(state.root.get().writerGeneration() + 1));
            assertThrows(IllegalArgumentException.class, () -> second.consume(state.fixture.source()::copy));
        }
    }

    @Test void returnedProjectionSurvivesRolledBackLevelClockButNotInitialOrOtherTransfer() throws Exception {
        try (var state = state(true)) {
            var source = EscrowGovernedDestinationActionTest.transit(state.fixture);
            var authority = EscrowGovernedDispatch.acquire(state.root::get, () -> true, state.journal, state.record);
            var change = EscrowGovernedReturnAction.issue(authority, source, 0, 0, source::copy, () -> true, () -> true).consume(source::copy);
            assertTrue(change.flight().stateStartedGameTime() >= 141);
            var returned = source.copy(); returned.put("flight", RocketFlightNbtCodec.encode(change.flight()));
            returned.put("pos", state.fixture.source().get("pos").copy());
            assertTrue(EscrowGovernedReturnAction.returned(state.record, returned));
            returned.putUUID("physical", GovernedTransferFixture.id(999));
            assertFalse(EscrowGovernedReturnAction.returned(state.record, returned));
        }
    }

    private EscrowGovernedReturnAction.Permit issue(State state, CompoundTag source, java.util.function.BooleanSupplier proof) {
        return EscrowGovernedReturnAction.issue(EscrowGovernedDispatch.acquire(state.root::get, () -> true, state.journal, state.record),
                source, 200, 200, source::copy, () -> true, proof);
    }
    private State state(boolean f4) throws Exception {
        var fixture = GovernedTransferFixture.create();
        var record = f4 ? fixture.record().destinationSpawned(fixture.record().nativeTransfer().orElseThrow().armed().destination()) : fixture.record();
        var journal = new RocketTransferSavedData();
        try {
            journal.put(record); acknowledge(journal);
            return new State(fixture, record, new AtomicReference<>(EscrowGovernedDispatchTest.root(record)), journal);
        } catch (Exception failure) { journal.stop(); throw failure; }
    }
    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
    }
    private record State(GovernedTransferFixture.Fixture fixture, RocketTransferRecord record, AtomicReference<EscrowLedger> root,
                         RocketTransferSavedData journal) implements AutoCloseable {
        @Override public void close() { journal.stop(); }
    }
}

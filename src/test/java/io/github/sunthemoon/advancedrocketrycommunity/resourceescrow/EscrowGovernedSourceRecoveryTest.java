package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedSourceRecovery.Action.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedSourceRecoveryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void exactCheckpointAndAllSourceProgressionsResumeWithoutChangingAnyField() throws Exception {
        try (var state = state()) {
            for (var observed : progressions(state.fixture)) {
                var before = observed.copy();
                var permit = issue(state, RESUME_EXACT_SOURCE, observed);
                var change = permit.consume(observed::copy);
                assertEquals(flight(observed), change.flight());
                var pos = observed.getList("pos", Tag.TAG_DOUBLE);
                assertEquals(pos.getDouble(0), change.position().x);
                assertEquals(pos.getDouble(1), change.position().y);
                assertEquals(pos.getDouble(2), change.position().z);
                assertEquals(before, observed);
                assertThrows(IllegalArgumentException.class, () -> permit.consume(observed::copy));
            }
        }
    }

    @Test void returnChangesOnlyNonResourceFlightAndPositionAndDoesNotFinishOrPrune() throws Exception {
        try (var state = state()) {
            var root = EscrowCodec.encode(state.root.get()); var journal = state.journal.save(new CompoundTag());
            for (var observed : progressions(state.fixture)) {
                var before = observed.copy(); var current = flight(observed);
                var change = issue(state, RETURN_EXACT_SOURCE, observed).consume(observed::copy);
                assertEquals(RocketFlightState.FUELED, change.flight().state());
                assertEquals(current.fuel(), change.flight().fuel());
                assertEquals(current.fuel().committedDebits(), change.flight().fuel().committedDebits());
                assertEquals(current.passengers(), change.flight().passengers());
                assertEquals(current.currentTarget(), change.flight().currentTarget());
                assertEquals(current.currentBody(), change.flight().currentBody());
                assertEquals(current.currentDimension(), change.flight().currentDimension());
                assertEquals(current.currentOrigin(), change.flight().currentOrigin());
                assertEquals(200, change.flight().stateStartedGameTime());
                assertTrue(change.flight().plan().isEmpty()); assertTrue(change.flight().activeTransferId().isEmpty());
                assertEquals(80, change.position().y); assertEquals(before, observed);
            }
            assertEquals(root, EscrowCodec.encode(state.root.get()));
            assertEquals(journal, state.journal.save(new CompoundTag()));
            assertEquals(EscrowFlightTables.Outcome.ARMED, state.root.get().armedFlights().get(0).outcome());
            assertTrue(state.journal.acknowledgedCurrent());
        }
    }

    @Test void typedSourceAuthorityNeverExemptsAnyLegacyElection() throws Exception {
        try (var state = state()) {
            var record = state.fixture.record();
            var credited = new EscrowRecoveryFence.Rocket(record.sourceEntityId(), 7, false, true);
            for (var action : RocketTransferRecoveryAction.values()) {
                assertEquals("ARMED_AUTHORITY_REQUIRED", EscrowRecoveryFence.reason(state.root.get(), record.logicalRocketId(),
                        record.transferId(), action, List.of(credited), List.of()));
            }
            assertNull(reason(state, RESUME_EXACT_SOURCE, observation(state.fixture.source())));
            assertEquals("FLOOR_CONFLICT", EscrowRecoveryFence.reason(EscrowLedger.empty(state.root.get().id()),
                    record.logicalRocketId(), record.transferId(), RocketTransferRecoveryAction.KEEP_SOURCE, List.of(credited), List.of()));
        }
    }

    @Test void missingDuplicateDestinationAndUnloadedCensusDoNotGrantAResumeOrReturn() throws Exception {
        try (var state = state()) {
            var source = state.fixture.source();
            for (var action : EscrowGovernedSourceRecovery.Action.values()) {
                assertEquals("WAITING_UNLOADED", reason(state, action, new EscrowGovernedSourceRecovery.Observation(false, List.of(source), List.of())));
                assertEquals("MISSING_ENDPOINT", reason(state, action, new EscrowGovernedSourceRecovery.Observation(true, List.of(), List.of())));
                assertEquals("GOVERNED_ENDPOINT_CONFLICT", reason(state, action,
                        new EscrowGovernedSourceRecovery.Observation(true, List.of(source, source), List.of())));
                assertEquals("GOVERNED_ENDPOINT_CONFLICT", reason(state, action,
                        new EscrowGovernedSourceRecovery.Observation(true, List.of(source), List.of(state.fixture.destination()))));
            }
        }
    }

    @Test void censusAtTheLimitIsIncompleteAndOverTheLimitRefusesBeforeCopies() throws Exception {
        try (var state = state()) {
            var values = java.util.Collections.nCopies(64, state.fixture.source());
            assertEquals("MATCH_LIMIT", reason(state, RESUME_EXACT_SOURCE, new EscrowGovernedSourceRecovery.Observation(true, values, List.of())));
            assertEquals("MATCH_LIMIT", reason(state, RETURN_EXACT_SOURCE, new EscrowGovernedSourceRecovery.Observation(true, List.of(), values)));
            assertThrows(IllegalArgumentException.class, () -> new EscrowGovernedSourceRecovery.Observation(true,
                    java.util.Collections.nCopies(65, state.fixture.source()), List.of()));
            assertThrows(IllegalArgumentException.class, () -> new EscrowGovernedSourceRecovery.Observation(true,
                    List.of(), java.util.Collections.nCopies(65, state.fixture.source())));
            assertEquals("GOVERNED_ENDPOINT_CONFLICT", reason(state, RESUME_EXACT_SOURCE,
                    new EscrowGovernedSourceRecovery.Observation(true, values.subList(0, 63), List.of())));
        }
    }

    @Test void everyEnvelopeFieldIsRequiredButAssignedRidersMayDismount() throws Exception {
        try (var state = state()) {
            var source = state.fixture.source();
            for (String key : source.getAllKeys()) {
                var changed = source.copy(); changed.remove(key);
                assertNotNull(reason(state, RESUME_EXACT_SOURCE, observation(changed)), key);
                assertNotNull(reason(state, RETURN_EXACT_SOURCE, observation(changed)), key);
            }
            var extra = source.copy(); extra.putBoolean("unrecognized", true);
            assertNotNull(reason(state, RESUME_EXACT_SOURCE, observation(extra)));
            var dismounted = source.copy(); dismounted.put("passengers", new net.minecraft.nbt.ListTag());
            assertNull(reason(state, RETURN_EXACT_SOURCE, observation(dismounted)), "R18 accepts assigned dismounts without changing the manifest");
        }
    }

    @Test void sameTotalDifferentBanksAndDebitHistoryCannotUseThePresentSourcePermit() throws Exception {
        try (var state = state()) {
            var source = state.fixture.source(); var current = flight(source); var fuel = current.fuel();
            var banks = fuel.propulsion().orElseThrow();
            var swapped = fuel.withFluidBank(false, banks.primary().fluidId(), null, 1000)
                    .withFluidBank(true, banks.oxidizer().fluidId(), banks.primary().fluidTag(), 1000);
            assertEquals(fuel.totalAmount(), swapped.totalAmount());
            var debited = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.restorePropulsion(
                    banks, List.of(state.fixture.record().transferId()));
            for (var changedFuel : List.of(swapped, debited)) {
                var changed = source.copy(); changed.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(current, changedFuel)));
                assertNotNull(reason(state, RESUME_EXACT_SOURCE, observation(changed)));
                assertNotNull(reason(state, RETURN_EXACT_SOURCE, observation(changed)));
            }
        }
    }

    @Test void invalidPositionAndPrematureOrFuturePhaseClocksStayFenced() throws Exception {
        try (var state = state()) {
            for (var source : progressions(state.fixture)) {
                var sideways = source.copy(); sideways.put("pos", GovernedTransferFixture.vector(8.6, 80, 8.5));
                assertNotNull(reason(state, RESUME_EXACT_SOURCE, observation(sideways)));
                var wrongColumn = source.copy(); wrongColumn.putIntArray("chunk", new int[] {1, 0});
                assertNotNull(reason(state, RETURN_EXACT_SOURCE, observation(wrongColumn)));
            }
            var ascent = progressions(state.fixture).get(2);
            for (double height : new double[] {79.99, 128.01, Double.NaN, Double.POSITIVE_INFINITY}) {
                var changed = ascent.copy(); changed.put("pos", GovernedTransferFixture.vector(8.5, height, 8.5));
                assertNotNull(reason(state, RESUME_EXACT_SOURCE, observation(changed)));
            }
            assertNotNull(EscrowRecoveryFence.reason(state.authority, RESUME_EXACT_SOURCE, observation(ascent), 60));
            var early = ascent.copy(); var initial = flight(state.fixture.source());
            early.put("flight", RocketFlightNbtCodec.encode(initial.withPlan(state.fixture.record().sourceFlightData().plan().orElseThrow())
                    .startCountdown(1).completeCountdown(2)));
            assertNotNull(reason(state, RESUME_EXACT_SOURCE, observation(early)));
            var tooHigh = ascent.copy(); tooHigh.put("pos", GovernedTransferFixture.vector(8.5, 81, 8.5));
            assertNotNull(EscrowRecoveryFence.reason(state.authority, RESUME_EXACT_SOURCE, observation(tooHigh), 61));
        }
    }

    @Test void laterAuthorityPhasesNeedTheirOwnPublicationAndAbsenceProofEvenIfRamIsEmpty() throws Exception {
        try (var state = state()) {
            var record = state.fixture.record().destinationSpawned(state.fixture.record().nativeTransfer().orElseThrow().armed().destination());
            for (var phase : List.of(RocketTransferPhase.DESTINATION_SPAWNED, RocketTransferPhase.PASSENGERS_TRANSFERRED,
                    RocketTransferPhase.SOURCE_REMOVED, RocketTransferPhase.COMMITTED)) {
                if (record.phase() != phase) { record = record.advance(phase); }
                state.journal.put(record); acknowledge(state.journal);
                var authority = EscrowGovernedDispatch.acquire(state.root::get, () -> true, state.journal, record);
                for (var action : EscrowGovernedSourceRecovery.Action.values()) {
                    assertEquals("GOVERNED_SOURCE_PHASE_MISMATCH", EscrowRecoveryFence.reason(authority, action, observation(state.fixture.source()), 200));
                }
            }
        }
    }

    @Test void finalCensusAndBoundaryRepeatAndRefusedPermitsAreSpent() throws Exception {
        try (var state = state()) {
            var observed = new AtomicReference<>(observation(state.fixture.source())); var allowed = new AtomicBoolean(true);
            var permit = EscrowGovernedSourceRecovery.issue(state.authority, RETURN_EXACT_SOURCE, observed::get, 200, allowed::get);
            observed.set(new EscrowGovernedSourceRecovery.Observation(true, List.of(state.fixture.source()), List.of(state.fixture.destination())));
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture.source()::copy));
            observed.set(observation(state.fixture.source()));
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture.source()::copy));
            var second = EscrowGovernedSourceRecovery.issue(state.authority, RETURN_EXACT_SOURCE, observed::get, 200, allowed::get);
            allowed.set(false);
            assertThrows(IllegalArgumentException.class, () -> second.consume(state.fixture.source()::copy));
            allowed.set(true);
            assertThrows(IllegalArgumentException.class, () -> second.consume(state.fixture.source()::copy));
        }
    }

    @Test void changedActualEntityCannotBeHiddenByAnUnchangedCensus() throws Exception {
        try (var state = state()) {
            var permit = issue(state, RETURN_EXACT_SOURCE, state.fixture.source());
            var changed = state.fixture.source().copy(); changed.putUUID("physical", GovernedTransferFixture.id(999));
            assertThrows(IllegalArgumentException.class, () -> permit.consume(() -> changed));
        }
    }

    @Test void supersededJournalLedgerAndTerminalOutcomeInvalidateDeferredActions() throws Exception {
        try (var state = state()) {
            var permit = issue(state, RETURN_EXACT_SOURCE, state.fixture.source());
            state.journal.setDirty();
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture.source()::copy));
            acknowledge(state.journal);
            var authority = EscrowGovernedDispatch.acquire(state.root::get, () -> true, state.journal, state.fixture.record());
            var next = EscrowGovernedSourceRecovery.issue(authority, RESUME_EXACT_SOURCE,
                    () -> observation(state.fixture.source()), 200, () -> true);
            state.root.set(state.root.get().generation(state.root.get().writerGeneration() + 1));
            assertThrows(IllegalArgumentException.class, () -> next.consume(state.fixture.source()::copy));
            state.root.set(EscrowGovernedDispatchTest.arm(state.root.get(), tag -> tag.putString("outcome", "CANCELLED")));
            var terminal = EscrowGovernedDispatch.acquire(state.root::get, () -> true, state.journal, state.fixture.record());
            assertEquals("GOVERNED_ACTIVE_ACTION_REQUIRED", EscrowRecoveryFence.reason(terminal, RESUME_EXACT_SOURCE,
                    observation(state.fixture.source()), 200));
        }
    }

    @Test void copiedCensusCannotBeRewrittenByItsCaller() throws Exception {
        try (var state = state()) {
            var source = state.fixture.source().copy(); var list = new ArrayList<>(List.of(source));
            var census = new EscrowGovernedSourceRecovery.Observation(true, list, List.of());
            source.remove("wrapper"); list.clear(); census.source().remove("snapshot");
            assertNull(reason(state, RESUME_EXACT_SOURCE, census));
        }
    }

    @Test void ambiguousCensusDoesNotCopyPayloadsButUniqueSourceStillHasTheEnvelopeBound() throws Exception {
        try (var state = state()) {
            var oversized = new CompoundTag(); oversized.putByteArray("oversized", new byte[EscrowNativeEnvelope.MAX_BYTES + 1]);
            var ambiguous = new EscrowGovernedSourceRecovery.Observation(true, java.util.Collections.nCopies(64, oversized), List.of());
            assertNull(ambiguous.source()); assertEquals("MATCH_LIMIT", reason(state, RESUME_EXACT_SOURCE, ambiguous));
            assertThrows(IllegalArgumentException.class, () -> observation(oversized));
        }
    }

    @Test void stoppedAndAbortedJournalsInvalidateAlreadyIssuedPermits() throws Exception {
        for (boolean abort : List.of(false, true)) {
            try (var state = state()) {
                var permit = issue(state, RETURN_EXACT_SOURCE, state.fixture.source());
                if (abort) { state.journal.abort(); } else { state.journal.stop(); }
                assertFalse(state.journal.actionAuthorityAvailable());
                assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture.source()::copy));
                assertNull(EscrowGovernedDispatch.acquire(state.root::get, () -> true, state.journal, state.fixture.record()));
            }
        }
    }

    @Test void wrongThreadCannotConsumeOrReuseThePermit() throws Exception {
        try (var state = state()) {
            var permit = issue(state, RESUME_EXACT_SOURCE, state.fixture.source());
            var failure = new AtomicReference<Throwable>();
            var thread = new Thread(() -> {
                try { permit.consume(state.fixture.source()::copy); }
                catch (Throwable refused) { failure.set(refused); }
            });
            thread.start(); thread.join(5000);
            assertFalse(thread.isAlive()); assertInstanceOf(IllegalArgumentException.class, failure.get());
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture.source()::copy));
        }
    }

    private EscrowGovernedSourceRecovery.Permit issue(State state, EscrowGovernedSourceRecovery.Action action, CompoundTag source) {
        return EscrowGovernedSourceRecovery.issue(state.authority, action, () -> observation(source), 200, () -> true);
    }
    private static String reason(State state, EscrowGovernedSourceRecovery.Action action, EscrowGovernedSourceRecovery.Observation observation) {
        return EscrowRecoveryFence.reason(state.authority, action, observation, 200);
    }
    private static EscrowGovernedSourceRecovery.Observation observation(CompoundTag source) {
        return new EscrowGovernedSourceRecovery.Observation(true, List.of(source), List.of());
    }
    private static List<CompoundTag> progressions(GovernedTransferFixture.Fixture fixture) {
        var initial = flight(fixture.source());
        var countdown = initial.withPlan(fixture.record().sourceFlightData().plan().orElseThrow()).startCountdown(1);
        var ascent = countdown.completeCountdown(61); var transit = ascent.beginTransit(fixture.record().transferId(), 141);
        var result = new ArrayList<CompoundTag>(); result.add(fixture.source().copy());
        for (var flight : List.of(countdown, ascent, transit)) {
            var value = fixture.source().copy(); value.put("flight", RocketFlightNbtCodec.encode(flight));
            value.put("pos", GovernedTransferFixture.vector(8.5, flight.state() == RocketFlightState.COUNTDOWN ? 80
                    : flight.state() == RocketFlightState.ASCENT ? 104 : 128, 8.5));
            result.add(value);
        }
        return result;
    }
    private static RocketFlightData flight(CompoundTag value) {
        return RocketFlightNbtCodec.decode(value.getCompound("flight")).data().orElseThrow();
    }
    private State state() throws Exception {
        var fixture = GovernedTransferFixture.create(); var journal = new RocketTransferSavedData();
        try {
            journal.put(fixture.record()); acknowledge(journal);
            var root = new AtomicReference<>(EscrowGovernedDispatchTest.root(fixture.record()));
            return new State(fixture, root, journal, EscrowGovernedDispatch.acquire(root::get, () -> true, journal, fixture.record()));
        } catch (Exception failure) { journal.stop(); throw failure; }
    }
    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
    }
    private record State(GovernedTransferFixture.Fixture fixture, AtomicReference<EscrowLedger> root,
                         RocketTransferSavedData journal, EscrowGovernedDispatch.Authority authority) implements AutoCloseable {
        @Override public void close() { journal.stop(); }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedPreparationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void acknowledgedF2StartsOnlyCountdownWithoutDebitOrSourceReplacement() throws Exception {
        try (var state = state()) {
            var initial = RocketFlightNbtCodec.decode(state.fixture.source().getCompound("flight")).data().orElseThrow();
            var permit = EscrowGovernedPreparation.countdownPermit(state.authority(), state.fixture.source(), 20, () -> true);
            assertNotNull(permit);
            var after = permit.consume(state.fixture::source);
            assertEquals(RocketFlightState.COUNTDOWN, after.state()); assertEquals(20, after.stateStartedGameTime());
            assertEquals(initial.fuel(), after.fuel()); assertEquals(initial.passengers(), after.passengers());
            assertEquals(state.fixture.record().sourceFlightData().plan(), after.plan());
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture::source));
        }
    }

    @Test void unacknowledgedOrStoppedJournalDoesNotIssueACountdownPermit() throws Exception {
        try (var state = state()) {
            state.journal.setDirty();
            assertNull(EscrowGovernedPreparation.countdownPermit(state.authority(), state.fixture.source(), 20, () -> true));
            acknowledge(state.journal); state.journal.stop();
            assertNull(EscrowGovernedPreparation.countdownPermit(state.authority(), state.fixture.source(), 20, () -> true));
        }
    }

    @Test void finalBoundaryChangeConsumesPermitWithoutPublishing() throws Exception {
        try (var state = state()) {
            var boundary = new AtomicBoolean(true);
            var permit = EscrowGovernedPreparation.countdownPermit(state.authority(), state.fixture.source(), 20, boundary::get);
            assertNotNull(permit); boundary.set(false);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture::source));
            boundary.set(true);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture::source));
        }
    }

    @Test void invalidatedAuthorityCannotRunDeferredCountdown() throws Exception {
        try (var state = state(); var dispatcher = new EscrowGovernedDispatch()) {
            var authority = dispatcher.authority(() -> state.root, () -> true, state.journal, state.fixture.record());
            var permit = EscrowGovernedPreparation.countdownPermit(authority, state.fixture.source(), 20, () -> true);
            assertNotNull(permit); dispatcher.invalidated(state.fixture.record().logicalRocketId());
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture::source));
            assertNull(EscrowGovernedPreparation.countdownPermit(authority, state.fixture.source(), 20, () -> true));
        }
    }

    @Test void changedCompleteSourceAndFalseBoundaryKeepTheHold() throws Exception {
        try (var state = state()) {
            for (String field : List.of("flight", "wrapper", "passengers", "snapshot", "pos", "motion", "owner")) {
                var changed = state.fixture.source().copy(); changed.remove(field);
                assertNull(EscrowGovernedPreparation.countdownPermit(state.authority(), changed, 20, () -> true), field);
            }
            assertNull(EscrowGovernedPreparation.countdownPermit(state.authority(), state.fixture.source(), 20, () -> false));
            assertTrue(state.journal.acknowledgedCurrent()); assertEquals(1, state.root.armedFlights().size());
        }
    }

    @Test void supersededJournalLeaseCannotPublishTheOldCallback() throws Exception {
        try (var state = state()) {
            var permit = EscrowGovernedPreparation.countdownPermit(state.authority(), state.fixture.source(), 20, () -> true);
            assertNotNull(permit); state.journal.remove(state.fixture.record().transferId()); acknowledge(state.journal);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(state.fixture::source));
            assertNull(state.authority());
        }
    }

    private State state() throws Exception {
        var fixture = GovernedTransferFixture.create(); var arm = fixture.record().nativeTransfer().orElseThrow().armed();
        var root = new EscrowLedger(arm.ledger(), 0, arm.generation(), List.of(), List.of(), List.of(),
                arm.serial(), List.of(), List.of(arm));
        var journal = new RocketTransferSavedData(); journal.put(fixture.record()); acknowledge(journal);
        return new State(fixture, root, journal);
    }
    private void acknowledge(RocketTransferSavedData journal) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
    }
    private record State(GovernedTransferFixture.Fixture fixture, EscrowLedger root, RocketTransferSavedData journal) implements AutoCloseable {
        EscrowGovernedDispatch.Authority authority() { return EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, fixture.record()); }
        @Override public void close() { journal.stop(); }
    }
}

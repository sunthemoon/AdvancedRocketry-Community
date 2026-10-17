package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedSourceAction.Action.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowGovernedSourceActionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void threeOneUseTransitionsPreserveEveryResourceAndTheSourceWrapper() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var root = EscrowGovernedDispatchTest.root(record); var journal = new RocketTransferSavedData(); journal.put(record);
        try {
            var authority = acknowledge(root, journal, record);
            var observed = fixture.source(); var original = observed.copy();
            var countdown = EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, observed, 1, () -> true);
            var started = countdown.consume(observed::copy); assertEquals(RocketFlightState.COUNTDOWN, started.state());
            assertThrows(IllegalArgumentException.class, () -> countdown.consume(observed::copy));
            observed.put("flight", RocketFlightNbtCodec.encode(started));
            var ascent = EscrowGovernedSourceAction.issue(authority, COMPLETE_COUNTDOWN, observed, 61, () -> true).consume(observed::copy);
            assertEquals(RocketFlightState.ASCENT, ascent.state()); observed.put("flight", RocketFlightNbtCodec.encode(ascent));
            var origin = record.sourceSnapshot().sourceOrigin();
            observed.put("pos", GovernedTransferFixture.vector(origin.x() + 0.5, origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS, origin.z() + 0.5));
            var transit = EscrowGovernedSourceAction.issue(authority, BEGIN_TRANSIT, observed, 141, () -> true).consume(observed::copy);
            assertEquals(RocketFlightState.TRANSIT, transit.state());
            assertEquals(record.transferId(), transit.activeTransferId().orElseThrow());
            assertEquals(record.sourceFlightData().fuel(), transit.fuel());
            assertEquals(original.get("wrapper"), observed.get("wrapper"));
            assertEquals(record.sourceFlightData().passengers().assignments(), transit.passengers().assignments());
            assertFalse(transit.fuel().committedDebits().contains(record.transferId()), "The source never pays the destination debit");
        } finally { journal.stop(); }
    }

    @Test void completeEnvelopeDifferencesCannotUseAResourceOnlyOrIdentityOnlyExemption() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var root = EscrowGovernedDispatchTest.root(record); var journal = new RocketTransferSavedData(); journal.put(record);
        try {
            var authority = acknowledge(root, journal, record);
            for (String field : List.of("physical", "owner", "logical", "assembly_snapshot")) {
                var changed = fixture.source().copy(); changed.putUUID(field, GovernedTransferFixture.id(900));
                assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, changed, 1, () -> true), field);
            }
            for (String field : List.of("wrapper", "snapshot", "passengers", "motion")) {
                var changed = fixture.source().copy(); changed.remove(field);
                assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, changed, 1, () -> true), field);
            }
            var changed = fixture.source().copy(); var fuel = flight(changed).fuel();
            var refuelled = fuel.withFluidBank(false, fuel.propulsion().orElseThrow().primary().fluidId(),
                    fuel.propulsion().orElseThrow().primary().fluidTag(), 999);
            changed.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight(changed), refuelled)));
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, changed, 1, () -> true));
        } finally { journal.stop(); }
    }

    @Test void acknowledgedLandedSuccessorStartsWithoutRewritingItsF1Checkpoint() throws Exception {
        var fixture = EscrowGovernedLaunchTest.landed(); var initial = flight(fixture.source());
        var baseline = EscrowLedger.empty(fixture.record().nativeTransfer().orElseThrow().armed().ledger());
        var countdown = initial.withFuel(initial.fuel(), initial.stateStartedGameTime())
                .withPlan(fixture.record().sourceFlightData().plan().orElseThrow()).startCountdown(2);
        var prepared = EscrowGovernedLaunch.prepare(baseline, fixture.source(), fixture.record().sourceSnapshot(), countdown,
                fixture.record().destinationSnapshot(), fixture.record().nativeTransfer().orElseThrow().armed().destination(),
                fixture.record().nativeTransfer().orElseThrow().armed().journal());
        var root = prepared.preflight(baseline, 1).generation(1); var record = prepared.record(root.armedFlights().get(0));
        var journal = new RocketTransferSavedData(); journal.put(record);
        try {
            var authority = acknowledge(root, journal, record); var original = fixture.source().copy();
            var after = EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, fixture.source(), 2, () -> true)
                    .consume(fixture.source()::copy);
            assertEquals(countdown, after); assertEquals(initial.fuel(), after.fuel());
            assertEquals(original, fixture.source());
            assertEquals(EscrowGovernedEnvelope.comparison(original), record.nativeTransfer().orElseThrow().sourceEnvelope(record));
        } finally { journal.stop(); }
    }

    @Test void phaseTimersAndExactTopOfAscentAreRequired() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var root = EscrowGovernedDispatchTest.root(record); var journal = new RocketTransferSavedData(); journal.put(record);
        try {
            var authority = acknowledge(root, journal, record); var observed = fixture.source();
            var countdown = EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, observed, 1, () -> true).consume(observed::copy);
            observed.put("flight", RocketFlightNbtCodec.encode(countdown));
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(authority, COMPLETE_COUNTDOWN, observed, 60, () -> true));
            var ascent = EscrowGovernedSourceAction.issue(authority, COMPLETE_COUNTDOWN, observed, 61, () -> true).consume(observed::copy);
            observed.put("flight", RocketFlightNbtCodec.encode(ascent));
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(authority, BEGIN_TRANSIT, observed, 141, () -> true),
                    "A source still at its origin cannot transition to transit");
            var origin = record.sourceSnapshot().sourceOrigin();
            observed.put("pos", GovernedTransferFixture.vector(origin.x() + 0.5, origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS, origin.z() + 0.5));
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(authority, BEGIN_TRANSIT, observed, 140, () -> true));
            assertNotNull(EscrowGovernedSourceAction.issue(authority, BEGIN_TRANSIT, observed, 141, () -> true));
        } finally { journal.stop(); }
    }

    @Test void finalBoundaryAndSourceAreRecheckedAndFailedConsumptionCannotBeReused() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var root = EscrowGovernedDispatchTest.root(record); var journal = new RocketTransferSavedData(); journal.put(record);
        try {
            var authority = acknowledge(root, journal, record); var boundary = new AtomicBoolean(true);
            var permit = EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, fixture.source(), 1, boundary::get);
            boundary.set(false);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(fixture.source()::copy));
            boundary.set(true);
            assertThrows(IllegalArgumentException.class, () -> permit.consume(fixture.source()::copy));
            var changed = fixture.source().copy(); changed.putBoolean("on_ground", true);
            var second = EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, fixture.source(), 1, () -> true);
            assertThrows(IllegalArgumentException.class, () -> second.consume(changed::copy));
            assertThrows(IllegalArgumentException.class, () -> second.consume(fixture.source()::copy));
        } finally { journal.stop(); }
    }

    @Test void supersededJournalAndTerminalEntriesNeverAuthorizeSourceProgression() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var root = EscrowGovernedDispatchTest.root(record); var journal = new RocketTransferSavedData(); journal.put(record);
        try {
            var authority = acknowledge(root, journal, record);
            var permit = EscrowGovernedSourceAction.issue(authority, START_COUNTDOWN, fixture.source(), 1, () -> true);
            journal.put(record.destinationSpawned(record.nativeTransfer().orElseThrow().armed().destination()));
            assertThrows(IllegalArgumentException.class, () -> permit.consume(fixture.source()::copy));
            var next = journal.find(record.transferId()).orElseThrow(); var advanced = acknowledge(root, journal, next);
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(advanced, START_COUNTDOWN, fixture.source(), 1, () -> true));
            var cancelled = EscrowGovernedDispatchTest.arm(root, tag -> tag.putString("outcome", "CANCELLED"));
            var terminal = EscrowGovernedDispatch.acquire(() -> cancelled, () -> true, journal, next);
            assertEquals(EscrowGovernedDispatch.Route.CLEANUP, terminal.decision().route());
            assertThrows(IllegalArgumentException.class, () -> EscrowGovernedSourceAction.issue(terminal, START_COUNTDOWN, fixture.source(), 1, () -> true));
        } finally { journal.stop(); }
    }

    private EscrowGovernedDispatch.Authority acknowledge(EscrowLedger root, RocketTransferSavedData journal,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord record) throws Exception {
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
        var authority = EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, record);
        assertNotNull(authority); return authority;
    }
    private static RocketFlightData flight(CompoundTag envelope) {
        return RocketFlightNbtCodec.decode(envelope.getCompound("flight")).data().orElseThrow();
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferBayRecoveryGate.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPresence;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryDecision;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class RocketTransferBayDispatchTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final class Stub implements RocketTransferBayRecoveryGate {
        Claim claim = Claim.held(State.OWNED_WAIT, "PROVING_ENDPOINTS");
        Boundary boundary = Boundary.held();
        int observations;
        @Override public Claim claim(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) {
            Claim result = claim;
            if (result.kind() == Kind.READY_SOURCE || result.kind() == Kind.RELEASED_TO_ORDINARY) { claim = Claim.unclaimed(); }
            return result;
        }
        @Override public Boundary beforeAction(MinecraftServer server, RocketTransferSavedData journal,
                RocketTransferRecord record, Action action) { return boundary; }
        @Override public void endpointChanged(MinecraftServer server, RocketTransferSavedData journal, RocketTransferRecord record) { observations++; }
    }
    private static final class Fixture {
        final Set<UUID> live = new HashSet<>(), settled = new HashSet<>();
        final RocketTransferFences fences = new RocketTransferFences(ignored -> null);
        final RocketTransferBayDispatch bays = new RocketTransferBayDispatch(live, settled, fences);
        final RocketTransferRecord record = record(72);
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final Stub gate = new Stub();
        Fixture() { journal.put(record); bays.install(gate); fences.bays(bays); }
        void classify() { fences.classify(null, journal, live, settled); }
    }
    private static RocketTransferRecord record(int x) { return RocketStationMotionRuleTest.record(new RocketPosition(x, 80, 8)); }

    @Test void heldClaimBlocksOnlyOverlappingStationRegions() {
        var f = new Fixture(); f.classify();
        assertFalse(RocketTransferService.stationRegionInMotion(f.journal, f.bays.moving(), f.settled, 0,
                f.record.destinationSnapshot().sourceDimension(), 1_000, 1_000, 1_511, 1_511));
        assertTrue(RocketTransferService.stationRegionInMotion(f.journal, f.bays.moving(), f.settled, 0,
                f.record.destinationSnapshot().sourceDimension(), 0, 0, 511, 511));
    }
    @Test void secondRecordRecoversAndCursorMovesPastHeldClaim() {
        var f = new Fixture(); f.classify(); var second = record(92); var third = record(112);
        var rows = List.of(f.record, second, third);
        var a = RocketTransferRecoveryService.step(rows, f.live, f.settled, f.bays.excluded(), 0, ignored -> false);
        assertSame(second, a.candidate()); assertEquals(1, a.cursor());
        var b = RocketTransferRecoveryService.step(rows, f.live, f.settled, f.bays.excluded(), a.cursor(), ignored -> true);
        assertSame(third, b.candidate()); assertTrue(b.ready());
    }
    @Test void heldClaimNeverReachesOrdinaryTicketingPredicate() {
        var f = new Fixture();
        for (int tick = 0; tick < 1_200; tick++) {
            f.classify();
            assertNull(RocketTransferRecoveryService.step(f.journal.entries(), f.live, f.settled, f.bays.excluded(), tick,
                    ignored -> { fail("Held record requested a recovery ticket"); return false; }).candidate());
        }
        assertTrue(f.fences.ids().isEmpty());
    }
    @Test void healthyDeploymentNeverEmitsAFenceLog() {
        var messages = new java.util.ArrayList<String>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("bay-dispatch-test", null,
                org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(), false,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) { messages.add(event.getMessage().getFormattedMessage()); }
        };
        appender.start(); logger.addAppender(appender);
        try {
            var f = new Fixture(); f.live.add(f.record.transferId()); f.settled.add(f.record.transferId());
            f.classify(); assertTrue(f.fences.ids().isEmpty()); assertTrue(f.live.isEmpty()); assertTrue(f.settled.isEmpty());
            assertFalse(messages.stream().anyMatch(line -> line.contains("ARCE_TRANSFER_FENCED")), messages.toString());
        } finally { logger.removeAppender(appender); appender.stop(); }
    }
    @Test void recoverByIdRetriesBeforeJournalOrWorldAccess() {
        var f = new Fixture(); var recovery = new RocketTransferRecoveryService(f.live, f.settled);
        recovery.bays().install(f.gate);
        // A null server makes any journal retry, fence read or ticketing query fail instead of passing vacuously.
        for (int attempt = 0; attempt < 20; attempt++) {
            assertEquals(RocketTransferRecoveryService.Status.RETRY_LATER,
                    recovery.recoverById(null, f.journal, f.record.transferId()).status());
        }
        assertTrue(recovery.fencedIds().isEmpty()); assertEquals(Set.of(f.record.transferId()), recovery.bays().ids());
    }
    @Test void finalBoundaryHeldClassifiesInTheSameCallWithoutConsumingAHandoff() {
        var f = new Fixture(); f.live.add(f.record.transferId());
        f.gate.claim = Claim.readySource(f.record.sourceEntityId());
        assertFalse(f.bays.allowed(null, f.journal, f.record, Action.SPAWN_DESTINATION));
        assertEquals(Set.of(f.record.transferId()), f.bays.ids()); assertTrue(f.live.isEmpty()); assertTrue(f.fences.ids().isEmpty());
        f.classify(); assertEquals(f.record.sourceEntityId(), f.bays.source(f.record.transferId()));
    }
    @Test void readySourceContinuesAsLiveAndNeverSelectsOrdinaryRollback() {
        var f = new Fixture(); f.classify(); f.gate.claim = Claim.readySource(f.record.sourceEntityId());
        f.classify(); assertEquals(Set.of(f.record.transferId()), f.live); assertTrue(f.bays.ids().isEmpty());
        assertNull(RocketTransferRecoveryService.step(f.journal.entries(), f.live, f.settled, f.bays.excluded(), 0,
                ignored -> { fail("Source handoff was sent to stationary recovery"); return true; }).candidate());
        assertEquals(f.record.sourceEntityId(), f.bays.source(f.record.transferId()));
    }
    @Test void releasedToOrdinarySelectsTheStationarySourceRecovery() {
        var f = new Fixture(); f.classify(); f.gate.claim = Claim.ordinary();
        // Exercise just the handoff; ordinary loaded/world classification is covered by the GameTest.
        assertTrue(f.bays.classify(null, f.journal, f.record));
        var step = RocketTransferRecoveryService.step(f.journal.entries(), f.live, f.settled, f.bays.excluded(), 0, ignored -> true);
        assertSame(f.record, step.candidate());
        assertEquals(RocketTransferRecoveryAction.KEEP_SOURCE,
                RocketTransferRecoveryDecision.decide(f.record.phase(), new RocketTransferPresence(true, false)));
    }
    @Test void defaultGateLeavesExistingClassificationAndSelectionUnchanged() {
        var record = record(72); var journal = new RocketTransferSavedData(); journal.put(record);
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var dispatch = new RocketTransferBayDispatch(live, settled, new RocketTransferFences());
        assertTrue(dispatch.classify(null, journal, record)); assertTrue(dispatch.ids().isEmpty());
        assertEquals(BoundaryKind.UNCLAIMED, dispatch.before(null, journal, record, Action.RECOVER_AUTO).kind());
        assertSame(record, RocketTransferRecoveryService.step(journal.entries(), live, settled, dispatch.excluded(), 0, ignored -> true).candidate());
    }
    @Test void releasedFlightRequiresAnExactActionPermitAndHeldEndsOnlyAtClassification() {
        var f = new Fixture(); f.classify(); f.gate.claim = Claim.releasedFlight();
        f.classify(); assertTrue(f.bays.ids().isEmpty()); assertTrue(f.live.contains(f.record.transferId()));
        f.gate.boundary = Boundary.unclaimed(); assertFalse(f.bays.allowed(null, f.journal, f.record, Action.TICK_SOURCE));
        var uses = new AtomicInteger();
        f.gate.boundary = Boundary.permit((server, journal, record, action) -> record == f.record
                && action == Action.TICK_SOURCE && uses.incrementAndGet() == 1);
        assertTrue(f.bays.allowed(null, f.journal, f.record, Action.TICK_SOURCE));
        assertFalse(f.bays.allowed(null, f.journal, f.record, Action.TICK_SOURCE));
        assertEquals(Set.of(f.record.transferId()), f.bays.ids());
    }
    @Test void joinAndLeaveNotifyClaimsWithoutCreatingAFenceAndDiagnosticsStayBounded() {
        var f = new Fixture(); f.classify();
        assertTrue(f.bays.endpointChanged(null, f.journal, f.record));
        assertTrue(f.bays.endpointChanged(null, f.journal, f.record));
        assertEquals(2, f.gate.observations); assertTrue(f.fences.ids().isEmpty());
        for (int i = 1; i < 6; i++) { var record = record(72 + 20 * i); f.journal.put(record); f.bays.classify(null, f.journal, record); }
        String line = f.bays.diagnostics(f.journal);
        assertTrue(line.startsWith(" claimed=6"), line);
        assertEquals(4, line.split("\\[claimed_transfer=", -1).length - 1);
        assertFalse(line.contains("load an end")); assertFalse(line.contains("repair=offline-whole-world"));
        f.bays.conflict(null, f.journal, f.journal.entries().get(0));
        assertTrue(f.bays.diagnostics(f.journal).contains("repair=offline-whole-world"));
        assertThrows(IllegalStateException.class, () -> f.bays.install(f.gate));
    }
    @Test void completionCacheDoesNotAuthorizeAnUnacknowledgedRemoval() {
        var record = record(72); var journal = new RocketTransferSavedData(); journal.put(record); journal.remove(record.transferId());
        var recovery = new RocketTransferRecoveryService(new HashSet<>(), new HashSet<>());
        var completed = new RocketTransferRecoveryService.Result(RocketTransferRecoveryService.Status.RECOVERED,
                record.transferId(), record.phase(), RocketTransferRecoveryAction.KEEP_SOURCE, 1, 0);
        recovery.completed(completed, record);
        assertEquals(RocketTransferRecoveryService.Status.RETRY_LATER,
                recovery.recoverById(null, journal, record.transferId()).status());
        assertSame(completed, recovery.takeCompleted(record.transferId()), "A pending refusal must retain the completion");
    }
    @Test void endpointNotificationAppliesRatherThanConsumesTheOneShotSourceHandoff() {
        var f = new Fixture(); f.classify(); f.gate.claim = Claim.readySource(f.record.sourceEntityId());
        assertTrue(f.bays.endpointChanged(null, f.journal, f.record));
        assertEquals(f.record.sourceEntityId(), f.bays.source(f.record.transferId()));
        assertEquals(Set.of(f.record.transferId()), f.live); assertTrue(f.bays.ids().isEmpty()); assertTrue(f.fences.ids().isEmpty());
        assertEquals(Kind.UNCLAIMED, f.gate.claim.kind());
    }
}

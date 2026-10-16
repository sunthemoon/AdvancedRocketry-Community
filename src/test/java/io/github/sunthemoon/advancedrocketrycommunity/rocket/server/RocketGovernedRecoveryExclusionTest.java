package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketGovernedRecoveryExclusionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void missingArmedEntryNeverFallsBackToTicketAddingLegacySelection() {
        var governed = GovernedTransferFixture.create().record();
        var healthy = RocketStationMotionRuleTest.record(new RocketPosition(92, 80, 8));
        var step = RocketTransferRecoveryService.step(List.of(governed), Set.of(), Set.of(), 0,
                candidate -> { fail("Governed records must not enter legacy readiness"); return true; });
        assertNull(step.candidate());
        step = RocketTransferRecoveryService.step(List.of(governed, healthy), Set.of(), Set.of(), 0,
                candidate -> { assertSame(healthy, candidate); return true; });
        assertSame(healthy, step.candidate());
    }

    @Test void classificationManualRecoveryAndFinalActionsHoldWithoutReadingWorldOrLedger() {
        var record = GovernedTransferFixture.create().record(); var journal = new RocketTransferSavedData(); journal.put(record);
        var fences = new RocketTransferFences(server -> { fail("Legacy ledger election must not inspect record 3"); return null; });
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>(); live.add(record.transferId()); settled.add(record.transferId());
        fences.classify(null, journal, live, settled);
        assertTrue(live.isEmpty()); assertTrue(settled.isEmpty()); assertEquals("GOVERNED_ACTION_REQUIRED", fences.reason(record.transferId()));
        assertFalse(fences.permit(null, journal, record, live, settled));
        for (var action : RocketTransferRecoveryAction.values()) {
            assertFalse(fences.actionAllowed(null, journal, record, action, List.of(), List.of(), live, settled));
        }
        fences.joined(null, record.logicalRocketId(), true, journal, live, settled);
        assertFalse(fences.permit(null, journal, record, live, settled)); assertTrue(live.isEmpty()); assertTrue(settled.isEmpty());
    }

    @Test void checkedF3ThroughF5RouteToTheirExecutorsButPendingAndTerminalAuthorityStayFenced() {
        var prepared = GovernedTransferFixture.create().record();
        var spawned = prepared.destinationSpawned(prepared.nativeTransfer().orElseThrow().armed().destination());
        var fences = new RocketTransferFences();
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var record = prepared;
        for (var phase : RocketTransferPhase.values()) {
            record = phase == prepared.phase() ? prepared : phase == spawned.phase() ? spawned : record.advance(phase);
            var active = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.ACTIVE, null);
            fences.classifyGoverned(record, active, live, settled);
            assertTrue(live.contains(record.transferId()));
            assertFalse(fences.ids().contains(record.transferId()));
            assertTrue(settled.isEmpty());
            var pending = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD, "GOVERNED_JOURNAL_UNACKNOWLEDGED");
            fences.classifyGoverned(record, pending, live, settled);
            assertTrue(live.isEmpty()); assertEquals(pending.reason(), fences.reason(record.transferId()));
            var terminal = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.CLEANUP, null);
            fences.classifyGoverned(record, terminal, live, settled);
            assertTrue(live.isEmpty()); assertEquals("GOVERNED_CLEANUP_REQUIRED", fences.reason(record.transferId()));
        }
    }
}

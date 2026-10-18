package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W90 (R21 item 2): a recorded end outside the checked set holds before any execution or refresh. */
class RocketDiscoveryMarginHoldTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void anAlreadyHeldRecordAndAHoldRaisedByTheReadySupplierBothMakeZeroRefreshCalls() {
        var active = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.ACTIVE, null);
        var refreshes = new AtomicInteger(); var executions = new AtomicInteger();
        for (int visit = 0; visit < 1_000; visit++) {
            assertFalse(RocketGovernedSourceDispatcher.afterClassification(active, () -> true, refreshes::incrementAndGet,
                    () -> false, executions::incrementAndGet));
        }
        var held = new AtomicBoolean();
        for (int visit = 0; visit < 1_000; visit++) {
            // The ready supplier registers DISCOVERY_MARGIN and answers not ready, as the dispatcher's does.
            assertFalse(RocketGovernedSourceDispatcher.afterClassification(active, held::get, refreshes::incrementAndGet,
                    () -> { held.set(true); return false; }, executions::incrementAndGet));
        }
        assertEquals(0, refreshes.get(), "A held record refreshed its flight tickets");
        assertEquals(0, executions.get(), "A held invalid record reached an executor");
    }

    @Test void onlyEndsThatAreValidAndContainTheirOriginColumnAreAdmissible() {
        var record = GovernedTransferFixture.create().record();
        assertTrue(RocketGovernedSourceDispatcher.marginsAdmissible(record), "The ordinary fixture would be held");
        var earth = record.sourceSnapshot().sourceDimension();
        var wide = RocketMarginTicketsTest.snapshot(earth, 2, 8, 0, 252);
        var outside = RocketMarginTicketsTest.snapshot(earth, 15, 8, 5, 6);
        var valid = RocketMarginTicketsTest.snapshot(earth, 1, 8, 0, 252);
        assertTrue(io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns.of(valid).admissible());
        assertFalse(io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns.of(wide).admissible());
        assertFalse(io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns.of(outside).admissible());
    }
}

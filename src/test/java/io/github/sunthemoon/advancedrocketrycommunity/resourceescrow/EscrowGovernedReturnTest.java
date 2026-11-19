package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.HashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowGovernedReturnTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void forwardPhasesAreRejectedBeforeCompleteEnvelopeSerialization() {
        var fixture = io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture.create();
        var initial = EscrowGovernedReturnAction.flight(fixture.source());
        var countdown = initial.withPlan(fixture.record().sourceFlightData().plan().orElseThrow()).startCountdown(1);
        assertTrue(EscrowGovernedReturn.stationaryCandidate(initial));
        assertFalse(EscrowGovernedReturn.stationaryCandidate(null));
        assertFalse(EscrowGovernedReturn.stationaryCandidate(countdown));
        assertFalse(EscrowGovernedReturn.stationaryCandidate(countdown.completeCountdown(61)));
        assertFalse(EscrowGovernedReturn.stationaryCandidate(countdown.completeCountdown(61).beginTransit(fixture.record().transferId(), 141)));
        assertTrue(EscrowGovernedReturn.stationaryCandidate(countdown.markFailed(200).recover(true, 200)));
    }

    @Test void pendingReturnsIncludeCheckpointsAndRefuseOneBeyondTheFlightLimit() {
        var pending = new HashMap<UUID, Boolean>();
        for (int index = 0; index < RocketFlightLimits.MAX_ACTIVE_TRANSFERS; index++) {
            var id = new UUID(0, index);
            EscrowGovernedReturn.requireCapacity(pending, id); pending.put(id, index % 2 == 0);
        }
        assertEquals(64, pending.size());
        assertThrows(IllegalArgumentException.class, () -> EscrowGovernedReturn.requireCapacity(pending, UUID.randomUUID()));
        assertDoesNotThrow(() -> EscrowGovernedReturn.requireCapacity(pending, new UUID(0, 0)));
        pending.remove(new UUID(0, 0));
        assertDoesNotThrow(() -> EscrowGovernedReturn.requireCapacity(pending, UUID.randomUUID()));
    }

    @Test void aCallbackBoundaryRefusalDoesNotHoldTheNextVisitOrReplaceTheSource() {
        for (boolean returned : new boolean[] { false, true }) {
            var boundary = new java.util.concurrent.atomic.AtomicBoolean(false);
            var source = EscrowFlightSourceTest.liveSource(boundary::get);
            var record = io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture.create().record();
            var state = new EscrowGovernedReturn.Work(record, null, source, returned, () -> fail("Observation called completion"));
            assertFalse(state.source.observe(), "A callback accepted a refused boundary");
            state.release();
            boundary.set(true);
            assertNull(EscrowGovernedReturn.sourceHold(state, record, source.rocket));
            assertSame(source, state.source, "Boundary recovery recaptured the return source");
            assertTrue(state.ready(1, () -> true));
            assertEquals(0, state.retry.failures());
        }
    }

    @Test void aMovedSourceStillHoldsWithReturnEnvelopeEvenAfterItsPositionIsRestored() {
        var source = EscrowFlightSourceTest.liveSource(() -> true);
        var record = io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture.create().record();
        var state = new EscrowGovernedReturn.Work(record, null, source, false, () -> fail("Hold called completion"));
        assertNull(EscrowGovernedReturn.sourceHold(state, record, source.rocket));
        var original = source.rocket.position();
        EscrowFlightSourceTest.position(source.rocket, original.add(1, 0, 0));
        assertEquals(EscrowGovernedExecutorHolds.Reason.RETURN_ENVELOPE,
                EscrowGovernedReturn.sourceHold(state, record, source.rocket));
        assertFalse(source.unchanged.get());
        EscrowFlightSourceTest.position(source.rocket, original);
        assertEquals(EscrowGovernedExecutorHolds.Reason.RETURN_ENVELOPE,
                EscrowGovernedReturn.sourceHold(state, record, source.rocket));
        assertSame(source, state.source);
    }
}

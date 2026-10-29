package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailurePoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionAbortException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Retained/I6 handoff ordering, request age, completion and certified rollback. */
@Timeout(60)
class RocketLandedDisassemblyHandoffTest extends RocketLandedDisassemblyTestSupport {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void checkedRemovalDefersRetainedAdmissionToItsOwnBudgetedTurn() throws Exception {
        var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.retained = true;
        f.release(); f.acknowledge();
        assertNull(f.request.retainedCompletion, "An END-tick journal callback must not consume an already-spent B2 budget");
        assertTrue(f.disassemblies.pending(f.logical())); assertEquals(0, f.request.restores);
        f.port.now++;
        RocketLandedDisassemblies.class.getDeclaredMethod("tickRetained").invoke(f.disassemblies);
        assertNotNull(f.request.retainedCompletion);
        f.request.retainedCompletion.accept(SealedDisassembly.Result.SEALED); f.close();
    }

    @Test void normalStopWritesBackAnAcknowledgedHandoffThatNeverEnteredB2() throws Exception {
        var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.retained = true;
        f.release(); f.acknowledge();
        RocketLandedDisassemblies.class.getDeclaredMethod("cancelRetainedHandoffs").invoke(f.disassemblies);
        assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
        assertNull(f.request.retainedCompletion); assertEquals(0, f.request.restores);
        f.acknowledge(); assertFalse(f.disassemblies.pending(f.logical())); f.close();
    }

    @Test void deferredHandoffCannotRefreshAgeOrStartAfterClear() {
        var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.retained = true;
        f.request.requestedAt = 10; f.port.now = 210; f.release(); f.acknowledge();
        f.port.now = 211; f.disassemblies.tickRetained();
        assertNull(f.request.retainedCompletion);
        assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
        f.acknowledge(); f.close();
        var stopped = new Fixture(new RocketPosition(64, 80, 64)); stopped.request.retained = true;
        stopped.release(); stopped.acknowledge(); stopped.disassemblies.clear(); stopped.disassemblies.tickRetained();
        assertNull(stopped.request.retainedCompletion); stopped.close();
    }

    @Test void retainedCompletionUsesActualResultAndOnlyRollbackProvedWritesBack() {
        for (var result : SealedDisassembly.Result.values()) {
            var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.retained = true;
            f.release(); f.acknowledge(); f.disassemblies.tickRetained();
            assertEquals(0, f.request.restores, "The retained executor must replace synchronous restore");
            assertTrue(f.disassemblies.pending(f.logical()));
            assertNotNull(f.request.retainedCompletion);
            f.request.retainedCompletion.accept(result); f.request.retainedCompletion.accept(result);
            assertEquals(List.of(result.name()), f.request.reports);
            assertEquals(result == SealedDisassembly.Result.ROLLBACK_PROVED,
                    f.journal.find(f.landed.transferId()).isPresent());
            f.acknowledge(); assertFalse(f.disassemblies.pending(f.logical())); f.close();
        }
    }

    @Test void retainedAdmissionRefusalDoesNotSetTheMutationLatch() {
        var f = new Fixture(new RocketPosition(64, 80, 64));
        f.request.retained = true; f.request.acceptRetained = false;
        f.release(); f.acknowledge(); f.disassemblies.tickRetained();
        assertEquals(0, f.request.restores);
        assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
        assertFalse(f.disassemblies.find(f.logical()).restorationStarted());
        f.acknowledge(); f.close();
    }

    @Test void provedHandoffCreatesPendingWithoutQueueingTheReservationTwice() throws Exception {
        var f = new Fixture(new RocketPosition(64, 80, 64));
        var prepare = RocketLandedDisassemblies.class.getDeclaredMethod("prepareRelease",
                RocketTransferSavedData.class, RocketTransferRecord.class, UUID.class,
                RocketLandedDisassemblies.Request.class, RocketLandedDisassemblies.Port.class,
                RocketTransferSavedData.Reservation.class);
        try (var token = f.journal.reserve(f.logical()).orElseThrow()) {
            var pending = prepare.invoke(f.disassemblies, f.journal, f.landed, f.entity, f.request, f.port, token);
            assertNotNull(pending);
            assertSame(pending, f.disassemblies.find(f.logical()));
            assertTrue(f.journal.find(f.landed.transferId()).isEmpty());
            assertEquals(1, f.disassemblies.heldSlots(f.journal));
            assertEquals(0, f.request.restores);
            assertTrue(RocketJournalTestAccess.queue(f.journal, token, () -> true, () -> { }),
                    "The proof coordinator must still own the one continuation slot");
        }
        f.acknowledge(); assertEquals(0, f.request.restores);
        f.disassemblies.clear(); f.close();
    }

    @Test void proofHandoffDoesNotRefreshTheOriginalRequestAge() {
        var f = new Fixture(new RocketPosition(64, 80, 64));
        f.request.requestedAt = 10; f.port.now = 211;
        f.release(); f.acknowledge();
        assertEquals(0, f.request.restores, "M1 must retain M0's request age");
        assertSame(f.landed, f.journal.find(f.landed.transferId()).orElseThrow());
        f.acknowledge(); f.close();
    }

    @Test void asynchronousRestoreKeepsPendingUntilItsCompletionAndIgnoresLateCallbacks() {
        var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.deferred = true;
        assertEquals(RocketValidationCode.DISASSEMBLY_PENDING, f.release()); f.acknowledge();
        assertTrue(f.disassemblies.pending(f.logical()), "M2 must wait for the retained executor");
        assertTrue(f.request.reports.isEmpty()); assertTrue(f.world.blocks.isEmpty());
        var callback = f.request.completion;
        assertNotNull(callback);
        var result = f.request.restore(); callback.accept(result); callback.accept(result);
        assertEquals(List.of("SUCCESS"), f.request.reports);
        assertFalse(f.disassemblies.pending(f.logical())); assertTrue(f.journal.entries().isEmpty()); f.close();
    }

    @Test void ramRollbackCountsWithoutNativeCertificationCannotWriteTBack() {
        var f = new Fixture(new RocketPosition(64, 80, 64)); f.request.certified = false;
        f.request.injector = (point, progress) -> {
            if (point == RocketFailurePoint.DURING_RESTORATION && progress == 2) {
                throw new RocketTransactionAbortException(RocketValidationCode.EXTRACTION_FAILED, "RAM rollback only");
            }
        };
        f.release(); f.acknowledge();
        assertTrue(f.world.blocks.isEmpty()); assertTrue(f.world.rockets.containsKey(f.entity));
        assertTrue(f.journal.find(f.landed.transferId()).isEmpty(), "RAM rollback is not M3 permission");
        assertFalse(f.disassemblies.pending(f.logical())); f.close();
    }

}

package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Permanent form of the W121 single-capture probe: real scheduler/codec, explicitly modelled capture budget. */
class EscrowJournalHoldTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void heldFirstCaptureCannotStarveTheNextComponentAcrossTwoRestarts() {
        for (int count : List.of(2, 5, 8)) {
            var records = new ArrayList<EscrowOperation>();
            for (int i = 1; i <= count; i++) { records.add(EscrowTestFixtures.operation(i)); }
            var root = EscrowTestFixtures.ledger(records.toArray(EscrowOperation[]::new));
            for (int restart = 0; restart < 3; restart++) {
                root = EscrowCodec.decode(EscrowCodec.encode(root)); var held = root;
                var driver = new BudgetDriver();
                var scheduler = new EscrowReconciliationScheduler(() -> held,
                        (id, reason) -> fail("A transient boundary hold must never persist"), driver::attempt);
                for (int tick = 0; tick < 1_200; tick++) { driver.tick = tick; scheduler.tick(tick); }
                assertEquals(60, driver.captures.stream().filter(id -> id == 1L).count());
                assertTrue(driver.captures.contains(2L), "The held first component starved the younger one");
                assertEquals(1_200, driver.captures.size(), "The one-capture budget is unchanged");
                assertEquals(EscrowReason.NONE, root.records().get(0).reason());
            }
        }
    }

    @Test void boundaryWaitShowsReasonAndRetryInStatusInspectAndLoaderWithoutClearingAStall() {
        for (var persisted : List.of(EscrowReason.NONE, EscrowReason.BARRIER_IO)) {
            var op = EscrowTestFixtures.operation(1).withReason(persisted); var root = EscrowTestFixtures.ledger(op);
            var before = EscrowCodec.encode(root); var attempts = new ArrayList<Long>();
            var scheduler = new EscrowReconciliationScheduler(() -> root, (id, reason) -> fail("No durable reason change"),
                    (member, all, done) -> { attempts.add(member.sequence()); done.accept(new EscrowReconciliationScheduler.Result(
                            EscrowReason.TRANSFER_JOURNAL_UNPROVED, false)); });
            scheduler.tick(100); scheduler.tick(119); assertEquals(1, attempts.size());
            assertEquals(120, scheduler.view(op.id()).nextTick());
            var status = EscrowOperatorDiagnostics.reconciliationStatus(root, scheduler::view);
            assertTrue(status.contains("runtime_reason=TRANSFER_JOURNAL_UNPROVED")); assertTrue(status.contains("next_retry=120"));
            var inspect = String.join(" ", EscrowOperatorDiagnostics.inspect(root, op.id(), ignored -> fail(), scheduler::view, "ACKNOWLEDGED"));
            assertTrue(inspect.contains("reason=TRANSFER_JOURNAL_UNPROVED")); assertTrue(inspect.contains("next_retry=120"));
            assertTrue(inspect.contains("persisted_reason=" + persisted));
            for (boolean applied : List.of(false, true)) {
                assertEquals(FluidFuelLoaderStatus.ESCROW_PENDING, EscrowLoaderAdmission.progress(true, applied, true, false,
                        EscrowLoaderAdmission.held(op, op.reason(), scheduler.view(op.id())), false));
            }
            scheduler.tick(120); assertEquals(2, attempts.size()); assertEquals(140, scheduler.view(op.id()).nextTick());
            assertEquals(before, EscrowCodec.encode(root));
            assertEquals(persisted == EscrowReason.NONE ? 0 : 3, scheduler.view(op.id()).failures());
        }
    }

    private static final class BudgetDriver {
        final List<Long> captures = new ArrayList<>();
        long tick, capturedAt = Long.MIN_VALUE;
        void attempt(EscrowOperation op, List<EscrowOperation> records,
                     java.util.function.Consumer<EscrowReconciliationScheduler.Result> done) {
            if (capturedAt == tick) { done.accept(EscrowReconciliationScheduler.Result.deferred()); return; }
            capturedAt = tick; captures.add(op.sequence());
            done.accept(new EscrowReconciliationScheduler.Result(op.sequence() == 1
                    ? EscrowReason.TRANSFER_JOURNAL_UNPROVED : EscrowReason.NONE, false));
        }
    }
}

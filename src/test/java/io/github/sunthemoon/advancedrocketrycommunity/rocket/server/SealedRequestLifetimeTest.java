package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.*;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class SealedRequestLifetimeTest {
    @Test void busyInLimitLivePrepareExpiresAt201WithoutRetainingAnything() {
        var f = new Fixture(false); f.run(200);
        assertEquals(SealedDisassembly.Phase.PREPARE, f.machine.phase()); assertNull(f.machine.result());
        f.tick(201);
        assertEquals(SealedDisassembly.Result.REJECTED, f.machine.result());
        assertFalse(f.claimed); assertEquals(0, f.retained); assertEquals(0, f.mutations);
    }
    @Test void cancellationDuringProofBackoffReleasesOnlyTheRamClaimOnThatTick() {
        var f = new Fixture(false); f.run(10); f.requestCurrent = false; f.tick(11);
        assertEquals(SealedDisassembly.Result.REJECTED, f.machine.result()); assertFalse(f.claimed);
        assertEquals(0, f.retained); assertEquals(0, f.mutations);
    }
    @Test void heldRequestExpiresEvenWhenNeitherMachineNorProofIsEligible() {
        var f = new Fixture(false); f.run(199); f.gate = SealedDisassembly.Gate.SAVE_OFF;
        f.tick(200); f.tick(201);
        assertEquals(SealedDisassembly.Result.REJECTED, f.machine.result()); assertFalse(f.claimed);
    }
    @Test void retainedRecoveryNeverReleasesUOrResourcesWhenProofCannotStart() {
        var f = new Fixture(true); f.requestCurrent = false; f.run(1000);
        assertNull(f.machine.result()); assertTrue(f.claimed); assertEquals(1, f.retained); assertEquals(0, f.mutations);
    }

    private static final class Fixture implements SealedWorkScheduler.Entry {
        final UUID id = UUID.randomUUID();
        final SealedWorkBudget budget = new SealedWorkBudget();
        final SealedWorkScheduler scheduler = new SealedWorkScheduler();
        final SealedDisassembly machine;
        SealedProofBatch batch;
        SealedDisassembly.Gate gate = SealedDisassembly.Gate.READY;
        long now;
        boolean requestCurrent = true, claimed = true;
        int retained, mutations;
        Fixture(boolean recovery) {
            retained = recovery ? 1 : 0;
            var port = (SealedDisassembly.Port) Proxy.newProxyInstance(SealedDisassembly.Port.class.getClassLoader(),
                    new Class<?>[] {SealedDisassembly.Port.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "now" -> now;
                        case "gate" -> gate;
                        case "requestCurrent" -> requestCurrent;
                        case "inspect" -> retained == 0 ? SealedDisassembly.Cell.EMPTY : SealedDisassembly.Cell.OWNED;
                        case "entity" -> retained == 0 ? SealedDisassembly.Entity.MATCHING : SealedDisassembly.Entity.ABSENT;
                        case "proofCurrent" -> false;
                        case "requestProof" -> {
                            if (batch == null) { batch = new SealedProofBatch(2, new SealedProofBatch.Port() {
                                public long now() { return now; }
                                public SealedProofBatch.Readiness readiness() { return SealedProofBatch.Readiness.valueOf(gate.name()); }
                                public boolean attemptRoom() { return false; }
                                public boolean available(int slot) { return true; }
                                public boolean capture(int slot, Consumer<EscrowBarrier.Result> callback) { fail("BUSY captured"); return false; }
                                public void held(String reason) { }
                            }); }
                            yield null;
                        }
                        case "retain" -> { retained++; yield null; }
                        case "removeRecord" -> { retained--; mutations++; yield null; }
                        case "stage", "erase", "discard", "discardDuplicate" -> { mutations++; yield true; }
                        case "held", "checkpoint", "elect" -> null;
                        default -> true;
                    });
            machine = recovery ? SealedDisassembly.recover(1, port, true) : new SealedDisassembly(1, port);
        }
        void run(int last) { for (int tick = 0; tick <= last; tick++) { tick(tick); } }
        void tick(long tick) { now = tick; budget.beginTick(tick); scheduler.tick(tick, List.of(this), null, budget, () -> { }, ignored -> fail()); }
        public UUID id() { return id; }
        public boolean recovery() { return false; }
        public boolean activeAttempt() { return batch != null && batch.active(); }
        public boolean eligible(long tick) {
            return claimed && machine.result() == null && tick >= machine.retryAt() && (batch == null || tick >= batch.retryAt());
        }
        public void tickDeadline() { if (batch != null) { batch.tickDeadline(); } }
        public void tickProof() { if (batch != null) { batch.tick(); } }
        public void tickRequest() {
            machine.tickRequest();
            finish();
        }
        public void work() { if (eligible(now)) { while (machine.step(budget)) { } finish(); } }
        void finish() { if (machine.result() != null) { claimed = false; if (batch != null) { batch.close(); } } }
    }
}

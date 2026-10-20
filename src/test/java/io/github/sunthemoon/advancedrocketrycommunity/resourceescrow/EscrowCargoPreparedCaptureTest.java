package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class EscrowCargoPreparedCaptureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temp;

    @Test void finalAdmissionExceptionStillDiscardsPreparedRam() {
        var c = EscrowCargoEligibilityTest.sample(true, 1, -1);
        var budget = new RocketCargoWorkBudget();
        var preparation = new RocketCargoPreparation(c.op().guard().rocket(),
                RocketSnapshotNbtCodec.decode(c.before().getCompound("snapshot")).snapshot().orElseThrow(),
                EscrowCargoPayload.decode(c.op().payload()), true);
        long tick = 1;
        while (!preparation.advance(tick++, budget)) { assertTrue(tick < 100); }
        var root = EscrowTestFixtures.ledger();
        var capture = new EscrowCargoPreparedCapture(preparation, c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 64_000);
        while (!capture.advance(tick++, budget, root, List.of())) { assertTrue(tick < 100); }
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("exception"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            long now = tick;
            assertThrows(IllegalStateException.class, () -> coordinator.prepareCargo(capture, now, budget,
                    () -> { throw new IllegalStateException("final input check failed"); }, ignored -> fail()));
            assertEquals(RocketCargoPreparation.State.DISCARDED, preparation.state());
            assertEquals(1, coordinator.nextSequence());
            assertEquals(0, coordinator.admissionAuthority().pendingCount());
            assertFalse(data.isDirty());
        }
    }

    @Test void nativeCaptureSuppliersRunOnlyAfterTheirWorkIsReserved() {
        var c = EscrowCargoEligibilityTest.sample(true, 1, -1);
        var root = EscrowTestFixtures.ledger(); var budget = new RocketCargoWorkBudget();
        var preparation = new RocketCargoPreparation(c.op().guard().rocket(),
                RocketSnapshotNbtCodec.decode(c.before().getCompound("snapshot")).snapshot().orElseThrow(),
                EscrowCargoPayload.decode(c.op().payload()), true);
        long tick = 1;
        while (!preparation.advance(tick++, budget)) { assertTrue(tick < 30); }
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var current = new java.util.concurrent.atomic.AtomicBoolean(true);
        var inputs = List.of(c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
        var captures = inputs.stream().<java.util.function.Supplier<net.minecraft.nbt.CompoundTag>>map(input -> () -> {
            calls.incrementAndGet(); return input.copy();
        }).toList();
        var capture = new EscrowCargoPreparedCapture(preparation, c.op(), captures, current::get, 64_000);
        assertEquals(0, calls.get());
        assertTrue(budget.charge(tick, RocketCargoWorkBudget.MAX_BYTES, RocketCargoWorkBudget.MAX_VISITS, false));
        assertFalse(capture.advance(tick++, budget, root, List.of())); assertEquals(0, calls.get());
        while (!capture.advance(tick++, budget, root, List.of())) { assertTrue(tick < 100); }
        assertEquals(4, calls.get()); assertEquals(Status.ELIGIBLE, capture.result().status());
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        try (var writer = new EscrowLedgerWriter(temp.resolve("native-final"), 0); var coordinator = new EscrowCoordinator(data, writer)) {
            current.set(false);
            assertEquals(EscrowCoordinator.Transfer.STALE, coordinator.prepareCargo(capture, tick, budget, () -> true, ignored -> fail()));
            assertEquals(1, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
            assertFalse(data.isDirty()); assertEquals(RocketCargoPreparation.State.DISCARDED, preparation.state());
        }
    }

    @Test void stagedCaptureMatchesSynchronousEligibilityInBothDirections() {
        for (boolean load : List.of(false, true)) {
            var candidate = EscrowCargoEligibilityTest.sample(load, 749, -1);
            var root = EscrowTestFixtures.ledger();
            var expected = EscrowCargoEligibility.check(root, List.of(), candidate.op(), candidate.before(), candidate.after(),
                    candidate.deviceBefore(), candidate.deviceAfter(), 64_000);
            var budget = new RocketCargoWorkBudget();
            var preparation = new RocketCargoPreparation(candidate.op().guard().rocket(),
                    RocketSnapshotNbtCodec.decode(candidate.before().getCompound("snapshot")).snapshot().orElseThrow(),
                    EscrowCargoPayload.decode(candidate.op().payload()), load);
            long tick = 1;
            while (!preparation.advance(tick++, budget)) { assertTrue(tick < 100); }
            var capture = new EscrowCargoPreparedCapture(preparation, candidate.op(), candidate.before(), candidate.after(),
                    candidate.deviceBefore(), candidate.deviceAfter(), 64_000);
            while (!capture.advance(tick++, budget, root, List.of())) {
                assertTrue(tick < 100); assertTrue(budget.chargedBytes() <= RocketCargoWorkBudget.MAX_BYTES);
                assertTrue(budget.chargedVisits() <= RocketCargoWorkBudget.MAX_VISITS);
            }
            assertEquals(Status.ELIGIBLE, capture.result().status());
            assertEquals(expected.capture(), capture.result().capture());
            var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
            try (var writer = new EscrowLedgerWriter(temp.resolve("capture-" + load), 0);
                 var coordinator = new EscrowCoordinator(data, writer)) {
                candidate.deviceBefore().putInt("changed", 1);
                assertEquals(EscrowCoordinator.Transfer.STALE, coordinator.prepareCargo(capture, tick++, budget, () -> true, ignored -> fail()));
                assertEquals(1, coordinator.nextSequence()); assertEquals(0, coordinator.admissionAuthority().pendingCount());
                assertFalse(data.isDirty()); assertFalse(coordinator.commitPreparations());
                candidate.deviceBefore().remove("changed");
                assertEquals(RocketCargoPreparation.State.DISCARDED, preparation.state());
                var retry = new RocketCargoPreparation(candidate.op().guard().rocket(),
                        RocketSnapshotNbtCodec.decode(candidate.before().getCompound("snapshot")).snapshot().orElseThrow(),
                        EscrowCargoPayload.decode(candidate.op().payload()), load);
                while (!retry.advance(tick++, budget)) { assertTrue(tick < 100); }
                var recaptured = new EscrowCargoPreparedCapture(retry, candidate.op(), candidate.before(), candidate.after(),
                        candidate.deviceBefore(), candidate.deviceAfter(), 64_000);
                while (!recaptured.advance(tick++, budget, root, List.of())) { assertTrue(tick < 100); }
                assertEquals(EscrowCoordinator.Transfer.QUEUED, coordinator.prepareCargo(recaptured, tick++, budget, () -> true, ignored -> { }));
                assertEquals(2, coordinator.nextSequence()); assertEquals(1, coordinator.admissionAuthority().pendingCount());
                assertEquals(RocketCargoPreparation.State.DISCARDED, retry.state());
            }
            preparation.close();
        }
    }
}

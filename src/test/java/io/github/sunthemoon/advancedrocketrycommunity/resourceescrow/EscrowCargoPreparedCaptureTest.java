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

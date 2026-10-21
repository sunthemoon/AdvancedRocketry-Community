package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemAdmissionRefusalTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void duplicateCandidatesAreStaleNotOversizedInBothCapturePaths() {
        for (boolean load : new boolean[] {true, false}) {
            var c = EscrowItemCargoEligibilityTest.sample(load, 5, EscrowItemCargoEligibilityTest.stone(-1));
            var root = EscrowTestFixtures.ledger();
            var first = EscrowItemCargoEligibility.check(root, List.of(), c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
            assertNotNull(first.capture());
            var pending = List.of(first.capture().operation());
            assertEquals(EscrowCoordinator.Transfer.STALE, EscrowCargoRefusals.capacity(root, pending, first.capture()).orElseThrow());
            var result = EscrowItemCargoEligibility.check(root, pending, c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter());
            assertEquals(EscrowCoordinator.Transfer.STALE, result.refusal());
            assertNull(result.capture()); assertTrue(root.records().isEmpty());
        }
    }

    @Test void itemRefusalsRetainTheirDeviceMeaning() {
        for (var status : io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.values()) {
            assertEquals(status == io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.ELIGIBLE
                    ? "READY" : status.name(), EscrowItemCargoAdmission.status(status).name());
        }
    }
}

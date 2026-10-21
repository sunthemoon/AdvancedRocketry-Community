package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoStoreSelectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void probeSelectsEachRoleInBothDirectionsOnlyWithTheGateOnAndBeforeRecovery() {
        for (boolean load : List.of(true, false)) {
            var op = EscrowCargoReconciliationFixture.create(load).op();
            for (String cut : List.of("SOURCE_ONLY_STORE", "DESTINATION_ONLY_STORE")) {
                for (var endpoint : List.of(op.source(), op.destination())) {
                    assertEquals(endpoint.equals(cut.startsWith("SOURCE") ? op.source() : op.destination()),
                            EscrowCargoReconciliationProbe.permitsStore(true, false, cut, op, endpoint));
                    assertTrue(EscrowCargoReconciliationProbe.permitsStore(false, false, cut, op, endpoint));
                    assertTrue(EscrowCargoReconciliationProbe.permitsStore(true, true, cut, op, endpoint));
                    assertTrue(EscrowCargoReconciliationProbe.permitsStore(true, false, "FIRST_STORE", op, endpoint));
                }
            }
        }
    }
}

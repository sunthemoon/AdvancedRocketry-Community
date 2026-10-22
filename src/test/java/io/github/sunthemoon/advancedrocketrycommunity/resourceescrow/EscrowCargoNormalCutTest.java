package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoNormalCutTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void secondForceObservationRequiresTheReleaseGateAndExactFluidCase() {
        for (String direction : List.of("LOAD", "UNLOAD")) {
            String name = "FC_" + direction + "_SECOND_FORCE_BEFORE";
            assertTrue(EscrowCargoReconciliationProbe.secondForceCut(true, name, false, 2, "BARRIER_FORCE_BEFORE"));
            assertFalse(EscrowCargoReconciliationProbe.secondForceCut(false, name, false, 2, "BARRIER_FORCE_BEFORE"));
            assertFalse(EscrowCargoReconciliationProbe.secondForceCut(true, name, true, 2, "BARRIER_FORCE_BEFORE"));
            for (int stores : List.of(0, 1, 3)) {
                assertFalse(EscrowCargoReconciliationProbe.secondForceCut(true, name, false, stores, "BARRIER_FORCE_BEFORE"));
            }
            for (String label : List.of("BARRIER_STORE_BEFORE", "BARRIER_FORCE_AFTER", "K8")) {
                assertFalse(EscrowCargoReconciliationProbe.secondForceCut(true, name, false, 2, label));
            }
        }
        for (String name : new String[] {null, "", "FC_LOAD_FIRST_STORE", "IC_LOAD_SECOND_FORCE_BEFORE"}) {
            assertFalse(EscrowCargoReconciliationProbe.secondForceCut(true, name, false, 2, "BARRIER_FORCE_BEFORE"));
        }
    }
}

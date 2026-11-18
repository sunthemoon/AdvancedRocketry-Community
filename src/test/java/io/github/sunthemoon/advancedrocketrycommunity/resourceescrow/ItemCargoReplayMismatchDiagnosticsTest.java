package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W381: the hold check keeps its four conditions; the text only names the ones that failed. */
class ItemCargoReplayMismatchDiagnosticsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void theHoldPassesOnlyWhenEveryConditionHolds() {
        for (int mask = 0; mask < 16; mask++) {
            boolean present = (mask & 1) != 0, sameId = (mask & 2) != 0, device = (mask & 4) != 0, rocket = (mask & 8) != 0;
            var failed = ItemCargoReplayMismatchDiagnostics.failedChecks(present, sameId, device, rocket);
            // The fixture's original expression: held != null && held.id().equals(first) && device equal && rocket equal.
            assertEquals(present && sameId && device && rocket, failed.isEmpty(), "mask " + mask);
        }
    }

    @Test void eachFailedConditionIsNamed() {
        assertEquals(List.of("OPERATION_MISSING"), ItemCargoReplayMismatchDiagnostics.failedChecks(false, false, true, true));
        assertEquals(List.of("OPERATION_ID_CHANGED"), ItemCargoReplayMismatchDiagnostics.failedChecks(true, false, true, true));
        assertEquals(List.of("DEVICE_CHANGED"), ItemCargoReplayMismatchDiagnostics.failedChecks(true, true, false, true));
        assertEquals(List.of("ROCKET_CHANGED"), ItemCargoReplayMismatchDiagnostics.failedChecks(true, true, true, false));
        assertEquals(List.of("OPERATION_MISSING", "DEVICE_CHANGED", "ROCKET_CHANGED"),
                ItemCargoReplayMismatchDiagnostics.failedChecks(false, false, false, false));
    }

    @Test void changedKeysAreSortedAndBounded() {
        var before = new CompoundTag(); var now = new CompoundTag();
        before.putInt("same", 1); now.putInt("same", 1);
        before.putInt("wrapper", 1); now.putInt("wrapper", 2);
        before.putInt("removed", 1); now.putInt("added", 1);
        assertEquals("[added, removed, wrapper]", ItemCargoReplayMismatchDiagnostics.changedKeys(before, now));
        assertEquals("[]", ItemCargoReplayMismatchDiagnostics.changedKeys(before, before.copy()));

        var many = new CompoundTag();
        for (int i = 0; i < ItemCargoReplayMismatchDiagnostics.LISTED + 5; i++) { many.putInt(String.format("k%02d", i), i); }
        assertEquals("[k00, k01, k02, k03, k04, k05, k06, k07]+5", ItemCargoReplayMismatchDiagnostics.changedKeys(new CompoundTag(), many));
    }

    @Test void ledgerRowsReportTheGenerationOfAnEmptyLedger() {
        var ledger = EscrowLedger.empty(java.util.UUID.randomUUID());
        assertEquals("generation=0 records=[]", ItemCargoReplayMismatchDiagnostics.ledgerRows(ledger, java.util.UUID.randomUUID(), "x"));
    }
}

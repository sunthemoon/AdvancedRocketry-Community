package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EscrowGameTestLaunchTest {
    @org.junit.jupiter.api.BeforeAll static void bootstrap() {
        io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap.initialize();
    }
    private final UUID ledger = UUID.randomUUID(), logical = UUID.randomUUID(), transfer = UUID.randomUUID();

    private String hold(int backoff) {
        return "ARCE_ESCROW_SUCCESSOR_HOLD ledger=" + ledger + " logical=" + logical + " transfer=" + transfer
                + " serial=0 phase=F1_PROOF reason=SUPERSEDING_SAVE backoff=" + backoff;
    }

    @Test void onlyExactMatchingF1SupersessionHasTwoRemainingAttempts() {
        assertTrue(EscrowGameTestLaunch.retryable(hold(100), ledger, logical, transfer, 1));
        assertTrue(EscrowGameTestLaunch.retryable(hold(400), ledger, logical, transfer, 2));
        assertFalse(EscrowGameTestLaunch.retryable(hold(1200), ledger, logical, transfer, 3));
        assertFalse(EscrowGameTestLaunch.retryable(hold(100), ledger, logical, transfer, 0));
        assertFalse(EscrowGameTestLaunch.retryable(hold(100), ledger, logical, transfer, 2));
    }

    @Test void otherReasonsPhasesIdentitiesOrMissingLogsStillFail() {
        for (String message : new String[] {null, "", hold(100).replace("SUPERSEDING_SAVE", "BARRIER_IO"),
                hold(100).replace("SUPERSEDING_SAVE", "SOURCE_OR_JOURNAL_CHANGED"),
                hold(100).replace("F1_PROOF", "F1_WRITE"), hold(100).replace("serial=0", "serial=1"),
                hold(100).replace(logical.toString(), UUID.randomUUID().toString()),
                hold(100).replace(transfer.toString(), UUID.randomUUID().toString()),
                hold(100).replace(ledger.toString(), UUID.randomUUID().toString()), hold(100) + " extra"}) {
            assertFalse(EscrowGameTestLaunch.retryable(message, ledger, logical, transfer, 1), message);
        }
    }
}

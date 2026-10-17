package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class EscrowTerminalRetriesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @CsvSource({"F6_PROOF,BARRIER_IO", "F7_WRITE,LEDGER_WRITE_FAILED", "F7_WRITE,LEDGER_QUEUE_WAIT"})
    void replacingCaptureOrAttemptDoesNotResetTheLogicalHistory(String phase, String reason) {
        var lines = new ArrayList<EscrowSuccessorRetry.Hold>();
        var retries = new EscrowTerminalRetries(lines::add);
        var record = GovernedTransferFixture.create().record();
        var logical = record.logicalRocketId(); var original = retries.history(logical);
        long tick = 20;
        for (int delay : new int[] {100, 400, 1200, 1200}) {
            // Each attempt has a fresh immutable record and serial, but the same logical rocket.
            record = GovernedTransferFixture.create(tick, 1, false).record();
            assertTrue(retries.ready(logical, tick));
            retries.failed(record, phase, reason, tick);
            assertSame(original, retries.history(logical));
            assertEquals(tick + delay, original.nextTick());
            for (long poll = tick; poll < tick + delay; poll++) { assertFalse(retries.ready(logical, poll)); }
            tick += delay;
        }
        assertEquals(4, lines.size()); assertEquals(logical, lines.get(0).logical());
        assertEquals(phase, lines.get(0).phase()); assertEquals(reason, lines.get(0).reason());
        assertEquals(3, original.failures());
    }

    @Test void changedTransferKeepsHistoryAndOnlyCompletionOrCheckedAbsenceClearsIt() {
        var retries = new EscrowTerminalRetries(hold -> { });
        var record = GovernedTransferFixture.create().record(); var logical = record.logicalRocketId();
        retries.failed(record, "F6_PROOF", "SUPERSEDING_SAVE", 0);
        var history = retries.history(logical);
        history.failed(record.nativeTransfer().orElseThrow().armed().ledger(), logical, UUID.randomUUID(), 2,
                "F7_WRITE", "LEDGER_WRITE_FAILED", 100);
        retries.retain(logical::equals);
        assertSame(history, retries.history(logical)); assertEquals(500, history.nextTick());
        retries.completed(logical); assertTrue(retries.ready(logical, 100));
        assertNotSame(history, retries.history(logical));
        retries.failed(record, "F6_PROOF", "BARRIER_FORCE", 200);
        retries.retain(id -> false); assertTrue(retries.ready(logical, 200));
    }

    @Test void historyHasTheActiveFlightBoundAndDoesNotEvictHeldFlights() {
        var retries = new EscrowTerminalRetries(hold -> { }); var first = new UUID(41, 0);
        var retained = retries.history(first);
        for (int i = 1; i < 64; i++) { retries.history(new UUID(41, i)); }
        assertThrows(IllegalArgumentException.class, () -> retries.history(new UUID(41, 64)));
        assertSame(retained, retries.history(first));
        retries.completed(new UUID(41, 63)); assertNotNull(retries.history(new UUID(41, 64)));
        retries.clear(); assertNotSame(retained, retries.history(first));
    }
}

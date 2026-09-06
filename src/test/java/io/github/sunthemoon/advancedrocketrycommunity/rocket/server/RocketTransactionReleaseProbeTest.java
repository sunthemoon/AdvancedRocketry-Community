package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import org.junit.jupiter.api.Test;

class RocketTransactionReleaseProbeTest {
    @Test
    void disabledProbeDoesNotParseSelectionOrTouchServerAndWorld() {
        var probe = new RocketTransactionReleaseProbe(false, "invalid");
        var bound = probe.bind(null, RocketTransactionType.ASSEMBLY, null, null, RocketTransactionJournal.NO_OP);
        assertSame(RocketTransactionJournal.NO_OP, bound.journal());
    }

    @Test
    void absentSelectionDoesNotChangeOrdinaryReleaseTestRuns() {
        var probe = new RocketTransactionReleaseProbe(true, null);
        assertSame(RocketTransactionJournal.NO_OP,
                probe.bind(null, RocketTransactionType.ASSEMBLY, null, null, RocketTransactionJournal.NO_OP).journal());
    }

    @Test
    void wrongOperationDoesNotConsumeSelectionAndLifecycleClearDisarmsIt() {
        var probe = new RocketTransactionReleaseProbe(true, "DISASSEMBLY:RESTORED:5");
        assertSame(RocketTransactionJournal.NO_OP,
                probe.bind(null, RocketTransactionType.ASSEMBLY, null, null, RocketTransactionJournal.NO_OP).journal());
        probe.clear();
        assertSame(RocketTransactionJournal.NO_OP,
                probe.bind(null, RocketTransactionType.DISASSEMBLY, null, null, RocketTransactionJournal.NO_OP).journal());
    }

    @Test
    void invalidEnabledSelectionFailsImmediately() {
        assertThrows(IllegalArgumentException.class, () -> new RocketTransactionReleaseProbe(true, "invalid"));
    }
}

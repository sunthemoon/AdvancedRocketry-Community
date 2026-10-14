package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class RocketJournalContinuationTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private Path target() { return directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()); }
    private static CheckedSavedDataPolicy atomic() { return CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS); }

    @Test void stopAndAbortFinishPendingRequestsWithoutEvictingTransferAuthority() {
        for (boolean abort : new boolean[] {false, true}) {
            var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); data.put(record);
            var ledger = new RocketOperationLedger(1); UUID request = UUID.randomUUID();
            assertEquals(RocketOperationLedger.BeginResult.STARTED, ledger.begin(request));
            var drops = new AtomicInteger();
            assertTrue(data.queueEffect(record.logicalRocketId(), () -> fail("Stopped gameplay ran"),
                    () -> { drops.incrementAndGet(); ledger.finish(request, false); }));
            if (abort) { data.abort(); } else { data.stop(); }
            assertEquals(1, drops.get()); assertSame(record, data.find(record.transferId()).orElseThrow());
            assertEvictable(ledger, request); data.stop(); assertEquals(1, drops.get());
        }
    }

    @Test void changedRecordAndUncapturedGenerationDropTheirRequests() {
        for (boolean capture : new boolean[] {false, true}) {
            var data = new RocketTransferSavedData(); var record = RocketTransferSavedDataTest.record(); data.put(record);
            var ledger = new RocketOperationLedger(1); UUID request = UUID.randomUUID(); ledger.begin(request);
            var drops = new AtomicInteger();
            assertTrue(data.queueEffect(record.logicalRocketId(), () -> fail("Stale gameplay ran"),
                    () -> { drops.incrementAndGet(); ledger.finish(request, false); }));
            try {
                if (capture) { data.requestWrite(target(), atomic()); }
                var changed = record.destinationSpawned(UUID.randomUUID()); data.put(changed);
                if (capture) { data.drainForTests(); }
                data.requestWrite(target(), atomic()); data.drainForTests(); data.pollWriter(1);
                assertEquals(1, drops.get()); assertSame(changed, data.find(record.transferId()).orElseThrow());
                assertEvictable(ledger, request);
            } finally { data.stop(); }
            assertEquals(1, drops.get());
        }
    }

    @Test void authorityRefusalRuntimeExceptionAndErrorAllFinishThePendingRequest() {
        for (int mode = 0; mode < 3; mode++) {
            int failure = mode; var data = new RocketTransferSavedData();
            var record = RocketTransferSavedDataTest.record(); data.put(record);
            var ledger = new RocketOperationLedger(1); UUID request = UUID.randomUUID(); ledger.begin(request);
            var drops = new AtomicInteger();
            assertTrue(data.queueEffect(record.logicalRocketId(), () -> {
                if (failure == 1) { throw new IllegalStateException("Injected continuation throw"); }
                if (failure == 2) { throw new AssertionError("Injected continuation Error"); }
                return false; // The dependent action's flight authority refused.
            }, () -> { drops.incrementAndGet(); ledger.finish(request, false); }));
            try {
                data.requestWrite(target(), atomic()); data.drainForTests();
                if (mode == 2) { assertThrows(AssertionError.class, () -> data.pollWriter(1)); }
                else { data.pollWriter(1); }
                assertEquals(1, drops.get()); assertSame(record, data.find(record.transferId()).orElseThrow());
                assertEvictable(ledger, request);
            } finally { data.stop(); }
            assertEquals(1, drops.get());
        }
    }

    private static void assertEvictable(RocketOperationLedger ledger, UUID request) {
        assertEquals(RocketOperationLedger.Outcome.FAILED, ledger.outcome(request));
        assertEquals(RocketOperationLedger.BeginResult.STARTED, ledger.begin(UUID.randomUUID()));
        assertNull(ledger.outcome(request)); assertEquals(1, ledger.size());
    }
}

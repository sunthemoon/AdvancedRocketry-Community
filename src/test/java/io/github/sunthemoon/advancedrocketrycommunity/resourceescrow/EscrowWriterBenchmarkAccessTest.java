package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W129aF: B3a's escrow and receipt roots publish their paced mutations, readback paths included, on real files. */
final class EscrowWriterBenchmarkAccessTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void pacedMutationsOfBothRootsPublishOnOwnerTicksAndEveryTenthTakesTheReadbackPath() throws Exception {
        try (var access = EscrowWriterBenchmarkAccess.create(temp, 2, 4, 16, 0)) {
            var composition = access.composition();
            assertEquals(Map.of("operations", 4, "floors", 2), subset(composition.get("escrow_ledger"), "operations", "floors"));
            assertEquals(Map.of("receipts", 16, "owners", 1), subset(composition.get("bay_receipts"), "receipts", "owners"));
            long tick = access.now() + 20;
            for (int k = 0; k < 20 * 21; k++, tick++) {
                access.tick(tick);
                if (k % 20 == 0) { access.mutateLedger(); }
                if (k % 20 == 10) { access.mutateReceipt(); }
                Thread.sleep(2);
            }
            for (int k = 0; k < 2_000 && access.holdsWork(); k++, tick++) { Thread.sleep(2); access.tick(tick); }
            assertFalse(access.holdsWork());
            assertTrue(access.operational());
            var results = access.results();
            var ledger = (Map<?, ?>) results.get("escrow_ledger"); var receipts = (Map<?, ?>) results.get("bay_receipts");
            assertEquals(21L, (long) ledger.get("mutations") + (long) ledger.get("skipped_while_unpublished"));
            assertEquals((long) ledger.get("mutations"), (long) (int) ledger.get("published"));
            assertEquals(21L, (long) receipts.get("mutations") + (long) receipts.get("skipped_while_unpublished"));
            assertEquals((long) receipts.get("mutations"), (long) (int) receipts.get("published"));
            assertEquals(0L, receipts.get("refused"));
            assertTrue((int) ledger.get("readback_paths_taken") >= 1 && (int) receipts.get("readback_paths_taken") >= 1);
            assertEquals(Map.of("COMMITTED", (int) (long) receipts.get("mutations")), receipts.get("outcomes"));
            // The files hold what RAM published.
            var disk = EscrowCodec.decode(CheckedSavedDataFile.readPayload(temp.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()),
                    ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow());
            assertEquals(ledger.get("writer_generation"), disk.writerGeneration());
            var receiptDisk = BayReceiptCodec.decode(CheckedSavedDataFile.readPayload(temp.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName()),
                    ManagedSavedDataType.BAY_RECEIPTS).orElseThrow());
            assertEquals(receipts.get("writer_generation"), receiptDisk.writerGeneration());
            assertEquals(16, receiptDisk.receipts().size());
        }
    }

    @Test void theBenchmarkCompositionStopsAtAdmissionCapacityAndFillsTheRetainedReceiptBound() throws Exception {
        try (var access = EscrowWriterBenchmarkAccess.create(temp, EscrowWriterBenchmarkAccess.FLOORS, EscrowWriterBenchmarkAccess.OPERATIONS,
                EscrowWriterBenchmarkAccess.RECEIPTS, 0)) {
            var composition = access.composition();
            var ledger = (Map<?, ?>) composition.get("escrow_ledger"); var receipts = (Map<?, ?>) composition.get("bay_receipts");
            assertEquals(128, ledger.get("floors"));
            int operations = (int) ledger.get("operations");
            assertTrue(operations > 0 && operations < EscrowLimits.OPERATIONS, ledger::toString);
            assertEquals("REFUSED: Settlement/flight root reservation exhausted", ledger.get("admission_stop"),
                    "the ledger grows until admission refuses, so admission can reach it");
            assertEquals(operations, ledger.get("pending_slots"));
            assertTrue((int) ledger.get("root_bytes") <= EscrowLimits.ROOT_BYTES);
            assertEquals(Map.of("receipts", 4_096, "owners", 32), subset(receipts, "receipts", "owners"));
            assertEquals(4_096, receipts.get("retained_slots"));
            assertTrue((int) receipts.get("root_bytes") > BayReceiptCodec.ROOT_BYTES * 9L / 10
                    && (int) receipts.get("root_bytes") <= BayReceiptCodec.ROOT_BYTES, receipts::toString);
            assertTrue(access.operational());
        }
    }

    private static Map<Object, Object> subset(Object map, String... keys) {
        var result = new java.util.HashMap<Object, Object>();
        for (String key : keys) { result.put(key, ((Map<?, ?>) map).get(key)); }
        return result;
    }
}

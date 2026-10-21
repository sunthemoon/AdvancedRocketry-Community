package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BayReceiptFenceTest {
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void notLoadedFailsClosedAndProvenNoFileAllowsBothActions() throws Exception {
        try (var h = new BayReceiptHarness(new EscrowAdmissionAuthority(), temporary)) {
            UUID satellite = UUID.randomUUID();
            assertTrue(h.receipts.blocksInsertion(satellite));
            assertTrue(h.receipts.blocksDecommission(satellite));
            h.receipts.start(null, CheckedSavedDataFile.Presence.ABSENT, h.file, 0);
            assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, h.receipts.state());
            assertFalse(h.receipts.blocksInsertion(satellite));
            assertFalse(h.receipts.blocksDecommission(satellite));
        }
    }

    @Test void phaseAndExactAcknowledgementMatrixIncludesQueuedCapturedAndRemoval() throws Exception {
        for (BayReceipt.Phase phase : BayReceipt.Phase.values()) {
            try (var h = BayReceiptHarness.empty(temporary.resolve(phase.name()))) {
                var receipt = receipt(1, id(100), id(200), phase);
                assertTrue(h.put(receipt).queued());
                assertEquals(phase != BayReceipt.Phase.REJECTED, h.receipts.blocksInsertion(receipt.satellite()));
                assertEquals(phase != BayReceipt.Phase.REJECTED, h.receipts.blocksDecommission(receipt.satellite()));
                h.settle();
                assertTrue(h.store().acknowledged(receipt.op()));
                assertEquals(phase != BayReceipt.Phase.REJECTED, h.receipts.blocksInsertion(receipt.satellite()));
                assertEquals(phase == BayReceipt.Phase.INTENT || phase == BayReceipt.Phase.REPAIR,
                        h.receipts.blocksDecommission(receipt.satellite()));
                assertTrue(h.remove(receipt).queued());
                assertEquals(phase != BayReceipt.Phase.REJECTED, h.receipts.blocksInsertion(receipt.satellite()));
                assertEquals(phase != BayReceipt.Phase.REJECTED, h.receipts.blocksDecommission(receipt.satellite()));
                h.settle();
                assertFalse(h.receipts.blocksInsertion(receipt.satellite()));
                assertFalse(h.receipts.blocksDecommission(receipt.satellite()));
            }
        }
    }

    @Test void registeredTransitionDoesNotReleaseIntentFenceBeforeItsOwnAcknowledgement() throws Exception {
        try (var h = BayReceiptHarness.empty(temporary)) {
            var intent = receipt(1, id(100), id(200), BayReceipt.Phase.INTENT);
            assertTrue(h.put(intent).queued()); h.settle();
            var registered = receipt(1, id(100), id(200), BayReceipt.Phase.REGISTERED);
            assertTrue(h.put(registered).queued());
            assertFalse(h.store().acknowledged(intent.op()));
            assertTrue(h.receipts.blocksDecommission(intent.satellite()));
            h.settle();
            assertTrue(h.store().acknowledged(intent.op()));
            assertFalse(h.receipts.blocksDecommission(intent.satellite()));
            assertTrue(h.receipts.blocksInsertion(intent.satellite()));
        }
    }

    @Test void twoReloadsIndexDurableIntentBeforeAnyTickAndKeepRegisteredRemovalSemantics() throws Exception {
        for (BayReceipt.Phase phase : List.of(BayReceipt.Phase.INTENT, BayReceipt.Phase.REGISTERED, BayReceipt.Phase.REPAIR)) {
            Path directory = temporary.resolve(phase.name());
            var receipt = receipt(1, id(100), id(200), phase);
            try (var h = BayReceiptHarness.empty(directory)) { assertTrue(h.put(receipt).queued()); h.settle(); }
            for (int restart = 0; restart < 2; restart++) {
                try (var h = new BayReceiptHarness(new EscrowAdmissionAuthority(), directory)) {
                    assertTrue(h.receipts.blocksDecommission(receipt.satellite()));
                    var loaded = BayReceiptSavedData.load(CheckedSavedDataFile.readPayload(h.file,
                            io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.BAY_RECEIPTS).orElseThrow());
                    h.receipts.start(loaded, CheckedSavedDataFile.Presence.PRESENT, h.file, 0);
                    assertTrue(h.receipts.blocksInsertion(receipt.satellite()));
                    assertEquals(phase != BayReceipt.Phase.REGISTERED, h.receipts.blocksDecommission(receipt.satellite()));
                    assertEquals(receipt, h.store().find(receipt.satellite()).orElseThrow());
                }
            }
        }
    }
}

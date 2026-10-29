package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BayReceiptReleaseGroupTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void releaseIsOneExactlyAcknowledgedGenerationAndRetainsAllCharges() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            var group = new ArrayList<BayReceipt>();
            for (int i = 1; i <= 8; i++) {
                var r = receipt(i, id(99), id(88), BayReceipt.Phase.REPAIR);
                group.add(r); assertTrue(h.put(r).queued());
            }
            h.settle(); long generation = h.store().durable().writerGeneration();
            var released = group.stream().map(BayReceipt::withReleased).toList();
            var outcomes = new ArrayList<BayReceiptWriter.Outcome>();
            assertTrue(h.store().releaseGroup(released, outcomes::add).queued());
            released.forEach(r -> assertFalse(h.store().acknowledged(r.op())));
            h.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), outcomes);
            assertEquals(generation + 1, h.store().durable().writerGeneration());
            assertEquals(released, h.store().durable().receipts());
            assertEquals(8, h.authority.pendingCount()); assertEquals(8, h.authority.retainedCount());
            released.forEach(r -> {
                assertTrue(h.store().acknowledged(r.op()));
                assertEquals(BayReceiptStore.Status.RELEASED_LOCKED, h.remove(r).status());
                assertTrue(h.store().holdsRocket(r.rocket()));
            });
        }
    }

    @Test void releaseRefusesAnyOtherFieldChangeWithoutMutatingTheGroup() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            var r = receipt(1, id(99), id(88), BayReceipt.Phase.INTENT);
            assertTrue(h.put(r).queued()); h.settle();
            for (var wrong : List.of(r, r.withReleased().withPhase(BayReceipt.Phase.REPAIR, "CONFLICT", 8),
                    r.withReleased().withKey(id(77), r.bay()))) {
                assertEquals(BayReceiptStore.Status.INVALID, h.store().releaseGroup(List.of(wrong), outcome -> fail()).status());
                assertEquals(List.of(r), h.store().receipts());
                assertTrue(h.store().acknowledged(r.op()));
            }
            assertEquals(BayReceiptStore.Status.INVALID, h.store().putGroup(List.of(r.withReleased()), outcome -> fail()).status());
        }
    }
}

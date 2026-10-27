package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W170 A0: the receipt store's group calls, D4's one-generation publication and D7's guarded removal, on real files. */
final class BayReceiptGroupTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = id(9), ROCKET = id(77);

    private static List<BayReceipt> intents(int count) {
        var receipts = new ArrayList<BayReceipt>();
        for (long n = 1; n <= count; n++) { receipts.add(receipt(n, OWNER, ROCKET, BayReceipt.Phase.INTENT)); }
        return receipts;
    }
    private static BayReceipt registered(BayReceipt intent) {
        return new BayReceipt(intent.op(), intent.seq(), intent.rocket(), intent.bay(), intent.satellite(), intent.owner(),
                intent.definition(), intent.kind(), intent.orbit(), intent.packageSig(), intent.transfer(), intent.srcBefore(),
                hash(2), intent.dstBefore(), hash(4), BayReceipt.Phase.REGISTERED, "NONE", false, false, 7, intent.createdTick(), 9, 0);
    }
    private static List<BayReceipt> registered(List<BayReceipt> intents) { return intents.stream().map(BayReceiptGroupTest::registered).toList(); }
    private static void settleAll(BayReceiptHarness harness, List<BayReceipt> receipts) {
        receipts.forEach(receipt -> assertTrue(harness.put(receipt).queued())); harness.settle();
    }

    @Test void eightRegisteredOutcomesPublishInOneGenerationAndChargeNothingNew() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var intents = intents(8); settleAll(harness, intents);
            long generation = harness.store().durable().writerGeneration();
            assertEquals(8, harness.authority.pendingCount()); assertEquals(8, harness.authority.retainedCount());
            intents.forEach(intent -> assertTrue(harness.store().blocksDecommission(intent.satellite()), "INTENT fences decommission"));
            var outcomes = new ArrayList<BayReceiptWriter.Outcome>();
            var group = registered(intents);
            var admission = harness.store().putGroup(group, outcomes::add);
            assertTrue(admission.queued(), admission::toString);
            group.forEach(receipt -> assertTrue(harness.store().blocksDecommission(receipt.satellite()), "unacknowledged REGISTERED fences"));
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), outcomes, "one completion for the group");
            assertEquals(generation + 1, harness.store().durable().writerGeneration(), "one generation");
            assertEquals(group, harness.store().durable().receipts());
            group.forEach(receipt -> {
                assertTrue(harness.store().acknowledged(receipt.op()));
                assertFalse(harness.store().blocksDecommission(receipt.satellite()), "the exact acknowledgement lifts the fence");
                assertTrue(harness.store().blocks(receipt.satellite()), "insertion stays fenced while REGISTERED exists");
            });
            assertEquals(8, harness.authority.pendingCount()); assertEquals(8, harness.authority.retainedCount());
        }
    }

    @Test void aGroupWithAnyRefusedMemberChangesNothing() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var intents = intents(3); settleAll(harness, intents);
            var before = harness.store().receipts();
            var unknownOp = new ArrayList<>(registered(intents)); unknownOp.add(registered(receipt(9, OWNER, ROCKET, BayReceipt.Phase.INTENT)));
            assertEquals(BayReceiptStore.Status.INVALID, harness.store().putGroup(unknownOp, outcome -> fail()).status());
            var moved = new ArrayList<>(registered(intents)); moved.set(1, moved.get(1).withKey(id(78), moved.get(1).bay()));
            assertEquals(BayReceiptStore.Status.INVALID, harness.store().putGroup(moved, outcome -> fail()).status(), "a key change");
            var twice = List.of(registered(intents.get(0)), registered(intents.get(0)));
            assertEquals(BayReceiptStore.Status.INVALID, harness.store().putGroup(twice, outcome -> fail()).status(), "one op twice");
            var released = List.of(registered(intents.get(0)).withReleased());
            assertEquals(BayReceiptStore.Status.INVALID, harness.store().putGroup(released, outcome -> fail()).status());
            assertEquals(BayReceiptStore.Status.INVALID, harness.store().putGroup(List.of(), outcome -> fail()).status());
            assertTrue(harness.put(intents.get(2).withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 4)).queued());
            assertEquals(BayReceiptStore.Status.BUSY, harness.store().putGroup(registered(intents), outcome -> fail()).status(),
                    "a member with a change in flight");
            harness.settle();
            assertEquals(3, harness.store().receipts().size());
            assertEquals(before.subList(0, 2), harness.store().receipts().subList(0, 2), "the refusals changed nothing");
        }
    }

    @Test void aDefinitelyFailedGroupRestoresEveryIntent() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var intents = intents(4); settleAll(harness, intents);
            var outcomes = new ArrayList<BayReceiptWriter.Outcome>();
            // A directory at the staging path refuses before staging: FAILED_BEFORE_REPLACE (W128's B12_FAILED).
            Path pending = Files.createDirectories(harness.file.resolveSibling(harness.file.getFileName() + ".arce-pending"));
            assertTrue(harness.store().putGroup(registered(intents), outcomes::add).queued());
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE), outcomes);
            assertEquals(intents, harness.store().receipts(), "every INTENT is back");
            assertEquals(intents, harness.store().durable().receipts());
            intents.forEach(intent -> assertTrue(harness.store().blocksDecommission(intent.satellite())));
            Files.delete(pending);
            var retried = new ArrayList<BayReceiptWriter.Outcome>();
            assertTrue(harness.store().putGroup(registered(intents), retried::add).queued(), "the publication runs again");
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), retried);
        }
    }

    @Test void theGuardedRemovalNeedsAQuietStoreAndRemovesTheGroupInOneGeneration() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var group = registered(intents(2)); settleAll(harness, intents(2));
            assertTrue(harness.store().putGroup(group, outcome -> { }).queued()); harness.settle();
            var other = receipt(20, OWNER, id(80), BayReceipt.Phase.INTENT);
            assertTrue(harness.put(other).queued());
            var ops = group.stream().map(BayReceipt::op).toList();
            assertEquals(BayReceiptStore.Status.BUSY, harness.store().removeGroup(ops, () -> true, outcome -> fail()).status(),
                    "another change is queued or captured");
            harness.settle();
            long generation = harness.store().durable().writerGeneration();
            var outcomes = new ArrayList<BayReceiptWriter.Outcome>();
            assertTrue(harness.store().removeGroup(ops, () -> true, outcomes::add).queued());
            assertEquals(3, harness.authority.pendingCount(), "slots end only at the exact acknowledgement");
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), outcomes);
            assertEquals(generation + 1, harness.store().durable().writerGeneration());
            assertEquals(List.of(other), harness.store().durable().receipts());
            assertEquals(1, harness.authority.pendingCount()); assertEquals(1, harness.authority.retainedCount());
            assertEquals(BayReceiptStore.Status.INVALID, harness.store().removeGroup(ops, () -> true, outcome -> fail()).status(),
                    "removed receipts cannot be removed again");
        }
    }

    @Test void aRefusedGuardRestoresTheGroupAndALaterPutRidesItsOwnGeneration() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var group = registered(intents(2)); settleAll(harness, intents(2));
            assertTrue(harness.store().putGroup(group, outcome -> { }).queued()); harness.settle();
            var proof = new AtomicBoolean(true);
            var outcomes = new ArrayList<BayReceiptWriter.Outcome>();
            var ops = group.stream().map(BayReceipt::op).toList();
            assertTrue(harness.store().removeGroup(ops, proof::get, outcomes::add).queued());
            var later = receipt(30, OWNER, id(81), BayReceipt.Phase.INTENT);
            assertTrue(harness.put(later).queued(), "a later put waits for its own generation");
            proof.set(false); // the proof is invalidated before the replacement (B16)
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.GUARD_REFUSED), outcomes);
            assertTrue(harness.store().durable().receipts().containsAll(group), "every REGISTERED receipt stays durable");
            assertTrue(harness.store().durable().receipts().contains(later), "the later put was not cancelled by the guard");
            group.forEach(receipt -> assertTrue(harness.store().acknowledged(receipt.op())));
            assertEquals(3, harness.authority.pendingCount());
            assertFalse(harness.store().writer().unavailable(), "a refused guard is not an IO failure");
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.datagen.V180BayReceiptLanguage;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W27c1 A1, W27A-DESIGN section 5 (N5): the orphaned-REJECTED leak, the audited retirement and every refusal. */
final class BayReceiptRetirementTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = id(9);
    private static final BayReceiptRetirement.Journals CLEAR = journals(true, true);
    private static BayReceiptRetirement.Journals journals(boolean transfer, boolean transaction) {
        return new BayReceiptRetirement.Journals() {
            @Override public boolean transferClear(UUID rocket, UUID transferId) { return transfer; }
            @Override public boolean transactionClear(UUID rocket) { return transaction; }
        };
    }
    private static BayReceipt orphan(long n) { return receipt(n, OWNER, id(70_000 + n), BayReceipt.Phase.REJECTED); }
    private static BayReceiptRetirement.Refusal refusal(BayReceiptStore store, BayReceipt receipt) {
        return BayReceiptRetirement.check(store, receipt.satellite(), CLEAR).refusal();
    }

    @Test void orphanedRejectedReceiptsFillTheOwnersSlotsUntilOneIsRetiredAtItsExactAcknowledgement() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            for (long n = 1; n <= EscrowAdmissionAuthority.RETAINED_PER_OWNER; n++) { assertTrue(harness.put(orphan(n)).queued()); }
            harness.settle();
            var next = orphan(1_000);
            assertEquals(EscrowAdmissionAuthority.Retention.OWNER_FULL, harness.put(next).retention(), "the leak: the owner's 128 are full");
            var target = orphan(7);
            var checked = BayReceiptRetirement.check(harness.receipts, target.satellite(), CLEAR);
            assertNull(checked.refusal()); assertEquals(target, checked.receipt());
            var done = new ArrayList<BayReceiptWriter.Outcome>();
            assertTrue(BayReceiptRetirement.retire(harness.store(), target, "Operator/" + OWNER, done::add).queued());
            assertEquals(EscrowAdmissionAuthority.Retention.OWNER_FULL, harness.authority.checkRetain(next.op(), OWNER),
                    "the retained slot ends only at the exact removal acknowledgement");
            assertEquals(BayReceiptRetirement.Refusal.UNACKNOWLEDGED, refusal(harness.store(), target), "a retirement in flight");
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), done);
            assertFalse(harness.store().durable().receipts().contains(target));
            assertEquals(BayReceiptRetirement.Refusal.NOT_FOUND, refusal(harness.store(), target));
            assertTrue(harness.put(next).queued(), "admitted again"); harness.settle();
            assertEquals(128, harness.authority.retainedFor(OWNER));
        }
    }

    @Test void everyRefusalOfTheCommandChangesNothing() throws Exception {
        var authority = new EscrowAdmissionAuthority();
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true, List.of()));
        var unloaded = new BayReceipts(authority.register(EscrowAdmissionAuthority.Consumer.BAY), CheckedSavedDataFile::atomicMove);
        assertEquals(BayReceiptRetirement.Refusal.NOT_LOADED, BayReceiptRetirement.check(unloaded, id(1), CLEAR).refusal());
        unloaded.start(null, CheckedSavedDataFile.Presence.ABSENT, temp.resolve("none.dat"), 0);
        assertEquals(BayReceiptRetirement.Refusal.NOT_FOUND, BayReceiptRetirement.check(unloaded, id(1), CLEAR).refusal(), "NO_FILE");
        try (var harness = BayReceiptHarness.empty(temp.resolve("store"))) {
            var rejected = orphan(1); var intent = receipt(2, OWNER, id(80), BayReceipt.Phase.INTENT);
            var released = orphan(3).withReleased(); var sameRocket = receipt(4, OWNER, id(90), BayReceipt.Phase.REJECTED);
            var holder = receipt(5, OWNER, id(90), BayReceipt.Phase.REPAIR);
            for (var receipt : List.of(rejected, intent, released, sameRocket, holder)) { assertTrue(harness.put(receipt).queued()); }
            assertEquals(BayReceiptRetirement.Refusal.UNACKNOWLEDGED, refusal(harness.store(), rejected), "not yet acknowledged");
            harness.settle();
            assertNull(refusal(harness.store(), rejected));
            assertEquals(BayReceiptRetirement.Refusal.NOT_FOUND, refusal(harness.store(), receipt(99, OWNER, id(99), BayReceipt.Phase.REJECTED)));
            assertEquals(BayReceiptRetirement.Refusal.NOT_REJECTED, refusal(harness.store(), intent));
            assertEquals(BayReceiptRetirement.Refusal.RELEASED, refusal(harness.store(), released));
            assertEquals(BayReceiptRetirement.Refusal.ROCKET_PENDING, refusal(harness.store(), sameRocket));
            assertEquals(BayReceiptRetirement.Refusal.TRANSFER_RECORD,
                    BayReceiptRetirement.check(harness.store(), rejected.satellite(), journals(false, true)).refusal());
            assertEquals(BayReceiptRetirement.Refusal.TRANSACTION_RECORD,
                    BayReceiptRetirement.check(harness.store(), rejected.satellite(), journals(true, false)).refusal());
            for (var refusal : BayReceiptRetirement.Refusal.values()) {
                for (boolean chinese : new boolean[] {false, true}) {
                    assertTrue(V180BayReceiptLanguage.entries(chinese).containsKey(refusal.translationKey()), refusal + " " + chinese);
                }
                var checked = new BayReceiptRetirement.Checked(intent, refusal);
                assertEquals(refusal.translationKey(), ((net.minecraft.network.chat.contents.TranslatableContents)
                        BayReceiptCommands.refusal(checked, intent.satellite()).getContents()).getKey());
            }
            harness.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertTrue(harness.put(intent.withPhase(BayReceipt.Phase.INTENT, "NONE", 10)).queued());
            harness.until(harness.store().writer()::unavailable); // Three unknown outcomes of the exact candidate.
            assertEquals(BayReceiptRetirement.Refusal.UNAVAILABLE, refusal(harness.store(), rejected));
            assertEquals(5, harness.store().receipts().size());
            harness.committer.set(CheckedSavedDataFile::atomicMove); harness.settle();
        }
    }

    @Test void aFailedRetirementKeepsTheReceiptAndAnUnknownOneKeepsItsSlotUntilResolved() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            for (long n = 1; n <= EscrowAdmissionAuthority.RETAINED_PER_OWNER; n++) { assertTrue(harness.put(orphan(n)).queued()); }
            harness.settle();
            var target = orphan(3); var done = new ArrayList<BayReceiptWriter.Outcome>();
            Path pending = harness.file.resolveSibling(harness.file.getFileName() + ".arce-pending");
            Files.createDirectory(pending); // Staging is refused before any replacement: a definite failure.
            assertTrue(BayReceiptRetirement.retire(harness.store(), target, "Server", done::add).queued());
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE), done);
            assertTrue(harness.store().receipts().contains(target) && harness.store().durable().receipts().contains(target));
            assertEquals(128, harness.authority.retainedFor(OWNER), "a failed retirement frees nothing");
            assertNull(refusal(harness.store(), target), "and it can be retried");
            Files.delete(pending); harness.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertTrue(BayReceiptRetirement.retire(harness.store(), target, "Server", done::add).queued());
            harness.until(harness.store().writer()::unknown);
            assertEquals(List.of(BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE), done, "an unknown outcome is not final");
            assertEquals(128, harness.authority.retainedFor(OWNER), "an unknown retirement keeps the retained slot");
            assertEquals(EscrowAdmissionAuthority.Retention.OWNER_FULL, harness.authority.checkRetain(id(5_000), OWNER));
            harness.committer.set(CheckedSavedDataFile::atomicMove); harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE, BayReceiptWriter.Outcome.COMMITTED), done);
            assertEquals(127, harness.authority.retainedFor(OWNER));
        }
    }

    /** The retirement audit lines, captured from the root logger. */
    private static final class AuditLines extends org.apache.logging.log4j.core.appender.AbstractAppender implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger root = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
        private final List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
        AuditLines() {
            super("bay-receipt-retirement-audit", null, null, true, org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY);
            start(); root.addAppender(this);
        }
        @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
            String line = event.getMessage().getFormattedMessage();
            if (line.startsWith("ARCE_BAY_RECEIPT_RETIREMENT ")) { lines.add(event.getLevel() + " " + line); }
        }
        @Override public void close() { root.removeAppender(this); stop(); }
    }
    private static String audit(BayReceipt receipt, String operator, String outcome) {
        return "INFO ARCE_BAY_RECEIPT_RETIREMENT operator=" + operator + " rocket=" + receipt.rocket() + " bay=" + receipt.bay().x() + ","
                + receipt.bay().y() + "," + receipt.bay().z() + " satellite=" + receipt.satellite() + " op=" + receipt.op() + " outcome=" + outcome;
    }

    /** W27C1-R5 under R37 item 3: the operator's action is audited when queued, and the final outcome adds a second line. */
    @Test void theAuditLineIsWrittenWhenTheRemovalIsQueuedAndTheFinalOutcomeAddsASecondLine() throws Exception {
        try (var logs = new AuditLines(); var harness = BayReceiptHarness.empty(temp)) {
            var first = orphan(1); var second = orphan(2); String operator = "Operator/" + OWNER;
            assertTrue(harness.put(first).queued()); assertTrue(harness.put(second).queued()); harness.settle();
            var done = new ArrayList<BayReceiptWriter.Outcome>();
            assertTrue(BayReceiptRetirement.retire(harness.store(), first, operator, done::add).queued());
            assertEquals(List.of(audit(first, operator, "REQUESTED")), logs.lines, "audited when the removal is queued");
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), done);
            assertEquals(List.of(audit(first, operator, "REQUESTED"), audit(first, operator, "COMMITTED")), logs.lines);
            assertFalse(BayReceiptRetirement.retire(harness.store(), first, "Server", done::add).queued());
            assertEquals(2, logs.lines.size(), "a refused removal writes no line");
            // The stop drain gives up after three unknown outcomes: no final outcome, but the action stays audited.
            harness.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertTrue(BayReceiptRetirement.retire(harness.store(), second, "Server", done::add).queued());
            harness.store().close();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED), done, "no final outcome was published");
            assertEquals(List.of(audit(first, operator, "REQUESTED"), audit(first, operator, "COMMITTED"), audit(second, "Server", "REQUESTED")),
                    logs.lines);
        }
    }

    @Test void theAuditOperatorIsBoundedAndPrintable() {
        assertEquals("Ops_name_x", BayReceiptRetirement.operator("Ops name\nx"));
        assertEquals(80, BayReceiptRetirement.operator("x".repeat(500)).length());
        assertEquals("unknown", BayReceiptRetirement.operator(""));
        for (String key : List.of("requested", "retired", "failed")) {
            assertTrue(V180BayReceiptLanguage.entries(false).containsKey("message.advancedrocketrycommunity.bay_receipt." + key));
            assertTrue(V180BayReceiptLanguage.entries(true).containsKey("message.advancedrocketrycommunity.bay_receipt." + key));
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W27c1 A0 and A1: the store's unions, limits and exact acknowledgements on real files. */
final class BayReceiptStoreTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = id(9);
    private static BayReceipt intent(long n, UUID rocket) { return receipt(n, OWNER, rocket, BayReceipt.Phase.INTENT); }
    private static BayReceipt rejected(long n, UUID owner) { return receipt(n, owner, id(70_000 + n), BayReceipt.Phase.REJECTED); }
    private static BayReceipt reKeyed(BayReceipt receipt, UUID rocket, RocketPosition bay) { return receipt.withKey(rocket, bay); }
    private static void assertQueued(BayReceiptStore.Admission admission) { assertEquals(BayReceiptStore.Status.QUEUED, admission.status(), admission::toString); }

    @Test void retainedReceiptsAreLimitedTo4096PerServerAnd128PerOwnerIndependentlyOfPending() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            assertEquals(EscrowAdmissionAuthority.RETAINED_SECTION_BYTES,
                    EscrowAdmissionAuthority.RETAINED * EscrowAdmissionAuthority.RETAINED_BYTES,
                    "the byte reservation charges each receipt at its 1 KiB ceiling; its boundary is the count's");
            long n = 0;
            for (int i = 0; i < EscrowAdmissionAuthority.RETAINED_PER_OWNER; i++) { assertQueued(harness.put(rejected(++n, OWNER))); }
            var refused = harness.put(rejected(++n, OWNER));
            assertEquals(new BayReceiptStore.Admission(BayReceiptStore.Status.RETAIN_REFUSED, EscrowAdmissionAuthority.Retention.OWNER_FULL, null), refused);
            assertEquals(128, harness.store().receipts().size(), "a refusal changes nothing");
            for (int owner = 1; owner < EscrowAdmissionAuthority.RETAINED / EscrowAdmissionAuthority.RETAINED_PER_OWNER; owner++) {
                for (int i = 0; i < EscrowAdmissionAuthority.RETAINED_PER_OWNER; i++) { assertQueued(harness.put(rejected(++n, id(1_000 + owner)))); }
            }
            assertEquals(4_096, harness.authority.retainedCount()); assertEquals(128, harness.authority.retainedFor(OWNER));
            assertEquals(EscrowAdmissionAuthority.Retention.SERVER_FULL, harness.put(rejected(++n, id(5_000))).retention());
            assertEquals(0, harness.authority.pendingCount(), "REJECTED receipts hold no pending slot");
            assertEquals(EscrowAdmissionAuthority.Result.RESERVED, harness.authority.checkReserve(id(900_003), OWNER, id(2),
                    EscrowAdmissionAuthority.Consumer.PROPULSION), "the full retained union does not block a fill");
            harness.settle();
            assertEquals(4_096, harness.store().durable().receipts().size());
            for (UUID owner : List.of(OWNER, id(5_000))) {
                assertEquals(EscrowAdmissionAuthority.Retention.SERVER_FULL, harness.authority.checkRetain(id(999_999), owner),
                        "the server limit answers first, as SERVER_FULL does before ROCKET_FULL");
            }
        }
    }

    @Test void pendingReceiptsShareSixtyFourPerServerAndEightPerRocketAndARefusalChargesNothing() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            UUID rocket = id(77);
            for (long n = 1; n <= 8; n++) { assertQueued(harness.put(intent(n, rocket))); }
            var refused = harness.put(intent(9, rocket));
            assertEquals(BayReceiptStore.Status.RESERVE_REFUSED, refused.status());
            assertEquals(EscrowAdmissionAuthority.Result.ROCKET_FULL, refused.reservation());
            assertEquals(8, harness.authority.retainedCount(), "the checked retained slot was never charged");
            for (long n = 9; n <= 56; n++) { assertQueued(harness.put(intent(n, id(100 + n / 8)))); }
            harness.fluid.add(new EscrowAdmissionAuthority.Pending(id(900_001), OWNER, id(500), EscrowAdmissionAuthority.Consumer.PROPULSION));
            for (long n = 57; n <= 63; n++) { assertQueued(harness.put(intent(n, id(200)))); }
            assertEquals(64, harness.authority.pendingCount());
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, harness.put(intent(64, id(300))).reservation());
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, harness.authority.checkReserve(id(900_002), OWNER, id(400),
                    EscrowAdmissionAuthority.Consumer.PROPULSION));
            assertEquals(63, harness.store().receipts().size()); assertEquals(63, harness.authority.retainedCount());
            harness.settle(); assertEquals(64, harness.authority.pendingCount());
        }
    }

    @Test void oneOperationInDurableCapturedUnknownAndSuccessorStatesCostsOneSlotEach() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var x = intent(1, id(30)); assertQueued(harness.put(x)); harness.settle();
            harness.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertQueued(harness.put(x.withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 5)));
            harness.until(harness.store().writer()::unknown);
            var successor = x.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 6); assertQueued(harness.put(successor));
            assertTrue(harness.store().writer().hasSuccessor());
            assertEquals(1, harness.authority.pendingCount()); assertEquals(1, harness.authority.retainedCount());
            assertEquals(EscrowAdmissionAuthority.Result.ALREADY_HELD, harness.reserve(x));
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED, BayReceiptWriter.Outcome.UNKNOWN), harness.outcomes);
            harness.committer.set(CheckedSavedDataFile::atomicMove); harness.settle();
            assertEquals(List.of(successor), harness.store().durable().receipts());
            assertEquals(1, harness.authority.pendingCount(), "a REPAIR receipt keeps its slot");
        }
    }

    @Test void aDefiniteFailureCancelsANeverAppliedReceiptWhileAnUnknownOutcomeKeepsEveryReservation() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            Files.createDirectories(harness.file); // Refused before staging: FAILED_BEFORE_REPLACE.
            var x = intent(1, id(30)); assertQueued(harness.put(x));
            assertEquals(1, harness.authority.pendingCount()); assertTrue(harness.store().blocks(x.satellite()));
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE), harness.outcomes);
            assertTrue(harness.store().receipts().isEmpty());
            assertEquals(0, harness.authority.pendingCount()); assertEquals(0, harness.authority.retainedCount());
            assertFalse(harness.store().blocks(x.satellite()));
            Files.delete(harness.file); harness.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertQueued(harness.put(x)); harness.until(harness.store().writer()::unknown);
            assertEquals(1, harness.authority.pendingCount()); assertEquals(1, harness.authority.retainedCount());
            harness.committer.set(CheckedSavedDataFile::atomicMove); harness.settle();
            assertEquals(List.of(x), harness.store().durable().receipts());
            assertEquals(BayReceiptWriter.Outcome.COMMITTED, harness.outcomes.get(harness.outcomes.size() - 1));
        }
    }

    @Test void releasedReceiptsKeepTheirFlagNeverReArmAndCannotBeRemovedOnline() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var held = intent(1, id(30)).withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 2).withReleased();
            assertQueued(harness.put(held)); harness.settle();
            var reArm = held.withPhase(BayReceipt.Phase.INTENT, "NONE", 3);
            assertEquals(BayReceiptStore.Status.RELEASED_LOCKED, harness.put(reArm).status());
            var unreleased = new BayReceipt(held.op(), held.seq(), held.rocket(), held.bay(), held.satellite(), held.owner(),
                    held.definition(), held.kind(), held.orbit(), held.packageSig(), held.transfer(), held.srcBefore(), held.srcAfter(),
                    held.dstBefore(), held.dstAfter(), held.phase(), held.reason(), held.preexisting(), false, held.registryEpoch(),
                    held.createdTick(), held.updatedTick(), held.attempts());
            assertEquals(BayReceiptStore.Status.RELEASED_LOCKED, harness.put(unreleased).status());
            assertEquals(BayReceiptStore.Status.RELEASED_LOCKED, harness.remove(held).status());
            assertEquals(List.of(held), harness.store().receipts());
            assertEquals(1, harness.authority.pendingCount(), "a released receipt still counts");
            var intent = intent(2, id(31)).withReleased(); assertQueued(harness.put(intent)); harness.settle();
            assertQueued(harness.put(intent.withPhase(BayReceipt.Phase.INTENT, "NONE", 9)));
        }
    }

    @Test void aRejectedReceiptReArmsWithItsOwnOperationOnlyWhenAPendingSlotIsFree() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var x = intent(1, id(30)); assertQueued(harness.put(x)); harness.settle();
            var refused = x.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 4); assertQueued(harness.put(refused));
            assertEquals(1, harness.authority.pendingCount(), "the pending slot ends only at the exact refusal acknowledgement");
            harness.settle();
            assertEquals(0, harness.authority.pendingCount()); assertEquals(1, harness.authority.retainedCount());
            for (int i = 0; i < 64; i++) {
                harness.fluid.add(new EscrowAdmissionAuthority.Pending(id(10_000 + i), OWNER, id(20_000 + i / 8), EscrowAdmissionAuthority.Consumer.PROPULSION));
            }
            var reArm = reKeyed(refused, id(31), new RocketPosition(4, 4, 4)).withPhase(BayReceipt.Phase.INTENT, "NONE", 5);
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, harness.put(reArm).reservation());
            assertEquals(List.of(refused), harness.store().receipts(), "full refuses without changing op, key or package");
            var replacement = new BayReceipt(id(99), 99, id(31), new RocketPosition(4, 4, 4), refused.satellite(), OWNER, refused.definition(),
                    refused.kind(), refused.orbit(), refused.packageSig(), refused.transfer(), refused.srcBefore(), null, refused.dstBefore(),
                    null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, 1, 1, 0);
            assertEquals(BayReceiptStore.Status.INVALID, harness.put(replacement).status(), "never a replacement op for the same satellite");
            harness.fluid.remove(0);
            assertQueued(harness.put(reArm)); harness.settle();
            assertEquals(List.of(reArm), harness.store().durable().receipts());
            assertEquals(64, harness.authority.pendingCount()); assertEquals(1, harness.authority.pendingFor(id(31)));
        }
    }

    @Test void blockingReceiptsBlockInsertionFromEveryStateAndARejectedReceiptNeverBlocks() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var x = intent(1, id(30));
            assertFalse(harness.receipts.blocksInsertion(x.satellite()));
            assertQueued(harness.put(x)); assertTrue(harness.receipts.blocksInsertion(x.satellite()), "queued");
            harness.settle(); assertTrue(harness.receipts.blocksInsertion(x.satellite()), "durable");
            harness.committer.set(BayReceiptHarness.AMBIGUOUS);
            assertQueued(harness.put(x.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 3)));
            harness.until(harness.store().writer()::unknown);
            assertTrue(harness.receipts.blocksInsertion(x.satellite()), "the durable INTENT still blocks while REJECTED is unknown");
            harness.committer.set(CheckedSavedDataFile::atomicMove); harness.settle();
            assertFalse(harness.receipts.blocksInsertion(x.satellite()), "REJECTED never blocks");
            for (var phase : List.of(BayReceipt.Phase.REGISTERED, BayReceipt.Phase.REPAIR)) {
                var other = receipt(phase.ordinal() + 10, OWNER, id(40 + phase.ordinal()), phase);
                assertQueued(harness.put(other)); assertTrue(harness.receipts.blocksInsertion(other.satellite()), phase::name);
            }
            assertFalse(harness.receipts.blocksInsertion(id(123_456)), "another satellite is not blocked");
        }
    }

    @Test void notLoadedFailsClosedForBothConsumersAndInsertionUntilTheStartupLookup() throws Exception {
        for (var presence : CheckedSavedDataFile.Presence.values()) {
            var authority = new EscrowAdmissionAuthority();
            authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true, List.of()));
            var receipts = new BayReceipts(authority.register(EscrowAdmissionAuthority.Consumer.BAY), CheckedSavedDataFile::atomicMove);
            for (var consumer : EscrowAdmissionAuthority.Consumer.values()) {
                assertEquals(EscrowAdmissionAuthority.Result.UNAVAILABLE, authority.checkReserve(id(1), OWNER, id(2), consumer));
            }
            assertEquals(EscrowAdmissionAuthority.Retention.UNAVAILABLE, authority.checkRetain(id(1), OWNER));
            assertTrue(receipts.blocksInsertion(id(3)), "NOT_LOADED blocks every package");
            receipts.start(null, presence, temp.resolve(presence.name()), 0);
            boolean absent = presence == CheckedSavedDataFile.Presence.ABSENT;
            assertEquals(absent ? EscrowAdmissionAuthority.Delegate.State.NO_FILE : EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, receipts.state());
            assertEquals(absent ? EscrowAdmissionAuthority.Result.RESERVED : EscrowAdmissionAuthority.Result.UNAVAILABLE,
                    authority.checkReserve(id(1), OWNER, id(2), EscrowAdmissionAuthority.Consumer.PROPULSION));
            assertEquals(absent ? EscrowAdmissionAuthority.Retention.RESERVED : EscrowAdmissionAuthority.Retention.UNAVAILABLE,
                    authority.checkRetain(id(1), OWNER));
            assertEquals(!absent, receipts.blocksInsertion(id(3)), "a file that exists but did not load keeps NOT_LOADED");
            if (!absent) { assertThrows(IllegalArgumentException.class, () -> receipts.createForAdmission(temp.resolve("x.dat"), 0)); }
        }
    }
}

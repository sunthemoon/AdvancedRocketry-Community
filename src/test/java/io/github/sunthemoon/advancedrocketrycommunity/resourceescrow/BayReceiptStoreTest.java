package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
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
            var repair = x.withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 5); assertQueued(harness.put(repair));
            harness.until(harness.store().writer()::unknown);
            assertEquals(BayReceiptStore.Status.BUSY, harness.put(x.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 6)).status(),
                    "one change of an op in flight at a time (R37 item 2)");
            // Another op's change makes the successor, whose RAM capture holds X's copy as well.
            var other = rejected(2, OWNER); assertQueued(harness.put(other));
            assertTrue(harness.store().writer().hasSuccessor());
            assertEquals(1, harness.authority.pendingCount(), "X's durable, captured, UNKNOWN and successor copies cost one pending slot");
            assertEquals(2, harness.authority.retainedCount(), "one retained slot each for X and the REJECTED receipt");
            assertEquals(EscrowAdmissionAuthority.Result.ALREADY_HELD, harness.reserve(x));
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED, BayReceiptWriter.Outcome.UNKNOWN), harness.outcomes);
            harness.committer.set(CheckedSavedDataFile::atomicMove); harness.settle();
            assertEquals(List.of(repair, other), harness.store().durable().receipts());
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

    private static Path stagingBlocked(BayReceiptHarness harness) throws Exception {
        // Staging is refused before any replacement: every write fails definitely until this directory is removed.
        return Files.createDirectory(harness.file.resolveSibling(harness.file.getFileName() + ".arce-pending"));
    }

    /** W27C1-R1 under R37 item 1, the review's probe 1 inverted: a repaired storage recovers the store without a new change. */
    @Test void afterThreeDefiniteFailuresAGenerationWithNoNewChangeRecoversTheStoreOnceTheStorageIsRepaired() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var a = intent(1, id(30)); assertQueued(harness.put(a)); harness.settle();
            Path pending = stagingBlocked(harness);
            for (long n = 2; n <= 4; n++) { assertQueued(harness.put(rejected(n, OWNER))); harness.settle(); }
            var failed = BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE;
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED, failed, failed, failed), harness.outcomes);
            assertTrue(harness.store().unavailable(), "three definite failures make the store unavailable");
            assertEquals(BayReceiptStore.Status.STORE_UNAVAILABLE, harness.put(rejected(5, OWNER)).status());
            long generation = harness.store().writer().durableGeneration();
            // While the storage stays broken, a generation with no new change is retried at most once per 200 ticks.
            var starts = new TreeSet<Long>(); int[] ticks = {0};
            harness.until(() -> { starts.add(harness.store().writer().lastIoStart()); return ++ticks[0] > 700; });
            var gaps = new ArrayList<Long>(); Long previous = null;
            for (long start : starts) { if (previous != null) { gaps.add(start - previous); } previous = start; }
            assertTrue(gaps.size() == 3 && gaps.stream().allMatch(gap -> gap >= BayReceiptWriter.RETRY_TICKS), gaps::toString);
            assertTrue(harness.store().unavailable()); assertEquals(List.of(a), harness.store().receipts());
            assertEquals(4, harness.outcomes.size(), "a generation with no new change answers no caller");
            Files.delete(pending); // The storage is repaired.
            harness.until(() -> !harness.store().unavailable());
            assertFalse(harness.store().unavailable(), "available again after an exact commit");
            assertTrue(harness.store().writer().idle() && harness.store().writer().durableGeneration() > generation);
            assertEquals(List.of(a), harness.store().durable().receipts(), "no new change was written");
            assertEquals(List.of(a), BayReceiptCodec.decode(CheckedSavedDataFile.readPayload(harness.file,
                    ManagedSavedDataType.BAY_RECEIPTS).orElseThrow()).receipts());
            assertQueued(harness.put(rejected(5, OWNER))); harness.settle();
            assertEquals(BayReceiptWriter.Outcome.COMMITTED, harness.outcomes.get(harness.outcomes.size() - 1));
        }
    }

    /** W27C1-R2, the review's probe 2 with R37 item 2's expectation: a second change of one satellite waits. */
    @Test void aSatelliteWithARemovalInFlightRefusesAnotherOperationAndTheFailedRemovalRestoresExactly() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var a = receipt(1, OWNER, id(40), BayReceipt.Phase.REJECTED);
            assertQueued(harness.put(a)); harness.settle();
            Path pending = stagingBlocked(harness);
            assertQueued(harness.remove(a));
            var b = new BayReceipt(id(2), 2, id(41), new RocketPosition(9, 9, 9), a.satellite(), OWNER, a.definition(), a.kind(),
                    a.orbit(), a.packageSig(), id(400_002), a.srcBefore(), null, a.dstBefore(), null, BayReceipt.Phase.INTENT, "NONE",
                    false, false, 0, 1, 1, 0);
            assertEquals(BayReceiptStore.Admission.of(BayReceiptStore.Status.BUSY), harness.put(b), "the satellite has a change in flight");
            assertFalse(harness.store().writer().hasSuccessor(), "the refusal queued nothing");
            assertEquals(0, harness.authority.pendingCount(), "and charged nothing");
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED, BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE), harness.outcomes);
            assertEquals(List.of(a), harness.store().receipts(), "the failed removal restores A, and only A");
            assertEquals(List.of(a), harness.store().durable().receipts());
            Files.delete(pending);
            assertEquals(BayReceiptStore.Status.INVALID, harness.put(b).status(), "A still holds the satellite");
            assertQueued(harness.remove(a)); harness.settle();
            assertQueued(harness.put(b)); harness.settle();
            assertEquals(List.of(b), harness.store().durable().receipts());
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED, BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE,
                    BayReceiptWriter.Outcome.COMMITTED, BayReceiptWriter.Outcome.COMMITTED), harness.outcomes);
        }
    }

    /** W27C1-R2, the review's probe 3 with R37 item 2's expectation: a definitely failed change never becomes durable. */
    @Test void aSecondChangeOfOneOperationWaitsSoADefinitelyFailedChangeNeverBecomesDurable() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var a = intent(1, id(50));
            assertQueued(harness.put(a)); harness.settle();
            Path pending = stagingBlocked(harness);
            var first = a.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 2);
            assertQueued(harness.put(first));
            var second = a.withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 3);
            assertEquals(BayReceiptStore.Admission.of(BayReceiptStore.Status.BUSY), harness.put(second), "the op has a change in flight");
            assertFalse(harness.store().writer().hasSuccessor());
            harness.settle();
            assertEquals(List.of(BayReceiptWriter.Outcome.COMMITTED, BayReceiptWriter.Outcome.FAILED_BEFORE_REPLACE), harness.outcomes);
            assertEquals(List.of(a), harness.store().receipts(), "the failed change is cancelled exactly");
            Files.delete(pending);
            assertQueued(harness.put(receipt(2, OWNER, id(51), BayReceipt.Phase.REJECTED))); // Unrelated work.
            harness.settle();
            assertEquals(a, harness.store().durable().receipts().get(0), "the FAILED change never became durable");
            assertQueued(harness.put(second)); harness.settle();
            assertEquals(second, harness.store().durable().receipts().get(0), "the retried change is acknowledged");
        }
    }

    /** W27C1-R2: a key is unique against every state, so a cancelled re-key can return to its old key. */
    @Test void aKeyHeldInTheDurableOrCapturedStateRefusesAnotherOperationWhileTheReKeyIsInFlight() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            var x = rejected(1, OWNER);
            assertQueued(harness.put(x)); harness.settle();
            Path pending = stagingBlocked(harness);
            assertQueued(harness.put(reKeyed(x, id(31), new RocketPosition(4, 4, 4))));
            var y = reKeyed(rejected(2, OWNER), x.rocket(), x.bay());
            assertEquals(BayReceiptStore.Status.INVALID, harness.put(y).status(), "the durable and captured states still hold X's key");
            harness.settle();
            assertEquals(List.of(x), harness.store().receipts(), "the failed re-key returns X to its key");
            Files.delete(pending);
            assertEquals(BayReceiptStore.Status.INVALID, harness.put(y).status());
            assertQueued(harness.put(rejected(2, OWNER))); harness.settle();
            assertEquals(List.of(x, rejected(2, OWNER)), harness.store().durable().receipts());
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodecTest.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RocketRejoinCommitTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_REJOINS;
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    Path file() { return temp.resolve(TYPE.fileName()); }
    static RocketRejoinRoot p1() {
        var e = fixture().entries().get(0);
        return fixture().update(new Entry(e.release(), e.player(), 0, Placement.PENDING, Seat.VERIFIED,
                new Attempt(id(900), Target.PAD, 2.5, 64, 3.5)));
    }
    static RocketRejoinRoot p2() { return new RocketRejoinRoot(1, List.of(), List.of()); }
    static void await(RocketRejoinSavedData data, long tick) throws Exception {
        long before = data.completions(), until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (data.completions() == before && System.nanoTime() < until) { data.tick(tick); Thread.sleep(2); }
        assertTrue(data.completions() > before, "No completion within 10 seconds");
    }
    static void gate(CountDownLatch entered, CountDownLatch release) {
        entered.countDown();
        try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("Gate timed out"); } }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    @Test void heldNeverInstalledP2CanBeReplacedWithoutBlockingLaterObligations() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var lease = new RocketRejoinLease();
        try (var data = RocketRejoinSavedData.open(file(), CheckedSavedDataPolicy.atomic(TYPE), CheckedSavedDataFile::presence, p -> { },
                root -> { if (root.equals(p2())) { gate(entered, release); } })) {
            assertTrue(data.submit(p1())); await(data, 1); byte[] before = Files.readAllBytes(file());
            assertTrue(data.submitPlacement(p2(), lease));
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                assertEquals(p1(), data.root(), "Uninstalled P2 is not owner authority");
                assertFalse(data.submit(p2()), "No successor may depend on revocable P2");
                lease.revoke();
            } finally { release.countDown(); }
            await(data, 2);
            assertTrue(data.pendingRoots().isEmpty()); assertArrayEquals(before, Files.readAllBytes(file()));
            assertEquals(p1(), data.acknowledgedRoot()); assertEquals(p1(), data.root());
            var e = p1().entries().get(0);
            var fresh = p1().update(new Entry(e.release(), e.player(), 0, Placement.PENDING, Seat.VERIFIED,
                    new Attempt(id(901), Target.PAD, 4.5, 64, 5.5)));
            assertTrue(data.submit(fresh)); await(data, 3);
            assertEquals(fresh, data.acknowledgedRoot()); assertTrue(data.acknowledgedCurrent());
            assertTrue(data.submitPlacement(p2(), new RocketRejoinLease())); await(data, 4);
            assertEquals(p2(), data.root()); assertTrue(data.acknowledgedCurrent());
        } finally { release.countDown(); }
    }

    @Test void generationsIncreaseAcrossDiscardAndInstalledRootsNeverRegress() throws Exception {
        var results = new ArrayList<RocketRejoinWriter.Completion>();
        var writer = new RocketRejoinWriter(file(), CheckedSavedDataPolicy.atomic(TYPE), false, CheckedSavedDataFile::presence, p -> { });
        var lease = new RocketRejoinLease(); lease.revoke();
        try {
            assertTrue(writer.request(1, p1(), () -> true)); poll(writer, results, 1);
            assertTrue(writer.request(2, p2(), lease, true)); poll(writer, results, 2);
            assertTrue(results.get(1).discarded()); assertEquals(1, results.get(1).acknowledged().sequence());
            assertThrows(IllegalArgumentException.class, () -> writer.request(2, p1(), () -> true));
            assertTrue(writer.request(3, p1(), () -> true)); poll(writer, results, 3);
            assertEquals(List.of(1L, 2L, 3L), results.stream().map(c -> c.capture().generation()).toList());
            assertEquals(3, results.get(2).acknowledged().sequence());
        } finally { writer.close(results::add); }
    }
    static void poll(RocketRejoinWriter writer, List<RocketRejoinWriter.Completion> results, int count) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (results.size() < count && System.nanoTime() < until) { writer.tick(count, results::add); Thread.sleep(2); }
        assertEquals(count, results.size());
    }

    @Test void invalidationAfterLeaseCheckIsAfterCommitPoint() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var lease = new RocketRejoinLease();
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            if (RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow()).equals(p2())) { gate(entered, release); }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        try (var data = RocketRejoinSavedData.open(file(), policy, CheckedSavedDataFile::presence, p -> { })) {
            assertTrue(data.submit(p1())); await(data, 1);
            assertTrue(data.submitPlacement(p2(), lease));
            try { assertTrue(entered.await(10, TimeUnit.SECONDS)); lease.revoke(); }
            finally { release.countDown(); }
            await(data, 2); assertEquals(p2(), data.acknowledgedRoot()); assertEquals(Outcome.COMMITTED, data.outcome());
        } finally { release.countDown(); }
    }

    @Test void unknownInstallationCannotBeDiscardedAsNeverInstalled() throws Exception {
        var fail = new AtomicBoolean(); var lease = new RocketRejoinLease();
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target);
            if (fail.get()) { throw new java.io.IOException("after move"); }
        }, (path, type) -> { if (fail.get()) { throw new IllegalStateException("unreadable"); } return CheckedSavedDataFile.readPayload(path, type); });
        try (var data = RocketRejoinSavedData.open(file(), policy, CheckedSavedDataFile::presence, p -> { })) {
            assertTrue(data.submit(p1())); await(data, 1); fail.set(true);
            assertTrue(data.submitPlacement(p2(), lease)); await(data, 2); lease.revoke();
            fail.set(false); await(data, 202);
            assertEquals(Outcome.UNKNOWN, data.outcome()); assertEquals(List.of(p2()), data.pendingRoots());
            assertFalse(data.submit(p1()));
            assertEquals(p2(), RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow()));
        }
    }
}

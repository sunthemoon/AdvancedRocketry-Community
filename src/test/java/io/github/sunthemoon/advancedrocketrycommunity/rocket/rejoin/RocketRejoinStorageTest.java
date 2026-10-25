package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodecTest.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketRejoinStorageTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_REJOINS;
    private Path file() { return temp.resolve(TYPE.fileName()); }
    private RocketRejoinSavedData open(CheckedSavedDataPolicy policy) {
        return RocketRejoinSavedData.open(file(), policy, CheckedSavedDataFile::presence, p -> { });
    }
    private static void await(RocketRejoinSavedData data, long tick) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        long before = data.completions();
        while (data.completions() == before && System.nanoTime() < until) { data.tick(tick); Thread.sleep(2); }
        assertTrue(data.completions() > before, "Writer did not publish within 10 seconds");
    }

    @Test void openingInspectingEncodingAutosavingAndClosingWithoutUseCreateNoFile() {
        try (var data = open(CheckedSavedDataPolicy.atomic(TYPE))) {
            assertEquals(RocketRejoinRoot.empty(), data.root());
            assertFalse(data.isDirty()); assertFalse(data.acknowledgedCurrent());
            assertFalse(data.submit(RocketRejoinRoot.empty()));
            data.save(new CompoundTag()); data.save(file().toFile()); data.tick(1);
        }
        assertFalse(Files.exists(file()));
    }

    @Test void seatCompletionRequiresLiveProofAtReplacementAndRetriesOnlyAfterTwoHundredTicks() throws Exception {
        var valid = new AtomicBoolean(true); var moves = new AtomicInteger();
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            moves.incrementAndGet(); CheckedSavedDataFile.atomicMove(staged, target);
        });
        try (var data = open(policy)) {
            assertTrue(data.submit(fixture())); await(data, 1); byte[] before = Files.readAllBytes(file());
            var entry = fixture().entries().get(0);
            var s2 = fixture().update(new RocketRejoinRoot.Entry(entry.release(), entry.player(), entry.seatIndex(),
                    entry.placement(), RocketRejoinRoot.Seat.VERIFIED, null));
            valid.set(false); assertTrue(data.submit(s2, valid::get)); await(data, 2);
            assertFalse(data.acknowledgedCurrent()); assertArrayEquals(before, Files.readAllBytes(file()));
            assertEquals(fixture(), data.acknowledgedRoot()); assertEquals(s2, data.root());
            assertEquals(1, moves.get()); valid.set(true);
            data.tick(201); Thread.sleep(25); data.tick(201); assertEquals(1, moves.get());
            await(data, 202); assertTrue(data.acknowledgedCurrent()); assertEquals(2, moves.get());
            assertEquals(s2, RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow()));
        }
    }

    @Test void identicalDiskReadbackCannotBypassARejectedSeatProof() throws Exception {
        try (var data = open(CheckedSavedDataPolicy.atomic(TYPE))) {
            assertTrue(data.submit(fixture())); await(data, 1);
            assertTrue(data.submit(fixture(), () -> false)); await(data, 2);
            assertFalse(data.acknowledgedCurrent()); assertEquals(Outcome.UNKNOWN, data.outcome());
            assertEquals(fixture(), data.acknowledgedRoot());
        }
    }

    @Test void acknowledgementIsOwnerPublishedAndEmptyUsedSidecarIsKept() throws Exception {
        try (var data = open(CheckedSavedDataPolicy.atomic(TYPE))) {
            assertTrue(data.submit(fixture())); assertTrue(data.isDirty()); assertFalse(data.acknowledgedCurrent());
            await(data, 1); assertTrue(data.acknowledgedCurrent()); assertFalse(data.isDirty());
            var entry = fixture().entries().get(0);
            var retired = fixture().update(new RocketRejoinRoot.Entry(entry.release(), entry.player(), 0,
                    RocketRejoinRoot.Placement.VERIFIED, RocketRejoinRoot.Seat.VERIFIED, null));
            assertTrue(data.submit(retired)); await(data, 2);
            assertEquals(retired, RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow()));
        }
        assertTrue(Files.exists(file()));
        try (var reloaded = open(CheckedSavedDataPolicy.atomic(TYPE))) {
            assertTrue(reloaded.root().entries().isEmpty()); assertEquals(1, reloaded.root().nextSequence());
            assertFalse(reloaded.acknowledgedCurrent(), "Loading is not a session write acknowledgement");
        }
    }

    @Test void oneActiveOneSuccessorAndNoOffThreadPublication() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            entered.countDown(); try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IOException("Timed out gate"); } }
            catch (InterruptedException e) { throw new IOException(e); } CheckedSavedDataFile.atomicMove(staged, target);
        });
        try (var data = open(policy)) {
            assertTrue(data.submit(fixture())); assertTrue(entered.await(10, TimeUnit.SECONDS));
            var successor = fixture().admit(batch(2, 1, List.of(id(101))), List.of(entry(2, id(101))));
            assertTrue(data.submit(successor));
            assertEquals(List.of(fixture(), successor), data.pendingRoots());
            assertFalse(data.submit(successor.admit(batch(3, 2, List.of(id(102))), List.of(entry(3, id(102))))));
            assertEquals(successor, data.root()); assertFalse(data.acknowledgedCurrent());
            var rejected = new AtomicBoolean();
            var foreign = new Thread(() -> { try { data.tick(1); } catch (IllegalStateException expected) { rejected.set(true); } });
            foreign.start(); foreign.join(); assertTrue(rejected.get());
            release.countDown(); await(data, 1); assertFalse(data.acknowledgedCurrent());
            await(data, 2); assertTrue(data.acknowledgedCurrent());
        } finally { release.countDown(); }
    }

    @Test void firstWriteRaceNeverReplacesAnAppearedFile() throws Exception {
        try (var data = open(CheckedSavedDataPolicy.atomic(TYPE))) {
            byte[] foreign = {7, 8, 9}; Files.write(file(), foreign);
            assertTrue(data.submit(fixture())); await(data, 1);
            assertFalse(data.acknowledgedCurrent()); assertTrue(data.isDirty());
            assertFalse(data.submit(fixture())); assertArrayEquals(foreign, Files.readAllBytes(file()));
        }
    }

    @Test void failureAfterMoveUsesExactReadbackButUnknownRetainsObligationsAndRetries() throws Exception {
        var failRead = new AtomicBoolean(true); var failMove = new AtomicBoolean(true);
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target);
            if (failMove.get()) { throw new IOException("after move"); }
        }, (path, type) -> { if (failRead.get()) { throw new IllegalStateException("unreadable"); }
            return CheckedSavedDataFile.readPayload(path, type); });
        try (var data = open(policy)) {
            assertTrue(data.submit(fixture())); await(data, 1);
            assertEquals(Outcome.UNKNOWN, data.outcome()); assertFalse(data.acknowledgedCurrent());
            assertEquals(fixture(), data.root()); assertTrue(data.isDirty());
            failRead.set(false); // The first-write retry may claim only this exact candidate, not an arbitrary appeared file.
            await(data, 201); assertEquals(Outcome.COMMITTED, data.outcome()); assertTrue(data.acknowledgedCurrent());
            assertFalse(data.isDirty());
        }
    }

    @Test void failureBeforeMoveAndDirectoryForceFailureNeverAcknowledge() throws Exception {
        try (var data = open(new CheckedSavedDataPolicy(TYPE, (staged, target) -> { throw new IOException("before move"); }))) {
            assertTrue(data.submit(fixture())); await(data, 1);
            assertEquals(Outcome.NOT_COMMITTED, data.outcome()); assertFalse(Files.exists(file()));
            assertTrue(data.isDirty()); assertEquals(fixture(), data.root());
        }
        try (var data = RocketRejoinSavedData.open(file(), CheckedSavedDataPolicy.atomic(TYPE),
                CheckedSavedDataFile::presence, p -> { throw new IOException("force"); })) {
            assertTrue(data.submit(fixture())); await(data, 1);
            assertEquals(Outcome.UNKNOWN, data.outcome()); assertFalse(data.acknowledgedCurrent()); assertTrue(data.isDirty());
        }
    }

    @Test void unknownPresenceAtOpenOrFirstWriteNeverCreatesAFile() throws Exception {
        assertThrows(IllegalStateException.class, () -> RocketRejoinSavedData.open(file(), CheckedSavedDataPolicy.atomic(TYPE),
                p -> CheckedSavedDataFile.Presence.UNKNOWN, p -> { }));
        var unknown = new AtomicBoolean();
        try (var data = RocketRejoinSavedData.open(file(), CheckedSavedDataPolicy.atomic(TYPE),
                p -> unknown.get() ? CheckedSavedDataFile.Presence.UNKNOWN : CheckedSavedDataFile.presence(p), p -> { })) {
            unknown.set(true); assertTrue(data.submit(fixture())); await(data, 1);
            assertFalse(data.acknowledgedCurrent()); assertTrue(data.isDirty()); assertFalse(Files.exists(file()));
        }
    }

    @Test void unknownAfterAnAcknowledgedRootKeepsBothNamedGenerations() throws Exception {
        var fail = new AtomicBoolean();
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            CheckedSavedDataFile.atomicMove(staged, target); if (fail.get()) { throw new IOException("after move"); }
        }, (path, type) -> { if (fail.get()) { throw new IllegalStateException("unreadable"); }
            return CheckedSavedDataFile.readPayload(path, type); });
        try (var data = open(policy)) {
            assertTrue(data.submit(fixture())); await(data, 1); fail.set(true);
            var successor = fixture().admit(batch(2, 1, List.of(id(101))), List.of(entry(2, id(101))));
            assertTrue(data.submit(successor)); await(data, 2);
            assertEquals(Outcome.UNKNOWN, data.outcome()); assertFalse(data.acknowledgedCurrent());
            assertEquals(fixture(), data.acknowledgedRoot()); assertEquals(List.of(successor), data.pendingRoots());
            fail.set(false); await(data, 202); assertTrue(data.acknowledgedCurrent());
        }
    }

    @Test void identicalAppearedFileDoesNotProveFirstWriteOwnership() throws Exception {
        try (var data = open(CheckedSavedDataPolicy.atomic(TYPE))) {
            CheckedSavedDataFile.replace(file(), TYPE, RocketRejoinCodecTest::encoded);
            byte[] foreign = Files.readAllBytes(file());
            assertTrue(data.submit(fixture())); await(data, 1);
            assertFalse(data.acknowledgedCurrent(), "Readback cannot bypass a rejected first-write ownership check");
            assertEquals(Outcome.UNKNOWN, data.outcome()); assertTrue(data.isDirty());
            assertArrayEquals(foreign, Files.readAllBytes(file()));
        }
    }

    @Test void knownNonCommitDoesNotAuthorizeALaterIdenticalAppearedFile() throws Exception {
        var moves = new AtomicInteger();
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            moves.incrementAndGet(); throw new IOException("before move");
        });
        try (var data = open(policy)) {
            assertTrue(data.submit(fixture())); await(data, 1);
            assertEquals(Outcome.NOT_COMMITTED, data.outcome()); assertEquals(1, moves.get());
            CheckedSavedDataFile.replace(file(), TYPE, RocketRejoinCodecTest::encoded);
            await(data, 201);
            assertEquals(1, moves.get(), "Known non-commit cannot leave an ownership claim for the next attempt");
            assertFalse(data.acknowledgedCurrent()); assertEquals(Outcome.UNKNOWN, data.outcome());
        }
    }

    @Test void loadedRootRemainsNamedDuringAnUnknownFirstReplacement() throws Exception {
        CheckedSavedDataFile.replace(file(), TYPE, RocketRejoinCodecTest::encoded);
        var fail = new AtomicBoolean(true);
        try (var data = RocketRejoinSavedData.open(file(), CheckedSavedDataPolicy.atomic(TYPE),
                CheckedSavedDataFile::presence, p -> { if (fail.get()) { throw new IOException("force"); } })) {
            assertFalse(data.acknowledgedCurrent());
            var retired = new RocketRejoinRoot(1, List.of(), List.of());
            assertTrue(data.submit(retired)); await(data, 1);
            assertEquals(Outcome.UNKNOWN, data.outcome()); assertEquals(retired, data.root());
            assertEquals(fixture(), data.acknowledgedRoot(), "Startup obligations must survive an uncertain replacement");
            assertEquals(List.of(retired), data.pendingRoots());
            fail.set(false); await(data, 201); assertTrue(data.acknowledgedCurrent());
        }
    }

    @Test void closeResetsTheExistingRetryCountAfterEachCommittedGeneration() throws Exception {
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var moves = new AtomicInteger();
        var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            entered.countDown();
            try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IOException("Timed out gate"); } }
            catch (InterruptedException e) { throw new IOException(e); }
            if (moves.incrementAndGet() % 3 != 0) { throw new IOException("before move"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        try (var data = open(policy)) {
            assertTrue(data.submit(fixture())); assertTrue(entered.await(10, TimeUnit.SECONDS));
            var successor = fixture().admit(batch(2, 1, List.of(id(101))), List.of(entry(2, id(101))));
            assertTrue(data.submit(successor)); release.countDown(); data.close();
            assertTrue(data.acknowledgedCurrent()); assertFalse(data.isDirty()); assertEquals(6, moves.get());
            assertEquals(successor, RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow()));
        } finally { release.countDown(); }
    }
}

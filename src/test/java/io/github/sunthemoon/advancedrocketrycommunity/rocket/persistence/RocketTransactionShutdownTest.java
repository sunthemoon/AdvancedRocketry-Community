package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 items 9 and 10 and design section 6.2 for the transaction journal root: running saves only request; the
 * stop-path drain covers a final target after logout mutations within one shared three-attempt budget; close and abort
 * join before world ownership is released. A real but unstarted dedicated server supplies the tick and both flags.
 */
@Timeout(60)
class RocketTransactionShutdownTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aRunningSaveOnlyRequestsAndTheStoppingSaveDrainsToTheFinalTarget() {
        var server = live(); var data = journal(server);
        insert(data, 1);
        data.save(target().toFile());
        assertTrue(data.writerBusy(), "Autosave made no request");
        assertFalse(data.acknowledgedCurrent(), "Autosave waited for the write");
        pump(server, data, () -> !data.writerBusy());
        assertTrue(data.acknowledgedCurrent());
        flags(server, false, false);
        data.latchShutdown();
        insert(data, 2);
        assertFalse(data.requestWrite(target(), ATOMIC), "A gameplay request was admitted during shutdown");
        data.pollWriter();
        assertFalse(data.writerBusy(), "A tick start began a write during logout");
        data.save(target().toFile());
        assertTrue(data.acknowledgedCurrent()); assertFalse(data.isDirty());
        assertEquals(data.save(new CompoundTag()), read());
        assertTrue(data.drainReport().orElseThrow().success());
        data.close(); assertFalse(data.requestWrite(target(), ATOMIC));
    }

    @Test void bothShutdownFlagCombinationsRefuseRequestsAndStartsAndTheSaveObservesTheFlag() {
        for (boolean[] combination : List.of(new boolean[] {false, false}, new boolean[] {true, true}, new boolean[] {false, true})) {
            var server = live(); var data = journal(server);
            insert(data, 1); data.save(target().toFile()); pump(server, data, () -> !data.writerBusy());
            flags(server, combination[0], combination[1]);
            insert(data, 2);
            assertFalse(data.requestWrite(target(), ATOMIC), "running=" + combination[0] + " stopped=" + combination[1]);
            data.pollWriter(); assertFalse(data.writerBusy());
            assertEquals(RocketTransactionSavedData.AbsenceStatus.WRITE_REQUIRED, data.absenceStatus(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
            // No ServerStoppingEvent on the exception path: the save's own observation latches the shutdown.
            data.save(target().toFile());
            assertTrue(data.acknowledgedCurrent(), "The stop save did not cover the final RAM");
            assertEquals(data.save(new CompoundTag()), read());
            data.close();
        }
    }

    @Test void aStaleCaptureBeforeLogoutMutationsKeepsTheJournalDirtyUntilAFreshFinalCapture() throws Exception {
        for (var older : List.of(COMMITTED, UNKNOWN, NOT_COMMITTED)) {
            Files.deleteIfExists(target()); // each older outcome is classified against this journal's own history
            var server = live(); var data = journal(server);
            insert(data, 1);
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
            var policy = new CheckedSavedDataPolicy(TYPE, (staged, file) -> {
                entered.countDown(); RocketTransactionProofTest.await(release);
                if (older == NOT_COMMITTED) { throw new IOException("W129a stale attempt failed before replacement"); }
                CheckedSavedDataFile.atomicMove(staged, file);
                if (older == UNKNOWN) { throw new IOException("W129a stale attempt ambiguous after replacement"); }
            }, older == UNKNOWN ? (file, type) -> { throw new IllegalStateException("W129a unreadable"); }
                    : CheckedSavedDataFile::readPayload);
            assertTrue(data.requestWrite(target(), policy)); assertTrue(entered.await(10, TimeUnit.SECONDS));
            long stale = data.mutationSequence();
            flags(server, false, false); data.latchShutdown();
            insert(data, 2); // a logout mutation after the stale capture
            release.countDown();
            data.save(target().toFile());
            var report = data.drainReport().orElseThrow();
            assertTrue(report.success(), older + ": " + report);
            assertEquals(2, report.attempts(), older + ": the in-flight attempt counts");
            assertEquals(data.mutationSequence(), report.target()); assertEquals(data.mutationSequence(), report.committed());
            assertTrue(stale < data.mutationSequence());
            assertTrue(data.acknowledgedCurrent()); assertEquals(COMMITTED, data.writeOutcome());
            assertEquals(2, read().getList("transactions", 10).size(), older + ": the final capture lacks the logout mutation");
            data.close();
        }
    }

    @Test void oneShutdownBudgetSpansSaveAndCloseAndExhaustionReportsWithoutClaimingSuccess() throws Exception {
        var server = live(); var data = journal(server);
        insert(data, 1); data.save(target().toFile()); pump(server, data, () -> !data.writerBusy());
        long committed = data.mutationSequence();
        byte[] before = Files.readAllBytes(target());
        Files.createDirectory(directory.resolve(TYPE.fileName() + ".arce-pending")); // every staging fails
        insert(data, 2);
        RocketTransactionWriterBenchmark.at(server, server.getTickCount() + CheckedRootWriter.CADENCE_TICKS);
        assertTrue(data.requestWrite(target(), ATOMIC));
        assertTrue(data.writerInFlight());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!data.writerCompletionReady()) { assertTrue(System.nanoTime() < deadline); Thread.sleep(1); }
        try (var log = new LogLines()) {
            flags(server, false, false); data.latchShutdown();
            data.save(target().toFile());
            var exhausted = data.drainReport().orElseThrow();
            assertFalse(exhausted.success()); assertEquals("BUDGET_EXHAUSTED", exhausted.reason());
            assertEquals(3, exhausted.attempts()); assertEquals(data.mutationSequence(), exhausted.target());
            assertEquals(committed, exhausted.committed());
            insert(data, 3); // a later persistence hook raises the target, not the budget
            data.close();
            var closed = data.drainReport().orElseThrow();
            assertFalse(closed.success()); assertEquals(3, closed.attempts()); assertEquals(data.mutationSequence(), closed.target());
            assertTrue(data.isDirty()); assertFalse(data.acknowledgedCurrent());
            assertArrayEquals(before, Files.readAllBytes(target()), "Exhaustion changed the last committed file");
            assertFalse(data.requestWrite(target(), ATOMIC), "A request after close was admitted");
            data.abort();
            assertEquals(List.of("WARN ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root=rocket_transactions target="
                    + exhausted.target() + " committed=" + committed + " attempts=3 reason=BUDGET_EXHAUSTED"), log.warnings());
        }
    }

    @Test void closeJoinsAStalledWriterBeforeReturningAndKeepsAnInterruption() throws Exception {
        var server = live(); var data = journal(server);
        insert(data, 1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new AtomicBoolean();
        assertTrue(data.requestWrite(target(), ATOMIC.withCommitter((staged, file) -> {
            entered.countDown(); awaitUninterruptibly(release); CheckedSavedDataFile.atomicMove(staged, file); finished.set(true);
        })));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        flags(server, false, false); data.latchShutdown();
        var returned = new AtomicBoolean(); var released = new AtomicBoolean();
        Thread owner = Thread.currentThread();
        Thread helper = new Thread(() -> {
            try {
                while (owner.getState() != Thread.State.TIMED_WAITING) { Thread.sleep(5); }
                Thread.sleep(100); owner.interrupt(); Thread.sleep(300);
                if (!returned.get()) { released.set(true); }
            } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
            finally { release.countDown(); }
        }, "W129a-close-release");
        helper.start();
        data.close(); returned.set(true);
        assertTrue(released.get(), "Close returned while IO could still replace the file");
        assertTrue(finished.get()); assertTrue(Thread.interrupted(), "The interruption was not preserved");
        helper.join();
        var report = data.drainReport().orElseThrow();
        assertFalse(report.success()); assertEquals("INTERRUPTED", report.reason());
        assertTrue(data.acknowledgement().isEmpty(), "An interrupted teardown published its completion");
        assertTrue(data.isDirty()); assertFalse(data.requestWrite(target(), ATOMIC));
    }

    @Test void abortPublishesNothingAndCloseEstablishesTheTargetWhenTheSaveWasSkipped() throws Exception {
        var server = live(); var data = journal(server);
        insert(data, 1); assertTrue(data.requestWrite(target(), ATOMIC));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!data.writerCompletionReady()) { assertTrue(System.nanoTime() < deadline); Thread.sleep(1); }
        data.abort();
        assertEquals(UNACKNOWLEDGED, data.writeOutcome()); assertTrue(data.acknowledgement().isEmpty());
        assertEquals("ABORTED_UNRESOLVED", data.drainReport().orElseThrow().reason());
        assertFalse(data.drainReport().orElseThrow().success());
        assertFalse(data.requestWrite(target(), ATOMIC));

        var stopping = live(); flags(stopping, false, true);
        var skipped = journal(stopping); insert(skipped, 3);
        assertFalse(skipped.requestWrite(target(), ATOMIC), "A request during shutdown only binds the target");
        assertTrue(skipped.drainReport().isEmpty());
        skipped.close(); // no shutdown save happened: close sets the target and drains it
        assertTrue(skipped.drainReport().orElseThrow().success());
        assertTrue(skipped.acknowledgedCurrent()); assertEquals(skipped.save(new CompoundTag()), read());
    }

    @Test void aCleanLoadedJournalIsNotRewrittenAtShutdown() throws Exception {
        var seed = new RocketTransactionSavedData(); insert(seed, 1);
        assertEquals(COMMITTED, seed.saveChecked(target(), ATOMIC));
        byte[] bytes = Files.readAllBytes(target()); var modified = Files.getLastModifiedTime(target());
        var loaded = RocketTransactionSavedData.load(read());
        var server = live(); loaded.bindServerForTests(server);
        flags(server, false, false); loaded.latchShutdown();
        loaded.save(target().toFile()); loaded.close(); loaded.abort();
        assertTrue(loaded.drainReport().isEmpty(), "A clean loaded journal created a writer");
        assertArrayEquals(bytes, Files.readAllBytes(target())); assertEquals(modified, Files.getLastModifiedTime(target()));
    }

    private Path target() { return directory.resolve(TYPE.fileName()); }
    private CompoundTag read() { return CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow(); }

    static DedicatedServer live() { return RocketTransactionWriterBenchmark.live(); }
    static RocketTransactionSavedData journal(MinecraftServer server) {
        var data = new RocketTransactionSavedData(); data.bindServerForTests(server); return data;
    }
    static void flags(MinecraftServer server, boolean running, boolean stopped) {
        UnstartedServer.set(MinecraftServer.class, server, "running", running);
        UnstartedServer.set(MinecraftServer.class, server, "stopped", stopped);
    }
    /** Real ticks: one per millisecond, each polling at tick START, until the condition holds. */
    static void pump(MinecraftServer server, RocketTransactionSavedData data, BooleanSupplier done) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (!done.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "The writer did not finish");
            RocketTransactionWriterBenchmark.at(server, server.getTickCount() + 1); data.pollWriter();
            try { Thread.sleep(1); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); return; }
        }
    }
    static void insert(RocketTransactionSavedData data, int index) {
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), new ResourceLocation("minecraft:overworld"),
                new RocketPosition(index * 32, 64, 0), List.of(new RocketBlock(new RocketPosition(0, 0, 0),
                        new RocketBlockState(new ResourceLocation("minecraft:iron_block"), Map.of()))), List.of(),
                new RocketStats(1, 10, 0, 0, 0, 0, 0, 0), index);
        data.journalFor(snapshot, UUID.randomUUID()).write(new RocketTransactionRecord(UUID.randomUUID(),
                RocketTransactionType.ASSEMBLY, RocketTransactionPhase.SPAWNED, snapshot.snapshotId(), snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot), index, null));
    }
    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) { try { latch.await(); break; } catch (InterruptedException again) { interrupted = true; } }
        if (interrupted) { Thread.currentThread().interrupt(); }
    }

    /** WARN lines of the writer core. */
    static final class LogLines implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(CheckedRootWriter.class);
        private final List<String> lines = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.appender.AbstractAppender appender =
                new org.apache.logging.log4j.core.appender.AbstractAppender("w129a-shutdown", null, null, true,
                        org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                    @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                        if (event.getLevel() == org.apache.logging.log4j.Level.WARN) {
                            lines.add("WARN " + event.getMessage().getFormattedMessage());
                        }
                    }
                };
        LogLines() { appender.start(); logger.addAppender(appender); }
        List<String> warnings() { return new ArrayList<>(lines); }
        @Override public void close() { logger.removeAppender(appender); appender.stop(); }
    }
}

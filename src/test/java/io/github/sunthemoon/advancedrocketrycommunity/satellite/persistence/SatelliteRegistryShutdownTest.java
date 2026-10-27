package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 items 9 and 10 and design section 6.2 for the satellite registry: running saves only request; the stop path
 * drains to a final target that counts logout and clock mutations within one shared three-attempt budget; close and
 * abort join before world ownership is released. A real but unstarted dedicated server supplies the tick and flags.
 */
@Timeout(120)
final class SatelliteRegistryShutdownTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aRunningSaveOnlyRequestsAndTheStoppingSaveDrainsToTheFinalTargetIncludingAClockMutation() {
        var server = live(); var data = registry(server);
        launch(data);
        data.save(target().toFile());
        assertTrue(data.writerBusy(), "Autosave made no request");
        assertTrue(data.isDirty(), "Autosave waited for the write");
        pump(server, data, () -> !data.writerBusy());
        assertFalse(data.isDirty());
        flags(server, false, false);
        data.latchShutdown();
        UUID logout = launch(data);
        assertFalse(data.requestWrite(target(), ATOMIC), "A gameplay request was admitted during shutdown");
        data.pollWriter();
        assertFalse(data.writerBusy(), "A tick start began a write during logout");
        data.completeDue(40); // a clock-only change after the last state change is part of the final target
        data.save(target().toFile());
        assertFalse(data.isDirty());
        assertEquals(data.save(new CompoundTag()), read());
        assertTrue(reload(target()).satellite(logout).isPresent());
        var report = data.drainReport().orElseThrow();
        assertTrue(report.success()); assertEquals(data.mutationSequence(), report.target());
        data.close();
        assertFalse(data.requestWrite(target(), ATOMIC));
    }

    @Test void bothShutdownFlagCombinationsRefuseRequestsAndStartsAndTheSaveObservesTheFlag() {
        for (boolean[] combination : List.of(new boolean[] {false, false}, new boolean[] {true, true}, new boolean[] {false, true})) {
            var server = live(); var data = registry(server);
            Path file = fresh("flags-" + combination[0] + "-" + combination[1]);
            launch(data); write(server, data, file, ATOMIC);
            flags(server, combination[0], combination[1]);
            launch(data);
            assertFalse(data.requestWrite(file, ATOMIC), "running=" + combination[0] + " stopped=" + combination[1]);
            data.pollWriter(); assertFalse(data.writerBusy());
            // No ServerStoppingEvent on the exception path: the save's own observation latches the shutdown.
            data.save(file.toFile());
            assertFalse(data.isDirty(), "The stop save did not cover the final RAM");
            assertEquals(data.save(new CompoundTag()), CheckedSavedDataFile.readPayload(file, REGISTRY).orElseThrow());
            data.close();
        }
    }

    @Test void aStaleCaptureBeforeLogoutMutationsKeepsTheRegistryDirtyUntilAFreshFinalCapture() throws Exception {
        for (var older : Ends.values()) {
            var server = live(); var data = registry(server);
            Path file = fresh("stale-" + older);
            launch(data);
            var paused = new Paused(older);
            assertTrue(data.requestWrite(file, paused.policy)); paused.awaitEntered(server, data);
            long stale = data.mutationSequence();
            flags(server, false, false); data.latchShutdown();
            UUID logout = launch(data);
            paused.release.countDown();
            data.save(file.toFile());
            var report = data.drainReport().orElseThrow();
            assertTrue(report.success(), older + ": " + report);
            assertEquals(2, report.attempts(), older + ": the in-flight attempt counts");
            assertEquals(data.mutationSequence(), report.target()); assertEquals(data.mutationSequence(), report.committed());
            assertTrue(stale < data.mutationSequence());
            assertFalse(data.isDirty());
            assertTrue(reload(file).satellite(logout).isPresent(), older + ": the final capture lacks the logout mutation");
            data.close();
        }
    }

    @Test void oneShutdownBudgetSpansSaveAndCloseAndExhaustionReportsWithoutClaimingSuccess() throws Exception {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long committed = data.mutationSequence();
        byte[] before = Files.readAllBytes(target());
        Files.createDirectory(directory.resolve(REGISTRY.fileName() + ".arce-pending")); // every staging fails
        launch(data);
        at(server, server.getTickCount() + CheckedRootWriter.CADENCE_TICKS);
        assertTrue(data.requestWrite(target(), ATOMIC));
        assertTrue(data.writerInFlight());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!data.writerForTests().completionReady()) { assertTrue(System.nanoTime() < deadline); Thread.sleep(1); }
        try (var log = new WarnLines()) {
            flags(server, false, false); data.latchShutdown();
            data.save(target().toFile());
            var exhausted = data.drainReport().orElseThrow();
            assertFalse(exhausted.success()); assertEquals("BUDGET_EXHAUSTED", exhausted.reason());
            assertEquals(3, exhausted.attempts()); assertEquals(data.mutationSequence(), exhausted.target());
            assertEquals(committed, exhausted.committed());
            launch(data); // a later persistence hook raises the target, not the budget
            data.close();
            var closed = data.drainReport().orElseThrow();
            assertFalse(closed.success()); assertEquals(3, closed.attempts()); assertEquals(data.mutationSequence(), closed.target());
            assertTrue(data.isDirty());
            assertArrayEquals(before, Files.readAllBytes(target()), "Exhaustion changed the last committed file");
            assertFalse(data.requestWrite(target(), ATOMIC), "A request after close was admitted");
            data.abort();
            assertEquals(List.of("WARN ARCE_CHECKED_WRITER_DRAIN_INCOMPLETE root=satellite_registry target="
                    + exhausted.target() + " committed=" + committed + " attempts=3 reason=BUDGET_EXHAUSTED"), log.warnings());
        }
    }

    @Test void closeJoinsAStalledWriterBeforeReturningAndKeepsAnInterruption() throws Exception {
        var server = live(); var data = registry(server);
        launch(data);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var finished = new AtomicBoolean();
        assertTrue(data.requestWrite(target(), ATOMIC.withCommitter((staged, file) -> {
            entered.countDown(); await(release); CheckedSavedDataFile.atomicMove(staged, file); finished.set(true);
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
        }, "W129b-close-release");
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
        var server = live(); var data = registry(server);
        UUID first = launch(data);
        var watch = data.watch(first);
        assertTrue(data.requestWrite(target(), ATOMIC));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!data.writerForTests().completionReady()) { assertTrue(System.nanoTime() < deadline); Thread.sleep(1); }
        data.abort();
        assertTrue(data.acknowledgement().isEmpty() || !data.acknowledgement().orElseThrow().proves(watch));
        assertEquals("ABORTED_UNRESOLVED", data.drainReport().orElseThrow().reason());
        assertFalse(data.drainReport().orElseThrow().success());
        assertFalse(data.requestWrite(target(), ATOMIC));

        var stopping = live(); flags(stopping, false, true);
        var skipped = registry(stopping); launch(skipped);
        Path other = fresh("skipped");
        assertFalse(skipped.requestWrite(other, ATOMIC), "A request during shutdown only binds the target");
        assertTrue(skipped.drainReport().isEmpty());
        skipped.close(); // no shutdown save happened: close sets the target and drains it
        assertTrue(skipped.drainReport().orElseThrow().success());
        assertFalse(skipped.isDirty());
        assertEquals(skipped.save(new CompoundTag()), CheckedSavedDataFile.readPayload(other, REGISTRY).orElseThrow());
    }

    @Test void aCleanLoadedRegistryIsNotRewrittenAndABlockedRootDrainsAsCoverageOnly() throws Exception {
        var seed = SatelliteMissionSavedData.create(0); launch(seed); seed.flush(target());
        byte[] bytes = Files.readAllBytes(target()); var modified = Files.getLastModifiedTime(target());
        var loaded = reload(target());
        var server = live(); loaded.bindServerForTests(server);
        flags(server, false, false); loaded.latchShutdown();
        loaded.save(target().toFile()); loaded.close(); loaded.abort();
        assertTrue(loaded.drainReport().isEmpty(), "A clean loaded registry created a writer");
        assertArrayEquals(bytes, Files.readAllBytes(target())); assertEquals(modified, Files.getLastModifiedTime(target()));

        CompoundTag raw = SatelliteMissionSavedData.create(0).save(new CompoundTag());
        raw.putInt("schema_version", 999);
        var blocked = SatelliteMissionSavedData.load(raw);
        var stopping = live(); blocked.bindServerForTests(stopping); blocked.setDirty();
        flags(stopping, false, false);
        Path file = fresh("blocked");
        blocked.save(file.toFile());
        assertTrue(blocked.drainReport().orElseThrow().success());
        assertFalse(blocked.isDirty()); assertTrue(blocked.acknowledgement().isEmpty(), "Coverage is never a gameplay proof");
        assertEquals(raw, CheckedSavedDataFile.readPayload(file, REGISTRY).orElseThrow());
        blocked.close();
    }

    private Path target() { return directory.resolve(REGISTRY.fileName()); }
    private Path fresh(String name) {
        try { return Files.createDirectories(directory.resolve(name)).resolve(REGISTRY.fileName()); }
        catch (IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    private CompoundTag read() { return CheckedSavedDataFile.readPayload(target(), REGISTRY).orElseThrow(); }

    /** WARN lines of the writer core. */
    static final class WarnLines implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(CheckedRootWriter.class);
        private final List<String> lines = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.appender.AbstractAppender appender =
                new org.apache.logging.log4j.core.appender.AbstractAppender("w129b-shutdown", null, null, true,
                        org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                    @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                        if (event.getLevel() == org.apache.logging.log4j.Level.WARN) {
                            lines.add("WARN " + event.getMessage().getFormattedMessage());
                        }
                    }
                };
        WarnLines() { appender.start(); logger.addAppender(appender); }
        List<String> warnings() { return new ArrayList<>(lines); }
        @Override public void close() { logger.removeAppender(appender); appender.stop(); }
    }

}

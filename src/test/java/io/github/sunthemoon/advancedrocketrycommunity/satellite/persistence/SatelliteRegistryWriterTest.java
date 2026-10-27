package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 items 4 and 5, design section 4.1: cases A to H of the snapshot epoch with real paused IO on a live-like
 * registry, and the running server's request semantics. Mutations happen on the owner thread while the IO thread
 * waits inside the committer; nothing mutates live RAM from IO.
 */
@Timeout(120)
final class SatelliteRegistryWriterTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aRunningRequestReturnsAtOncePublishesOnlyAtTickStartAndAnAutosaveNeverWaits() {
        var server = live(); var data = registry(server);
        UUID first = launch(data);
        var watch = data.watch(first);
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy));
        paused.awaitEntered(server, data);
        assertTrue(data.writerInFlight());
        paused.release.countDown();
        long deadline = System.nanoTime() + 20_000_000_000L;
        while (!data.writerForTests().completionReady()) { assertTrue(System.nanoTime() < deadline); Thread.onSpinWait(); }
        assertTrue(data.acknowledgement().isEmpty(), "A completion was published off the tick");
        assertTrue(data.isDirty()); assertTrue(data.writerInFlight(), "Until publication the capture is in flight");
        data.pollWriter();
        assertTrue(data.acknowledgement().orElseThrow().proves(watch), "Tick START published the completion");
        assertFalse(data.writerBusy());
        UUID second = launch(data);
        data.save(target().toFile());
        assertTrue(data.writerBusy(), "Autosave made no request");
        assertTrue(data.isDirty(), "Autosave waited for its write");
        pump(server, data, () -> !data.writerBusy());
        assertFalse(data.isDirty());
        assertTrue(reload(target()).satellite(second).isPresent());
        assertThrows(IllegalStateException.class, () -> runOn(data::pollWriter), "Publication is owner-only");
    }

    @Test void caseAClockOnlyCaptureCarriesEAndCommitsWithoutAdvancing() {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long e = data.saveEpoch();
        data.completeDue(10);
        assertTrue(data.isDirty()); assertFalse(data.flushPending());
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(e, data.saveEpoch());
        assertFalse(data.isDirty()); assertFalse(data.flushPending());
        assertEquals(e, read().getLong("save_epoch"));
    }

    @Test void caseBAChangedCaptureCarriesTheSuccessorAndCommitsWithEveryFlagCleared() {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long e = data.saveEpoch();
        UUID changed = launch(data);
        var watch = data.watch(changed);
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(e + 1, data.saveEpoch());
        assertFalse(data.isDirty()); assertFalse(data.flushPending());
        assertTrue(data.acknowledgement().orElseThrow().proves(watch));
        assertEquals(e + 1, data.captureForTests().epoch(), "An unchanged registry carries E again");
    }

    @Test void caseCAFailedFlightWithoutMutationKeepsEAndHAndCarriesWAgain() {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long e = data.saveEpoch();
        byte[] before = bytes();
        launch(data);
        var paused = new Paused(Ends.FAIL);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(e, data.saveEpoch());
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
        assertArrayEquals(before, bytes());
        assertEquals(e + 1, data.captureForTests().epoch(), "W119 allows the unreserved w to be carried again");
        write(server, data, target(), ATOMIC);
        assertEquals(e + 1, data.saveEpoch());
    }

    @Test void caseDAnUnknownFlightReservesWAndClearsTheAcknowledgement() {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long e = data.saveEpoch();
        launch(data);
        var paused = new Paused(Ends.UNKNOWN);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(e, data.saveEpoch());
        assertTrue(data.acknowledgement().isEmpty());
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
        assertEquals(e + 2, data.captureForTests().epoch(), "UNKNOWN reserves its epoch");
        write(server, data, target(), ATOMIC);
        assertEquals(e + 2, data.saveEpoch());
    }

    @Test void caseEAMutationDuringACommittedFlightStampsWAndIsProvedOnlyByTheNextCapture() {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long e = data.saveEpoch();
        UUID first = launch(data);
        var firstWatch = data.watch(first);
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        UUID later = launch(data);
        var laterWatch = data.watch(later);
        assertEquals(e + 1, startEpoch(data, later), "A mutation during the flight stamps w");
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(e + 1, data.saveEpoch());
        assertTrue(data.acknowledgement().orElseThrow().proves(firstWatch));
        assertFalse(data.acknowledgement().orElseThrow().proves(laterWatch));
        assertFalse(startEpoch(data, later) < data.saveEpoch(), "M must not look durable");
        assertTrue(reload(target()).satellite(later).isEmpty());
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
        write(server, data, target(), ATOMIC);
        assertEquals(e + 2, data.saveEpoch());
        assertTrue(data.acknowledgement().orElseThrow().proves(laterWatch));
        assertTrue(startEpoch(data, later) < data.saveEpoch());
        assertFalse(data.isDirty()); assertFalse(data.flushPending());
    }

    @Test void casesFAndGAFailedOrUnknownFlightWithAMutationReservesWAndTheNextCaptureProvesIt() {
        for (var ends : new Ends[] {Ends.FAIL, Ends.UNKNOWN}) {
            var server = live(); var data = registry(server);
            Path file = fresh(ends.name());
            launch(data); write(server, data, file, ATOMIC);
            long e = data.saveEpoch();
            launch(data);
            var paused = new Paused(ends);
            assertTrue(data.requestWrite(file, paused.policy)); paused.awaitEntered(server, data);
            UUID later = launch(data);
            var laterWatch = data.watch(later);
            assertEquals(e + 1, startEpoch(data, later));
            paused.release.countDown();
            pump(server, data, () -> !data.writerBusy());
            assertEquals(e, data.saveEpoch(), ends.name());
            if (ends == Ends.UNKNOWN) { assertTrue(data.acknowledgement().isEmpty()); }
            UUID after = launch(data);
            assertEquals(e, startEpoch(data, after), "Stamps return to E after publication");
            assertEquals(e + 2, data.captureForTests().epoch(), ends + " reserved w because M was stamped w");
            write(server, data, file, ATOMIC);
            assertEquals(e + 2, data.saveEpoch());
            assertTrue(data.acknowledgement().orElseThrow().proves(laterWatch));
            assertTrue(startEpoch(data, later) < data.saveEpoch() && startEpoch(data, after) < data.saveEpoch());
        }
    }

    @Test void caseHAStateChangeDuringAClockOnlyFlightStampsEAndKeepsTheChangedFlag() {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long e = data.saveEpoch();
        data.completeDue(10);
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        UUID changed = launch(data);
        var watch = data.watch(changed);
        assertEquals(e, startEpoch(data, changed), "The flight carries E, so the snapshot epoch is E");
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(e, data.saveEpoch());
        assertTrue(data.isDirty()); assertTrue(data.flushPending());
        assertFalse(data.acknowledgement().orElseThrow().proves(watch));
        assertEquals(e + 1, data.captureForTests().epoch());
        write(server, data, target(), ATOMIC);
        assertEquals(e + 1, data.saveEpoch());
        assertTrue(data.acknowledgement().orElseThrow().proves(watch));
    }

    @Test void aRequestInFlightOnlySetsThePendingFlagAndTheNextStartCapturesFresh() {
        var server = live(); var data = registry(server);
        launch(data);
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        UUID later = launch(data);
        assertTrue(data.requestWrite(target(), ATOMIC));
        assertTrue(data.writerBusy());
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(2, data.writerForTests().completedWrites(), "The pending request took a fresh capture");
        assertTrue(reload(target()).satellite(later).isPresent());
        assertFalse(data.isDirty());
        assertThrows(IllegalArgumentException.class, () -> data.requestWrite(fresh("other"), ATOMIC),
                "The first request bound the target");
    }

    @Test void anErrorOnTheWriterThreadIsUnknownAndReservesItsEpoch() {
        var server = live(); var data = registry(server);
        launch(data); write(server, data, target(), ATOMIC);
        long e = data.saveEpoch();
        launch(data);
        var error = ATOMIC.withCommitter((staged, file) -> { throw new AssertionError("W129b injected writer Error"); });
        write(server, data, target(), error);
        assertEquals(e, data.saveEpoch());
        assertTrue(data.acknowledgement().isEmpty());
        assertEquals(e + 2, data.captureForTests().epoch());
    }

    private Path target() { return directory.resolve(REGISTRY.fileName()); }
    private Path fresh(String name) {
        try { return Files.createDirectories(directory.resolve(name)).resolve(REGISTRY.fileName()); }
        catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    private CompoundTag read() {
        return io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile
                .readPayload(target(), REGISTRY).orElseThrow();
    }
    private byte[] bytes() {
        try { return Files.readAllBytes(target()); }
        catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
    private static void runOn(Runnable task) {
        var failure = new java.util.concurrent.atomic.AtomicReference<RuntimeException>();
        Thread thread = new Thread(() -> { try { task.run(); } catch (RuntimeException thrown) { failure.set(thrown); } });
        thread.start();
        try { thread.join(); } catch (InterruptedException stop) { Thread.currentThread().interrupt(); }
        if (failure.get() != null) { throw failure.get(); }
    }
}

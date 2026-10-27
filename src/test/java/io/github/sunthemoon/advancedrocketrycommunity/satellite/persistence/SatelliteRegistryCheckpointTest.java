package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 item 6, design section 4.2: {@code requestCheckpoint} returns at once and is answered by the first published
 * completion with s >= m that forced the directory or did not commit; the fast path writes nothing; removal tokens are
 * covered only by a later commit of the same session; at most 256 requests wait.
 */
@Timeout(120)
final class SatelliteRegistryCheckpointTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aCheckpointReturnsAtOnceAndTheFirstForcedCoveringCompletionAnswersIt() {
        var server = live(); var data = registry(server);
        var forces = new CopyOnWriteArrayList<Thread>();
        UUID satellite = launch(data);
        var watch = data.watch(satellite);
        var checkpoint = data.requestCheckpoint(target(), List.of(watch), directory -> forces.add(Thread.currentThread()));
        assertTrue(checkpoint.answer().isEmpty(), "A checkpoint request never waits");
        pump(server, data, () -> checkpoint.answer().isPresent());
        var answer = checkpoint.answer().orElseThrow();
        assertEquals(SatelliteRegistryProof.Outcome.COMMITTED, answer.outcome());
        assertTrue(answer.proves(watch)); assertFalse(answer.directoryForceFailed());
        assertEquals(1, forces.size()); assertNotSame(Thread.currentThread(), forces.get(0), "The force runs on IO");
        assertTrue(data.proves(watch));
    }

    @Test void aPlainCommitInFlightDoesNotAnswerAForcedCheckpointButAFailureDoes() {
        for (var first : List.of(Ends.COMMIT, Ends.FAIL)) {
            var server = live(); var data = registry(server);
            Path file = fresh("plain-" + first);
            var forces = new CopyOnWriteArrayList<Thread>();
            UUID satellite = launch(data);
            var watch = data.watch(satellite);
            var paused = new Paused(first);
            assertTrue(data.requestWrite(file, paused.policy)); paused.awaitEntered(server, data);
            var checkpoint = data.requestCheckpoint(file, List.of(watch), directory -> forces.add(Thread.currentThread()));
            paused.release.countDown();
            pump(server, data, () -> data.writerForTests().completedWrites() == 1);
            if (first == Ends.COMMIT) {
                assertTrue(checkpoint.answer().isEmpty(), "An unforced commit answered a forcing checkpoint");
                assertTrue(data.proves(watch), "The unforced commit still proves the watch for other callers");
                pump(server, data, () -> checkpoint.answer().isPresent());
                assertEquals(SatelliteRegistryProof.Outcome.COMMITTED, checkpoint.answer().orElseThrow().outcome());
                assertEquals(1, forces.size());
            } else {
                assertEquals(SatelliteRegistryProof.Outcome.FAILED, checkpoint.answer().orElseThrow().outcome(),
                        "A failure is the first answer, so D3 counts one failure");
                assertTrue(forces.isEmpty());
                assertTrue(data.writerBusy(), "The forcing request waits out the backoff");
            }
        }
    }

    @Test void theFastPathAnswersAtOnceWithoutWritingAndAnOlderAcknowledgementDoesNotCoverANewerWatch() {
        var server = live(); var data = registry(server);
        UUID satellite = launch(data);
        var watch = data.watch(satellite);
        write(server, data, target(), ATOMIC);
        data.writePolicy(new CheckedSavedDataPolicy(REGISTRY, (staged, file) -> fail("The fast path wrote")));
        var fast = data.requestCheckpoint(target(), List.of(watch), directory -> fail("The fast path forced"));
        assertEquals(SatelliteRegistryProof.Outcome.COMMITTED, fast.answer().orElseThrow().outcome());
        assertTrue(fast.answer().orElseThrow().proves(watch)); assertFalse(data.writerBusy());
        data.writePolicy(ATOMIC);
        UUID later = launch(data);
        var laterWatch = data.watch(later);
        var slow = data.requestCheckpoint(target(), List.of(watch, laterWatch), directory -> { });
        assertTrue(slow.answer().isEmpty());
        pump(server, data, () -> slow.answer().isPresent());
        assertTrue(slow.answer().orElseThrow().proves(laterWatch));
    }

    @Test void removalTokensAreCoveredOnlyByALaterCommitOfTheSameSession() {
        var server = live(); var data = registry(server);
        UUID satellite = launch(data);
        UUID owner = data.satellite(satellite).orElseThrow().ownerId();
        assertTrue(data.cancel(io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager
                .initialMissionId(satellite), owner, false, 1).success());
        write(server, data, target(), ATOMIC);
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        // The registry keeps the satellite's history, so remove an idle craft instead.
        UUID idle = UUID.randomUUID();
        assertTrue(data.launchIdle(time -> RegistryWriterFixtures.idle(idle, time), 2).success());
        assertTrue(data.decommission(idle, GroundSurveyFixtures.OWNER, true, true).success());
        var token = data.token();
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertFalse(data.covers(token), "A capture taken before the removal cannot cover its token");
        write(server, data, target(), ATOMIC);
        assertTrue(data.covers(token));
        var other = registry(live());
        assertFalse(other.covers(token), "Another session never covers a token");
        launch(data);
        write(server, data, target(), ATOMIC.withCommitter((staged, file) -> { throw new AssertionError("W129b UNKNOWN"); }));
        assertFalse(data.covers(token), "UNKNOWN clears the acknowledgement");
        write(server, data, target(), ATOMIC);
        assertTrue(data.covers(token));
    }

    @Test void atMost256CheckpointsWaitAndShutdownDiscardsThemUnanswered() {
        var server = live(); var data = registry(server);
        launch(data);
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(target(), paused.policy)); paused.awaitEntered(server, data);
        var waiting = new ArrayList<SatelliteRegistryProof.Checkpoint>();
        for (int index = 0; index < SatelliteRegistryWriter.MAX_CHECKPOINTS; index++) {
            launch(data);
            waiting.add(data.requestCheckpoint(target(), List.of(), directory -> { }));
        }
        assertTrue(waiting.stream().noneMatch(SatelliteRegistryProof.Checkpoint::discarded));
        var refused = data.requestCheckpoint(target(), List.of(), directory -> { });
        assertTrue(refused.discarded(), "The 257th waiting checkpoint was queued");
        flags(server, false, false);
        data.latchShutdown();
        assertTrue(waiting.stream().allMatch(checkpoint -> checkpoint.discarded() && checkpoint.answer().isEmpty()));
        paused.release.countDown();
        data.close();
        assertTrue(waiting.stream().allMatch(checkpoint -> checkpoint.answer().isEmpty()), "The stop path answered a checkpoint");
    }

    private Path target() { return directory.resolve(REGISTRY.fileName()); }
    private Path fresh(String name) {
        try { return Files.createDirectories(directory.resolve(name)).resolve(REGISTRY.fileName()); }
        catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }
}

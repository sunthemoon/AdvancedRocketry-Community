package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowFlightBenchmarkMetricsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temporary;

    @Test void inactiveObserverDoesNotCollectAndCannotExportMidMeasurement() throws Exception {
        var metrics = new EscrowFlightBenchmarkMetrics();
        metrics.sample("ignored", 1); metrics.checkpoint("IGNORED");
        assertTrue(metrics.samples().isEmpty()); assertTrue(metrics.checkpoints().isEmpty());
        metrics.start(); metrics.sample("capture_main", 123);
        assertThrows(IllegalStateException.class, () -> metrics.write(temporary.resolve("samples.csv")));
        metrics.stop(); metrics.sample("ignored", 1); metrics.write(temporary.resolve("samples.csv"));
        assertEquals(List.of("phase,generation,value", "capture_main,-1,123"), Files.readAllLines(temporary.resolve("samples.csv")));
    }
    @Test void everyGenerationIncludingAnEmptyPruningGenerationRetainsAllCosts() {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start();
        var first = EscrowLedgerWriter.Snapshot.of(EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1), Set.of());
        var prune = EscrowLedgerWriter.Snapshot.of(first.ledger().retire(Set.of(first.ledger().records().get(0).id())).generation(2), Set.of());
        for (var snapshot : List.of(first, prune)) {
            long generation = snapshot.ledger().writerGeneration();
            metrics.encoded(snapshot, 50); metrics.captured(snapshot, 100); metrics.queued(generation, 10);
            metrics.writing(generation); metrics.written(generation, 200); metrics.acknowledged(generation);
            assertEquals(Set.of("generation_capture_main", "generation_encode_main", "generation_root_bytes", "generation_queue_age", "generation_request_queue_age",
                            "generation_writer_io", "generation_handoff", "generation_total_latency"),
                    metrics.samples().stream().filter(sample -> sample.generation() == generation).map(EscrowFlightBenchmarkMetrics.Sample::phase)
                            .collect(java.util.stream.Collectors.toSet()));
        }
        assertEquals(1, metrics.topology().get(1L).get("records"));
        assertEquals(0, metrics.topology().get(2L).get("records"));
        assertThrows(IllegalStateException.class, () -> metrics.captured(first, 1));
    }
    @Test void missingWriterObservationAndNegativeOrMalformedSamplesFailClosed() {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start();
        metrics.captured(EscrowLedgerWriter.Snapshot.of(EscrowTestFixtures.ledger().generation(1), Set.of()), 1);
        assertThrows(IllegalStateException.class, () -> metrics.acknowledged(1));
        assertThrows(IllegalArgumentException.class, () -> metrics.sample("capture_main", -1));
        assertThrows(IllegalArgumentException.class, () -> metrics.sample("bad,phase", 1));
        assertThrows(IllegalStateException.class, () -> metrics.checkpoint("bad,point"));
    }
    @Test void samplesAndCheckpointsAreBoundedAndRetainCounts() {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start();
        for (int i = 0; i < EscrowFlightBenchmarkMetrics.MAX_SAMPLES; i++) { metrics.sample("tick_main", i); }
        assertThrows(IllegalStateException.class, () -> metrics.sample("tick_main", 1));
        metrics.checkpoint("F8_PRUNE_ACK"); metrics.checkpoint("F8_PRUNE_ACK");
        assertEquals(2, metrics.checkpoints().get("F8_PRUNE_ACK"));
    }
    @Test void tickObservationsIncludeAllocationAndSeparateSaveTicksWithoutDroppingThem() {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start();
        metrics.startTick(); metrics.endTick(false); metrics.startTick(); metrics.endTick(true);
        assertEquals(2, metrics.samples().stream().filter(sample -> sample.phase().equals("tick_main")).count());
        assertEquals(2, metrics.samples().stream().filter(sample -> sample.phase().equals("allocation_bytes")).count());
        assertEquals(1, metrics.samples().stream().filter(sample -> sample.phase().equals("tick_main_nonsave")).count());
    }
}

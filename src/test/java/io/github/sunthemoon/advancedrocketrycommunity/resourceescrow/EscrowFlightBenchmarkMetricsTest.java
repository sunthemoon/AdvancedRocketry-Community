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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EscrowFlightBenchmarkMetricsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temporary;

    static java.util.stream.Stream<String> knownPhases() { return EscrowFlightBenchmarkMetrics.PHASES.stream().sorted(); }
    @Test void pythonCollectorCatalogExactlyMatchesJavaCollector() throws Exception {
        String[] phases = new com.google.gson.Gson().fromJson(
                Files.readString(Path.of("scripts/governed_benchmark_phases.json")), String[].class);
        assertEquals(phases.length, Set.of(phases).size());
        assertEquals(EscrowFlightBenchmarkMetrics.PHASES, Set.of(phases));
    }
    @ParameterizedTest @MethodSource("knownPhases")
    void eachKnownPhaseIsCollectedWithoutRenaming(String phase) {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start(); metrics.sample(phase, 17);
        assertEquals(List.of(new EscrowFlightBenchmarkMetrics.Sample(phase, -1, 17)), metrics.samples());
    }
    @Test void emittingSourcesHaveNoUncataloguedLiteralPhasesOrDynamicFamilies() throws Exception {
        // Independent source inventory: adding an emitter must update the release-test catalog, not silently pass.
        var directory = Path.of("src/main/java/io/github/sunthemoon/advancedrocketrycommunity/resourceescrow");
        var literal = java.util.regex.Pattern.compile("(?:sample|timing)\\(\"([^\"]+)\"\\s*,");
        var generation = java.util.regex.Pattern.compile("add\\(\"([^\"]+)\"\\s*,");
        var dynamic = java.util.regex.Pattern.compile("(?:sample|timing)\\(\"([^\"]+)\"\\s*\\+");
        var prefixes = new java.util.HashSet<String>(); int observations = 0;
        try (var sources = Files.list(directory)) {
            for (var file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                // A different collector's private generation labels never reach the governed collector.
                if (Set.of("EscrowConsumerTimingProbe.java", "EscrowBenchmarkProbe.java").contains(file.getFileName().toString())) { continue; }
                String source = Files.readString(file);
                var matcher = literal.matcher(source);
                while (matcher.find()) {
                    observations++;
                    assertTrue(EscrowFlightBenchmarkMetrics.PHASES.contains(matcher.group(1)), file + ": " + matcher.group(1));
                }
                if (file.getFileName().toString().equals("EscrowFlightBenchmarkMetrics.java")) {
                    matcher = generation.matcher(source);
                    while (matcher.find()) {
                        observations++;
                        assertTrue(EscrowFlightBenchmarkMetrics.PHASES.contains(matcher.group(1)), matcher.group(1));
                    }
                }
                matcher = dynamic.matcher(source);
                while (matcher.find()) { prefixes.add(matcher.group(1)); }
            }
        }
        assertTrue(observations >= 60, "Emitter inventory unexpectedly empty or incomplete");
        assertEquals(Set.of("f1.stall.", "cancel.stall.", "settlement.stall."), prefixes);
        for (var reason : EscrowReason.values()) {
            if (reason.retryableProofFailure()) {
                for (var prefix : prefixes) { assertTrue(EscrowFlightBenchmarkMetrics.PHASES.contains(prefix + reason.name())); }
            }
        }
    }
    @Test void unknownNamesAndUnknownDiagnosticSuffixesFailWithoutCollecting() {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start();
        for (var phase : List.of("unknown_phase", "settlement.unknown", "settlement.stall.UNKNOWN",
                "f1.stall.UNKNOWN", "cancel.wait.UNKNOWN", "settlement.attempt.extra", "", "bad,phase")) {
            assertThrows(IllegalArgumentException.class, () -> metrics.sample(phase, 0), phase);
        }
        assertThrows(IllegalArgumentException.class, () -> metrics.sample(null, 0));
        assertTrue(metrics.samples().isEmpty());
        // Invalid names do not consume the bounded collector's capacity.
        for (int i = 0; i < EscrowFlightBenchmarkMetrics.MAX_SAMPLES; i++) { metrics.sample("settlement.attempt", 0); }
        assertEquals(EscrowFlightBenchmarkMetrics.MAX_SAMPLES, metrics.samples().size());
    }
    @Test void knownDiagnosticWithCommaIsQuotedForCsv() throws Exception {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start();
        metrics.sample("settlement.stall.Rocket placement, occupancy or stationary state changed", 0);
        metrics.stop(); metrics.write(temporary.resolve("diagnostic.csv"));
        assertEquals(List.of("phase,generation,value",
                "\"settlement.stall.Rocket placement, occupancy or stationary state changed\",-1,0"),
                Files.readAllLines(temporary.resolve("diagnostic.csv")));
    }

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
    @Test void deferCountsRemainRealCheckpointsAndTimingIsOnlyTheContainingTick() {
        var metrics = new EscrowFlightBenchmarkMetrics(); metrics.start();
        metrics.startTick(); metrics.endTick(false);
        metrics.startTick(); metrics.checkpoint("WORLD_FIRST_DEFER"); metrics.checkpoint("WORLD_FIRST_DEFER"); metrics.endTick(false);
        metrics.startTick(); metrics.endTick(false);
        assertEquals(2, metrics.checkpoints().get("WORLD_FIRST_DEFER"));
        var ticks = metrics.samples().stream().filter(sample -> sample.phase().equals("tick_main")).toList();
        var deferred = metrics.samples().stream().filter(sample -> sample.phase().equals("tick_main_world_first_defer")).toList();
        assertEquals(3, ticks.size()); assertEquals(1, deferred.size());
        assertEquals(ticks.get(1).value(), deferred.get(0).value());
    }
}

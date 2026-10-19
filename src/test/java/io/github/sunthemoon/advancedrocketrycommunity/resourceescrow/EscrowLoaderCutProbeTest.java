package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowLoaderCutProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temporary;

    @Test void reversedNativeRequestIsAValidationRefusalWithoutChangingTheOriginal() {
        var op = EscrowTestFixtures.operation(1, 1, 0);
        var before = EscrowCodec.operation(op);
        assertTrue(EscrowLoaderCutProbe.oppositeRefused(op));
        assertEquals(before, EscrowCodec.operation(op));
        assertEquals(op, EscrowCodec.operation(before));
    }

    @Test void reconciliationNamesSelectExistingAcknowledgementAndNativeBarrierBoundaries() {
        String[][] cases = {{"D2_C0", "K0"}, {"D2_C1_BEFORE", "K1"}, {"D2_C1_REPLACED", "K2"},
                {"D2_C1_ACK", "K2_ACK"}, {"D2_C2", "K3"}, {"D2_C3", "C3_FROZEN"},
                {"D2_C4_BEFORE", "C4_BEFORE"}, {"D2_C4_AFTER", "C4_AFTER"}, {"D2_C5_BEFORE", "K6"},
                {"D2_C5_AFTER", "K6_FORCED"}, {"D2_C6_BEFORE", "C6_BEFORE"}, {"D2_C6_AFTER", "C6_AFTER"},
                {"D2_C6_JOINT", "K7"}, {"D2_C7_BEFORE", "K8"}, {"D2_C7_REPLACED", "K9"},
                {"D2_C7_ACK", "K10_ACK"}, {"D2_C8", "K10"}, {"D2_C7_REPLACED_BI", "K9"},
                {"C1", "CHAIN_PREFIX"}, {"C2", "CHAIN_PROVED"}, {"C2_PARTIAL_REPLACED", "CHAIN_REPLACED"},
                {"C2_PARTIAL_RELEASED", "CHAIN_RELEASED"}, {"C2_PARTIAL_REPLACED_BI", "CHAIN_REPLACED"}};
        for (var value : cases) { assertEquals(value[1], EscrowLoaderCutProbe.boundary("LF_M_" + value[0])); }
        assertEquals("K5", EscrowLoaderCutProbe.boundary("LF_M_K5"));
    }

    @Test void matrixSelectionDoesNotSelectHistoricalLoaderOrOtherProbes() {
        assertTrue(EscrowLoaderCutProbe.selected("LF_M_N0"));
        assertTrue(EscrowLoaderCutProbe.selected("LF_M_K9_BI"));
        for (String value : List.of("LF_C2_SOURCE_AFTER", "K2", "CR_C7_ACKNOWLEDGED", "", "LF_")) {
            assertFalse(EscrowLoaderCutProbe.selected(value));
        }
        assertFalse(EscrowLoaderCutProbe.selected(null));
    }

    @Test void oneSidedDestinationCutsPrimeOnlyUnchangedPreAdmissionTerrain() {
        assertTrue(EscrowLoaderCutProbe.primeTerrain("K5"));
        assertTrue(EscrowLoaderCutProbe.primeTerrain("C4_BEFORE"));
        for (String boundary : List.of("C4_AFTER", "CHAIN_PREFIX", "CHAIN_PROVED", "K4")) {
            assertFalse(EscrowLoaderCutProbe.primeTerrain(boundary));
        }
    }

    @Test void loaderStorageAliasesSelectOnlyTheExistingFaultCases() {
        assertEquals("E4L1_UNKNOWN", EscrowLoaderStorageProbe.writerCase("LF_M_E4L1_UNKNOWN"));
        assertEquals("E4L2_UNKNOWN", EscrowLoaderStorageProbe.writerCase("LF_M_E4L2_UNKNOWN"));
        assertEquals("E4L2_RESAVE", EscrowLoaderStorageProbe.writerCase("LF_M_E4L2_RESAVE_RESOLVED"));
        for (String name : List.of("LF_M_N2_UNKNOWN", "E4L1_UNKNOWN", "LF_M_E1", "", "LF_M_K1")) {
            assertEquals(name, EscrowLoaderStorageProbe.writerCase(name));
        }
        assertTrue(EscrowLoaderStorageProbe.selected("E4L1_FAIL"));
        assertTrue(EscrowLoaderStorageProbe.selected("E4L2_RESAVE_RESOLVED"));
        assertFalse(EscrowLoaderStorageProbe.selected("E1"));
        assertFalse(EscrowLoaderStorageProbe.selected("E4L1_RESAVE"));
    }

    @Test void baselineObservationsBracketTheSameNativeReplacementWithoutChangingAcknowledgement() throws Exception {
        Path file = temporary.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var root = EscrowLedger.empty(UUID.randomUUID()).generation(1);
        var events = new CopyOnWriteArrayList<String>();
        var replies = new CopyOnWriteArrayList<EscrowLedgerWriter.Completion>();
        try (var writer = new EscrowLedgerWriter(file, 0, (staged, target) -> {
            assertEquals(List.of("BASELINE_STAGED"), events);
            assertTrue(replies.isEmpty());
            CheckedSavedDataFile.atomicMove(staged, target);
            events.add("MOVE");
        }, name -> {
            assertTrue(replies.isEmpty());
            if (name.equals("BASELINE_REPLACED")) {
                assertEquals(root, EscrowCodec.decode(CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
            }
            events.add(name);
        })) {
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(root, Set.of()), replies::add));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            for (long tick = 1; !writer.idle() && System.nanoTime() < deadline; tick++) {
                writer.tick(tick); Thread.sleep(1);
            }
            assertTrue(writer.idle());
            assertEquals(List.of("BASELINE_STAGED", "MOVE", "BASELINE_REPLACED"), events);
            assertEquals(1, replies.size());
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, replies.get(0).outcome());
            assertEquals(root, replies.get(0).snapshot().ledger());
            assertFalse(writer.unknown());
        }
    }

    @Test void baselineSelectionIncludesFirstGenerationButExcludesIssuedAdmissions() {
        var empty = EscrowCodec.encode(EscrowLedger.empty(UUID.randomUUID()).generation(1));
        assertTrue(EscrowLoaderCutProbe.baselineCandidate(empty));
        empty.putLong("writer_generation", 3);
        assertTrue(EscrowLoaderCutProbe.baselineCandidate(empty));
        empty.putLong("issued_through", 1);
        assertFalse(EscrowLoaderCutProbe.baselineCandidate(empty));
        empty.putLong("issued_through", 0); empty.putLong("writer_generation", 0);
        assertFalse(EscrowLoaderCutProbe.baselineCandidate(empty));
    }

    @Test void threeDefiniteBaselineFailuresBecomeUnavailableWithoutAFile() throws Exception {
        baselineFailures(false);
    }

    @Test void threeUnknownBaselineFailuresKeepTheExactFirstGenerationAndBecomeUnavailable() throws Exception {
        baselineFailures(true);
    }

    private void baselineFailures(boolean unknown) throws Exception {
        Path file = temporary.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var root = EscrowLedger.empty(UUID.randomUUID());
        var replies = new CopyOnWriteArrayList<EscrowLedgerWriter.Completion>();
        var events = new CopyOnWriteArrayList<String>();
        var writer = new EscrowLedgerWriter(file, 0, (staged, target) -> {
            if (unknown) { throw new java.io.IOException("Release-test ambiguous replacement outcome"); }
            fail("Definite pre-replacement failure reached the committer");
        }, name -> {
            if (!unknown && (name.equals("BASELINE_STAGED") || name.equals("K8"))) { EscrowReleaseProbe.definiteFailure(); }
            events.add(name);
        });
        try {
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            for (long tick = 0; replies.size() < 3 && System.nanoTime() < deadline; tick++) {
                if (writer.idle()) {
                    var next = root.generation(writer.lastGeneration() + 1);
                    assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(next, Set.of()), replies::add));
                }
                writer.tick(tick); Thread.sleep(1);
            }
            assertEquals(3, replies.size());
            assertTrue(writer.unavailable());
            assertEquals(unknown, writer.unknown());
            assertEquals(!unknown, writer.idle());
            assertFalse(java.nio.file.Files.exists(file));
            assertEquals(3, events.stream().filter(name -> name.equals(unknown ? "WRITE_UNKNOWN" : "WRITE_FAILED_BEFORE_REPLACE")).count());
            for (int i = 0; i < 3; i++) {
                assertEquals(unknown ? EscrowLedgerWriter.Outcome.UNKNOWN : EscrowLedgerWriter.Outcome.FAILED_BEFORE_REPLACE, replies.get(i).outcome());
                assertEquals(unknown ? 1 : i + 1, replies.get(i).generation());
                assertEquals(0, replies.get(i).snapshot().ledger().issuedThrough());
                assertTrue(replies.get(i).snapshot().ledger().records().isEmpty());
                if (unknown) { assertSame(replies.get(0).snapshot(), replies.get(i).snapshot()); }
            }
        } finally { writer.abort(); }
    }
}

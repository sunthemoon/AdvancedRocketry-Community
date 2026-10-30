package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;

class EscrowLoaderS6SettlementProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    @Test void idleWriterDoesNotAcknowledgeAPacedManualTransition() throws Exception {
        var op = EscrowTestFixtures.operation(1);
        var initial = EscrowTestFixtures.ledger(op).generation(1);
        Path file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(file, () -> EscrowCodec.encode(initial));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(initial));
        var io = new AwaitedLedgerIo();
        try (var coordinator = new EscrowCoordinator(data, io.writer(file, 1, CheckedSavedDataFile::atomicMove))) {
            coordinator.tick(10); data.setDirty(); coordinator.autosave();
            io.awaitWrites(); coordinator.tick(11);
            assertTrue(coordinator.writerIdle());
            coordinator.transition(op.id(), EscrowReason.OTHER_STATE); coordinator.persistTransitions();
            assertTrue(coordinator.writerIdle());
            assertEquals(EscrowReason.OTHER_STATE, coordinator.currentReason(data.ledger().records().get(0)));
            assertEquals(EscrowReason.NONE, data.ledger().records().get(0).reason());
            assertFalse(EscrowLoaderS6SettlementProbe.manualHoldDurable(data.ledger().records().get(0).reason()));
            coordinator.tick(20); coordinator.persistTransitions();
            assertFalse(coordinator.writerIdle());
            io.awaitWrites(); coordinator.tick(21);
            assertTrue(coordinator.writerIdle());
            assertTrue(EscrowLoaderS6SettlementProbe.manualHoldDurable(data.ledger().records().get(0).reason()));
        }
    }
    @Test void nativeUnacknowledgedImagesAreNotManualBaselines() throws Exception {
        String replay = System.getenv("ARCE_S6_SETTLEMENT_REPLAY");
        var paths = replay == null ? java.util.List.of(Path.of("tests/fixtures/loader_s6/settlement-seed.json"))
                : java.util.Arrays.stream(replay.split(";")).map(Path::of).toList();
        int count = 0;
        for (var path : paths) {
            var document = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
            var images = new java.util.ArrayList<com.google.gson.JsonObject>();
            if (document.has("starts")) {
                for (var start : document.getAsJsonArray("starts")) { images.add(start.getAsJsonObject().getAsJsonObject("image")); }
            } else { images.add(document); }
            for (var image : images) {
                assertEquals("KNOWN", image.get("read_status").getAsString());
                var records = image.getAsJsonObject("native").getAsJsonObject("ledger").getAsJsonArray("records");
                assertEquals(1, records.size());
                var record = records.get(0).getAsJsonObject();
                assertEquals("PREPARED", record.get("phase").getAsString());
                assertEquals("NONE", record.get("reason").getAsString());
                assertFalse(EscrowLoaderS6SettlementProbe.manualHoldDurable(EscrowReason.valueOf(record.get("reason").getAsString())));
                count++;
            }
        }
        System.out.println("S6 unacknowledged native-image predicate checks=" + count);
    }
    @Test void exactReleaseGateAndNoOtherFamily() {
        for (String side : java.util.List.of("BEFORE", "REPLACED", "ACK")) {
            String cut = "LF_S6_O1_" + side;
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(true, cut + "_OTHER"));
        }
        assertFalse(EscrowLoaderS6SettlementProbe.selected(true, null));
    }
    @Test void cutsDistinguishReplacementFromTheOwnerCallback() {
        String[] cuts = {"LF_S6_O1_BEFORE", "LF_S6_O1_REPLACED", "LF_S6_O1_ACK"};
        String[] hooks = {"K1", "K2", "O1_ACK"};
        for (int i = 0; i < cuts.length; i++) {
            for (int j = 0; j < hooks.length; j++) {
                assertEquals(i == j, EscrowLoaderS6SettlementProbe.boundary(cuts[i], hooks[j]));
            }
            assertFalse(EscrowLoaderS6SettlementProbe.boundary(cuts[i], "K9"));
        }
    }
    @Test void survivorProofAndReceiptCutsRequireTheirOwnPhase() {
        for (String side : java.util.List.of("BEFORE", "AFTER")) {
            for (int index = 0; index < 2; index++) {
                String hook = "O2_" + side + "_" + index, cut = "LF_S6_" + hook;
                assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
                assertTrue(EscrowLoaderS6SettlementProbe.completionBoundary(cut, hook, ""));
                assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary(cut, "O2_" + side + "_" + (1 - index), ""));
            }
        }
        for (String[] row : new String[][] {{"O3_BEFORE", "K1", "O3"}, {"O3_REPLACED", "K2", "O3"},
                {"O3_ACK", "O3_AFTER", "O3"}, {"O4_BEFORE", "K8", "O4"}}) {
            String cut = "LF_S6_" + row[0];
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
            assertTrue(EscrowLoaderS6SettlementProbe.completionBoundary(cut, row[1], row[2]));
            assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary(cut, row[1], "O1"));
            assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary(cut, "unrelated", row[2]));
        }
        assertFalse(EscrowLoaderS6SettlementProbe.selected(true, "LF_S6_O2_BEFORE_2"));
    }
    @Test void actualEmptyRecordWriterUsesTheRetirementHookForO4() throws Exception {
        var initial = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        Path file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        CheckedSavedDataPolicy.atomic(ManagedSavedDataType.RESOURCE_ESCROW).replace(file, () -> EscrowCodec.encode(initial));
        var io = new AwaitedLedgerIo();
        var hooks = new java.util.concurrent.CopyOnWriteArrayList<String>();
        try (var writer = io.writer(file, 1, CheckedSavedDataFile::atomicMove, hooks::add)) {
            var empty = initial.replace(java.util.List.of(), java.util.List.of(), java.util.List.of()).generation(2);
            assertTrue(writer.request(() -> writer.snapshot(empty, java.util.Set.of()), ignored -> { }));
            io.awaitWrites();
            assertEquals(java.util.List.of("K8", "K9"), hooks);
            assertTrue(EscrowLoaderS6SettlementProbe.completionBoundary("LF_S6_O4_BEFORE", hooks.get(0), "O4"));
            assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary("LF_S6_O4_BEFORE", "K1", "O4"));
        }
    }
    @Test void o4ReplacementAndAcknowledgementRemainSeparate() {
        for (String[] row : new String[][] {{"LF_S6_O4_REPLACED", "K9"}, {"LF_S6_O4_ACK", "O4_AFTER"}}) {
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, row[0]));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(false, row[0]));
            assertTrue(EscrowLoaderS6SettlementProbe.completionBoundary(row[0], row[1], "O4"));
            assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary(row[0], row[1], "O3"));
            assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary(row[0], "K2", "O4"));
        }
    }
}

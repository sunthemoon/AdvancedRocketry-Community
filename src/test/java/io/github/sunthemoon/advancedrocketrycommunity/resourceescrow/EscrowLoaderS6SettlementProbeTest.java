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
    @Test void storedBankEditChangesOnlyPrimaryAndItsRequiredProjection() {
        var tag = new net.minecraft.nbt.CompoundTag();
        var data = new net.minecraft.nbt.CompoundTag(); tag.put("RocketEntityData", data);
        var flight = new net.minecraft.nbt.CompoundTag(); data.put("flight_data", flight);
        var fuel = new net.minecraft.nbt.CompoundTag(); flight.put("fuel", fuel);
        var propulsion = new net.minecraft.nbt.CompoundTag(); fuel.put("propulsion", propulsion);
        var primary = new net.minecraft.nbt.CompoundTag(); primary.putLong("amount", 1000); propulsion.put("primary", primary);
        fuel.putLong("amount", 1000); propulsion.putLong("revision", 17); data.putString("identity", "unchanged");
        for (int amount : new int[] {900, 1100}) {
            var changed = tag.copy(); EscrowLoaderS6SettlementProbe.changeStoredBank(changed, amount);
            var expected = tag.copy(); var expectedFuel = expected.getCompound("RocketEntityData").getCompound("flight_data").getCompound("fuel");
            expectedFuel.putLong("amount", amount); expectedFuel.getCompound("propulsion").getCompound("primary").putLong("amount", amount);
            assertEquals(expected, changed); assertNotEquals(tag, changed);
        }
    }
    @Test void accountingVariantsKeepSeparateEditsAndSplitSaveGates() {
        for (String name : java.util.List.of("SUBSTITUTE", "BANK_LOSS", "BANK_GAIN", "PRUNE_SPLIT_TANK", "PRUNE_SPLIT_BANK")) {
            String cut = "LF_S6_AC_" + name;
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(true, cut + "_OTHER"));
            assertEquals(0, EscrowLoaderS6SettlementProbe.tankDelta(cut));
            boolean pruning = name.startsWith("PRUNE");
            assertEquals(!pruning, EscrowLoaderS6SettlementProbe.boundary(cut, "K2"));
            assertFalse(EscrowLoaderS6SettlementProbe.boundary(cut, "K1"));
            assertFalse(EscrowLoaderS6SettlementProbe.boundary(cut, "O1_ACK"));
            assertEquals(pruning, EscrowLoaderS6SettlementProbe.pruned(cut, 0, true, 0, 0));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 0, true, 1, 0));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 0, true, 0, 1));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 1, true, 0, 0));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 0, false, 0, 0));
        }
        assertEquals(-100, EscrowLoaderS6SettlementProbe.bankDelta("LF_S6_AC_BANK_LOSS"));
        assertEquals(100, EscrowLoaderS6SettlementProbe.bankDelta("LF_S6_AC_BANK_GAIN"));
        assertEquals(0, EscrowLoaderS6SettlementProbe.bankDelta("LF_S6_AC_LOSS"));
        var water = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank.of(
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 4000);
        assertEquals(water, io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank.decode(water.encode()));
        assertNotEquals(water, water.withAmount(3900));
    }
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
    @Test void namedJournalCutsUseExactGatesAndAcknowledgementHook() {
        for (String side : java.util.List.of("BEFORE", "REPLACED", "ACK")) {
            String cut = "LF_S6_O3J_" + side;
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(false, cut));
            assertEquals(side.equals("ACK"), EscrowLoaderS6SettlementProbe.completionBoundary(cut, "O3J_AFTER", "O3"));
            assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary(cut, "K2", "O3"));
        }
    }
    @Test void accountingCutsAreGatedAndStopOnlyAtTheirOwnBoundary() {
        // W373C: the pruning cuts, and a known loss and gain of the held loader's tank.
        var added = java.util.List.of("LF_S6_AC_PRUNE_BEFORE", "LF_S6_AC_PRUNE_AFTER", "LF_S6_AC_LOSS", "LF_S6_AC_GAIN");
        for (String cut : added) {
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6SettlementProbe.selected(true, cut + "_OTHER"));
            // None of them stops at a completion checkpoint of another case.
            for (String hook : java.util.List.of("K1", "K2", "K8", "K9", "O2_BEFORE_0", "O2_AFTER_1", "O3_AFTER", "O4_AFTER", "O3J_AFTER")) {
                for (String phase : java.util.List.of("", "O3", "O4")) {
                    assertFalse(EscrowLoaderS6SettlementProbe.completionBoundary(cut, hook, phase), cut + " " + hook);
                }
            }
        }
        assertEquals(-100, EscrowLoaderS6SettlementProbe.tankDelta("LF_S6_AC_LOSS"));
        assertEquals(100, EscrowLoaderS6SettlementProbe.tankDelta("LF_S6_AC_GAIN"));
        for (String cut : java.util.List.of("LF_S6_AC_LOSS", "LF_S6_AC_GAIN")) {
            // The signed outcomes stop after O1's replacement, as LF_S6_O1_REPLACED does, and at no other hook.
            assertTrue(EscrowLoaderS6SettlementProbe.boundary(cut, "K2"));
            assertFalse(EscrowLoaderS6SettlementProbe.boundary(cut, "K1"));
            assertFalse(EscrowLoaderS6SettlementProbe.boundary(cut, "O1_ACK"));
            assertFalse(EscrowLoaderS6SettlementProbe.prunes(cut));
        }
        for (String cut : java.util.List.of("LF_S6_AC_PRUNE_BEFORE", "LF_S6_AC_PRUNE_AFTER")) {
            assertEquals(0, EscrowLoaderS6SettlementProbe.tankDelta(cut));
            for (String hook : java.util.List.of("K1", "K2", "O1_ACK")) { assertFalse(EscrowLoaderS6SettlementProbe.boundary(cut, hook)); }
            assertTrue(EscrowLoaderS6SettlementProbe.pruned(cut, 0, true, 0, 0));
            // A live record, an unreleased receipt, or a marker left on either survivor is not the pruned state.
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 1, true, 0, 0));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 0, false, 0, 0));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 0, true, 1, 0));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 0, true, 0, 1));
        }
        // The sixteen earlier cases keep their boundaries: none changes the tank, prunes, or stops at the pruned state.
        for (String cut : java.util.List.of("LF_S6_O1_BEFORE", "LF_S6_O1_REPLACED", "LF_S6_O1_ACK", "LF_S6_O2_BEFORE_0", "LF_S6_O2_AFTER_0",
                "LF_S6_O2_BEFORE_1", "LF_S6_O2_AFTER_1", "LF_S6_O3_BEFORE", "LF_S6_O3_REPLACED", "LF_S6_O3_ACK", "LF_S6_O4_BEFORE",
                "LF_S6_O4_REPLACED", "LF_S6_O4_ACK", "LF_S6_O3J_BEFORE", "LF_S6_O3J_REPLACED", "LF_S6_O3J_ACK")) {
            assertTrue(EscrowLoaderS6SettlementProbe.selected(true, cut));
            assertEquals(0, EscrowLoaderS6SettlementProbe.tankDelta(cut));
            assertFalse(EscrowLoaderS6SettlementProbe.pruned(cut, 0, true, 0, 0));
        }
        assertEquals(java.util.List.of(false, true, false),
                java.util.List.of("K1", "K2", "O1_ACK").stream().map(hook -> EscrowLoaderS6SettlementProbe.boundary("LF_S6_O1_REPLACED", hook)).toList());
    }
}

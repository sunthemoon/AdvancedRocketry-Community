package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowCargoRecoveryProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void saturatedSeedRetriesThrottledAutosaveAndRequiresCommittedAcknowledgementAndReadback(@TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("escrow.dat");
        var data = ResourceEscrowSavedData.create(); var ledgerIo = new AwaitedLedgerIo();
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        try (var writer = ledgerIo.writer(file, 0, (staged, target) -> {
            writes.incrementAndGet();
            io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.atomicMove(staged, target);
        }); var coordinator = new EscrowCoordinator(data, writer)) {
            coordinator.tick(100); data.setDirty(); coordinator.autosave();
            ledgerIo.awaitWrites(); coordinator.tick(101);
            assertEquals(1, data.ledger().writerGeneration()); assertEquals(1, writes.get());
            var floors = EscrowLoaderFloorProbe.floors(256);
            floors.forEach(floor -> floor.putString("signature_kind", EscrowSignatureKind.ROCKET_COMPOSITE_V1.name()));
            var seeded = new EscrowLedger(data.ledger().id(), 256, 1, java.util.List.of(), floors, java.util.List.of());
            data.acknowledged(seeded, true); coordinator.autosave();
            assertTrue(writer.idle()); assertTrue(data.isDirty()); assertEquals(1, writer.lastGeneration());
            assertEquals(1, writes.get(), "The old fixture's autosave was throttled before any write request, not refused by the writer");
            var baseline = new EscrowCargoRecoveryProbe.FloorBaseline(seeded);
            assertFalse(baseline.advance(false, null, null, null));
            assertFalse(baseline.advance(true, data, coordinator, file));
            assertThrows(IllegalArgumentException.class, () -> baseline.confirm(data, file));
            coordinator.tick(110);
            assertFalse(baseline.advance(true, data, coordinator, file));
            assertFalse(writer.idle()); assertEquals(2, writer.lastGeneration());
            ledgerIo.awaitWrites();
            assertEquals(2, writes.get()); assertEquals(1, data.ledger().writerGeneration());
            assertFalse(baseline.advance(true, data, coordinator, file), "Replacement before its callback is not an acknowledgement");
            coordinator.tick(111);
            assertTrue(baseline.advance(true, data, coordinator, file));
            assertEquals(seeded.generation(2), data.ledger()); assertFalse(data.isDirty());
            var saved = io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.readPayload(file,
                    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow();
            assertEquals(256, EscrowCodec.decode(saved).floors().size());
            assertEquals(EscrowCodec.encode(seeded.generation(2)), saved);
            // A forged RAM acknowledgement cannot stand in for the exact saved baseline.
            var wrong = ResourceEscrowSavedData.load(EscrowCodec.encode(seeded.generation(3)));
            assertThrows(IllegalArgumentException.class, () -> baseline.confirm(wrong, file));
            java.nio.file.Files.delete(file);
            assertThrows(IllegalArgumentException.class, () -> baseline.confirm(data, file), "Acknowledged RAM cannot replace the saved-file readback");
        }
    }
    @Test void recoveryFixturesRequireTheExactCaseAndReleaseProperty() {
        for (String suffix : new String[] {"R1_255", "R1_256", "R2_DUPLICATE", "R3_CURSOR", "R3_OTHER", "R3_RELOCATED", "G1_FUTURE", "G1_INVALID"}) {
            assertTrue(EscrowCargoRecoveryProbe.selected(true, "FC_LOAD_" + suffix));
            assertFalse(EscrowCargoRecoveryProbe.selected(false, "FC_LOAD_" + suffix));
            assertFalse(EscrowCargoRecoveryProbe.selected(true, "IC_LOAD_" + suffix));
            assertFalse(EscrowCargoRecoveryProbe.selected(true, "FC_UNLOAD_" + suffix));
        }
        for (String name : new String[] {null, "", "FC_LOAD_R3", "FC_LOAD_K1"}) {
            assertFalse(EscrowCargoRecoveryProbe.selected(true, name));
        }
    }
    @Test void relocationObservationNeverUsesAnInputPositionOrMovesTheStartFixture() {
        assertEquals(new BlockPos(72, 100, 64), EscrowCargoRecoveryProbe.devicePosition("FC_LOAD_R3_RELOCATED", true));
        assertEquals(new BlockPos(64, 100, 64), EscrowCargoRecoveryProbe.devicePosition("FC_LOAD_R3_RELOCATED", false));
        assertEquals(new BlockPos(64, 100, 64), EscrowCargoRecoveryProbe.devicePosition("FC_LOAD_R3_OTHER", true));
    }
    @Test void recoveryReasonComesFromTheExactTransferFenceNotFlightFailureHistory() {
        var transfer = java.util.UUID.randomUUID();
        String diagnostic = "journal=acknowledged [fenced_transfer=" + transfer + " reason=FLOOR_CONFLICT repair=offline-whole-world]";
        assertTrue(EscrowCargoRecoveryProbe.floorConflict(diagnostic, transfer));
        assertFalse(EscrowCargoRecoveryProbe.floorConflict(diagnostic, java.util.UUID.randomUUID()));
        assertFalse(EscrowCargoRecoveryProbe.floorConflict(diagnostic.replace("FLOOR_CONFLICT", "OTHER_STATE"), transfer));
        assertFalse(EscrowCargoRecoveryProbe.floorConflict("last_failure=FLOOR_CONFLICT", transfer));
    }
}

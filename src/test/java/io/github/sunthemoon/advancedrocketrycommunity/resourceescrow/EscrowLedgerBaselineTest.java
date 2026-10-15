package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowLedgerBaselineTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;
    @Test void onlyAnExactCheckedTargetProvidesTheLoadedReleaseBaseline() throws Exception {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(root));
        byte[] raw = Files.readAllBytes(file);
        try (var writer = new EscrowLedgerWriter(file, 1)) { assertDoesNotThrow(() -> writer.baseline(root)); }
        var changed = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE)).generation(1);
        try (var writer = new EscrowLedgerWriter(file, 1)) { assertThrows(IllegalStateException.class, () -> writer.baseline(changed)); }
        assertArrayEquals(raw, Files.readAllBytes(file));
    }
    @Test void absentOrStagedAuthorityNeverAcknowledgesAReleasedOrPendingLoadedRoot() throws Exception {
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); var empty = EscrowTestFixtures.ledger();
        try (var writer = new EscrowLedgerWriter(file, 0)) { assertDoesNotThrow(() -> writer.baseline(empty)); }
        var pending = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1));
        try (var writer = new EscrowLedgerWriter(file, 0)) { assertThrows(IllegalStateException.class, () -> writer.baseline(pending)); }
        var staged = file.resolveSibling(file.getFileName() + ".arce-pending");
        var outer = new net.minecraft.nbt.CompoundTag(); var loaded = pending.generation(1); outer.put("data", EscrowCodec.encode(loaded));
        net.minecraft.nbt.NbtIo.writeCompressed(outer, staged.toFile()); byte[] raw = Files.readAllBytes(staged);
        try (var writer = new EscrowLedgerWriter(file, 1)) { assertThrows(IllegalStateException.class, () -> writer.baseline(loaded)); }
        assertFalse(Files.exists(file)); assertArrayEquals(raw, Files.readAllBytes(staged));
    }
}

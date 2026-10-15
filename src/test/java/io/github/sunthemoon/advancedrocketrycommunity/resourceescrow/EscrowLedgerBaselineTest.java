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
        assertThrows(IllegalArgumentException.class, () -> EscrowLedgerBaseline.require(file, changed));
        assertArrayEquals(raw, Files.readAllBytes(file));
    }
    @Test void absentOrStagedAuthorityNeverAcknowledgesAReleasedOrPendingLoadedRoot() throws Exception {
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); var empty = EscrowTestFixtures.ledger();
        try (var writer = new EscrowLedgerWriter(file, 0)) { assertDoesNotThrow(() -> writer.baseline(empty)); }
        var pending = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1));
        assertThrows(IllegalArgumentException.class, () -> EscrowLedgerBaseline.require(file, pending));
        var staged = file.resolveSibling(file.getFileName() + ".arce-pending");
        var outer = new net.minecraft.nbt.CompoundTag(); var loaded = pending.generation(1); outer.put("data", EscrowCodec.encode(loaded));
        net.minecraft.nbt.NbtIo.writeCompressed(outer, staged.toFile()); byte[] raw = Files.readAllBytes(staged);
        assertThrows(IllegalArgumentException.class, () -> EscrowLedgerBaseline.require(file, loaded));
        assertFalse(Files.exists(file)); assertArrayEquals(raw, Files.readAllBytes(staged));
    }
    @Test void lateMismatchIsStickyUnavailableAndNeverThrowsThroughTicksOrWrites() throws Exception {
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(root));
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        var changed = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE)).generation(1);
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.RESOURCE_ESCROW, () -> EscrowCodec.encode(changed));
        byte[] raw = Files.readAllBytes(file);
        var lines = new java.util.ArrayList<String>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(EscrowLedgerWriter.class);
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("baseline-test", null, null, true,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) { lines.add(event.getMessage().getFormattedMessage()); }
        };
        appender.start(); logger.addAppender(appender);
        try (var writer = new EscrowLedgerWriter(file, 1); var coordinator = new EscrowCoordinator(data, writer)) {
            data.attach(coordinator, () -> true);
            assertFalse(assertDoesNotThrow(() -> writer.baseline(root)));
            assertTrue(writer.unavailable()); assertFalse(data.operational());
            for (int i = 0; i < 100; i++) {
                assertFalse(writer.baseline(root)); assertDoesNotThrow(() -> coordinator.tick(100));
                assertDoesNotThrow(coordinator::autosave);
                assertTrue(coordinator.logicalLocked(EscrowTestFixtures.id(999)));
            }
            assertFalse(writer.request(() -> { fail("Unavailable baseline cannot capture"); return null; }, ignored -> fail()));
            assertThrows(IllegalArgumentException.class, () -> coordinator.prepareTest(EscrowTestFixtures.operation(2), ignored -> fail()));
        } finally { logger.removeAppender(appender); appender.stop(); }
        assertEquals(1, lines.size()); assertTrue(lines.get(0).startsWith("ARCE_ESCROW_BASELINE_UNAVAILABLE"));
        assertTrue(lines.get(0).length() < 256); assertFalse(lines.get(0).contains("\n"));
        assertArrayEquals(raw, Files.readAllBytes(file));
    }
}

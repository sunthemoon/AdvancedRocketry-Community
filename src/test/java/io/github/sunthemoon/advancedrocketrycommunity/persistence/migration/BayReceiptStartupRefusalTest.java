package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptCodec;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W27c1 A1: the pre-start check refuses a future or invalid receipt root on real files and keeps its bytes. */
final class BayReceiptStartupRefusalTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.BAY_RECEIPTS;

    private static UUID id(long value) { return new UUID(2_702, value); }
    private static BayReceipt receipt(long n) {
        String hash = RocketNativeResources.hash("w27c1-startup");
        return new BayReceipt(id(n), n, id(100 + n), new RocketPosition((int) n, 0, 0), id(200 + n), id(1), "advancedrocketrycommunity:w27c1_data",
                SatelliteKind.DATA, "advancedrocketrycommunity:earth", hash, id(300), hash, null, hash, null, BayReceipt.Phase.INTENT,
                "NONE", false, false, 0, 1, 1, 0);
    }
    private static CompoundTag valid() { return BayReceiptCodec.encode(new BayReceiptRoot(id(9), 2, 4, List.of(receipt(1), receipt(2)))); }
    private Path world(CompoundTag payload) throws Exception {
        Path world = temp.resolve("world-" + System.nanoTime());
        Path file = Files.createDirectories(world.resolve("data")).resolve(TYPE.fileName());
        CheckedSavedDataFile.replace(file, TYPE, () -> payload);
        return world;
    }
    private static Path file(Path world) { return world.resolve("data").resolve(TYPE.fileName()); }

    @Test void aValidRootPassesWithoutAnyWriteOrBackup() throws Exception {
        Path world = world(valid()); byte[] before = Files.readAllBytes(file(world));
        var report = new WorldDataMigrationService().migrate(world);
        assertEquals(MigrationDiagnosticId.DATA_CURRENT, report.diagnosticId()); assertEquals(1, report.managedFileCount());
        assertArrayEquals(before, Files.readAllBytes(file(world)));
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
        assertEquals(BetaWorldMigrationEvents.ESCROW_HINT, BetaWorldMigrationEvents.recoveryHint(world),
                "a world with receipts never rolls back managed files alone");
        assertEquals(BetaWorldMigrationEvents.MIGRATION_HINT, BetaWorldMigrationEvents.recoveryHint(temp.resolve("empty")));
    }

    @Test void aFutureRootRefusesWholeWorldStartupAndKeepsItsBytes() throws Exception {
        CompoundTag future = valid(); future.putInt("schema_version", 2); future.putString("future_field", "kept");
        Path world = world(future); byte[] before = Files.readAllBytes(file(world));
        var refusal = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
        assertEquals(MigrationDiagnosticId.FUTURE_SCHEMA, refusal.diagnosticId()); assertEquals(TYPE, refusal.dataType().orElseThrow());
        assertArrayEquals(before, Files.readAllBytes(file(world)));
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    @Test void everyInvalidRootRefusesWholeWorldStartupAndKeepsItsBytes() throws Exception {
        var mutations = List.<Consumer<CompoundTag>>of(
                root -> root.putString("format_epoch", "v1.8.0-resource-escrow"),
                root -> root.putString("unknown", "x"),
                root -> ((CompoundTag) root.getList("receipts", 10).get(1)).putUUID("op", id(1)),
                root -> ((CompoundTag) root.getList("receipts", 10).get(0)).putString("extra", "x"),
                root -> ((CompoundTag) root.getList("receipts", 10).get(0)).putString("phase", "REGISTERED"),
                root -> root.putLong("issued_through", 1),
                root -> { ListTag list = root.getList("receipts", 10); list.add(list.get(0).copy()); },
                root -> root.remove("store_id"));
        for (var mutation : mutations) {
            CompoundTag invalid = valid(); mutation.accept(invalid);
            Path world = world(invalid); byte[] before = Files.readAllBytes(file(world));
            var refusal = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world), invalid::toString);
            assertEquals(MigrationDiagnosticId.INVALID_SCHEMA, refusal.diagnosticId()); assertEquals(TYPE, refusal.dataType().orElseThrow());
            assertArrayEquals(before, Files.readAllBytes(file(world)));
            assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
        }
    }
}

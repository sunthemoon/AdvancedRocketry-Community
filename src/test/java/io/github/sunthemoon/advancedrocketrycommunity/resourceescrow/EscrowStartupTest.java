package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.WorldDataMigrationService;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowStartupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path world;
    @Test void disabledAdmissionDoesNotCreateALedgerOrMigrationBackup() throws Exception {
        Files.createDirectories(world.resolve("data"));
        assertEquals(0, new WorldDataMigrationService().migrate(world).managedFileCount());
        assertFalse(Files.exists(file())); assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
        assertFalse(ManagedSavedDataType.RESOURCE_ESCROW.hasLegacySchema());
        assertEquals(2, ManagedSavedDataType.RESOURCE_ESCROW.firstEpochSchema());
    }
    @Test void futureAndInvalidRootsRefusePreStartAndPreserveTheirBytes() throws Exception {
        Files.createDirectories(world.resolve("data"));
        for (java.util.function.Consumer<CompoundTag> mutation : List.<java.util.function.Consumer<CompoundTag>>of(
                tag -> tag.putInt("schema_version", 3), tag -> tag.putInt("schema_version", 1), tag -> tag.putInt("schema_version", 0),
                tag -> tag.remove("ledger_id"), tag -> tag.putLong("issued_through", -1),
                tag -> tag.putInt("unexpected", 1))) {
            CompoundTag root = ResourceEscrowSavedData.create().save(new CompoundTag()); mutation.accept(root);
            CompoundTag outer = new CompoundTag(); outer.put("data", root); NbtIo.writeCompressed(outer, file().toFile());
            byte[] before = Files.readAllBytes(file());
            for (int attempt = 0; attempt < 2; attempt++) {
                assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
                assertArrayEquals(before, Files.readAllBytes(file())); assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
            }
        }
    }
    private Path file() { return world.resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()); }
}

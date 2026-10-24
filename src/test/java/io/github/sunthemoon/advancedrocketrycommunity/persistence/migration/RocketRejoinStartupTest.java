package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketRejoinStartupTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_REJOINS;
    private Path file(Path world) throws Exception { return Files.createDirectories(world.resolve("data")).resolve(TYPE.fileName()); }

    @Test void unusedWorldHasNoSidecarAndUsedEmptyRootIsNotRewritten() throws Exception {
        Path file = file(temp); new WorldDataMigrationService().migrate(temp); assertFalse(Files.exists(file));
        var root = RocketRejoinCodec.encode(new RocketRejoinRoot(29, List.of(), List.of()));
        CheckedSavedDataFile.replace(file, TYPE, () -> root); var bytes = Files.readAllBytes(file);
        assertEquals(1, new WorldDataMigrationService().migrate(temp).managedFileCount());
        assertArrayEquals(bytes, Files.readAllBytes(file));
        assertEquals(BetaWorldMigrationEvents.REJOIN_HINT, BetaWorldMigrationEvents.recoveryHint(temp));
        assertFalse(TYPE.hasLegacySchema()); assertEquals(1, TYPE.currentSchemaVersion());
        assertEquals(1_048_576L, TYPE.maxUncompressedBytes()); assertEquals(1_114_112L, TYPE.maxCompressedBytes());
    }

    @Test void malformedFutureOversizedAndUnreadableRootsRefuseTwiceWithoutChangingAnyBytes() throws Exception {
        var mutations = List.<Consumer<CompoundTag>>of(r -> r.putInt("schema_version", 2), r -> r.putInt("unknown", 1),
                r -> r.putLong("next_sequence", -1), r -> r.putByteArray("padding", new byte[1_048_576]));
        for (int n = 0; n < mutations.size() + 1; n++) {
            Path world = Files.createDirectory(temp.resolve("case-" + n)); Path file = file(world);
            if (n == mutations.size()) { Files.write(file, new byte[] {1, 2, 3}); }
            else { var root = RocketRejoinCodec.encode(RocketRejoinRoot.empty()); mutations.get(n).accept(root);
                var outer = new CompoundTag(); outer.put("data", root); net.minecraft.nbt.NbtIo.writeCompressed(outer, file.toFile()); }
            var original = Files.readAllBytes(file);
            for (int attempt = 0; attempt < 2; attempt++) {
                var failure = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
                assertEquals(TYPE, failure.dataType().orElseThrow()); assertArrayEquals(original, Files.readAllBytes(file));
                assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
            }
        }
    }

    @Test void unknownPresenceRefusesBeforeMigrationOrFileCreation() throws Exception {
        file(temp);
        var service = new WorldDataMigrationService(Clock.systemUTC(), (source, target) -> fail("No commit"),
                (path, bytes) -> fail("No staging"), p -> CheckedSavedDataFile.Presence.UNKNOWN);
        var failure = assertThrows(SavedDataMigrationException.class, () -> service.migrate(temp));
        assertEquals(TYPE, failure.dataType().orElseThrow()); assertFalse(Files.exists(file(temp)));
    }

    @Test void historicalTransferRootsStillMigrateToThreeWithoutCreatingSidecar() throws Exception {
        for (int schema : List.of(1, 2)) {
            Path world = Files.createDirectory(temp.resolve("root-" + schema)); file(world);
            var old = new CompoundTag(); old.putInt("schema_version", schema); old.put("transfers", new ListTag());
            if (schema == 2) { old.putString("format_epoch", "v0.9.0-beta"); }
            var transfer = world.resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            CheckedSavedDataFile.replace(transfer, ManagedSavedDataType.ROCKET_TRANSFERS, () -> old);
            assertEquals(1, new WorldDataMigrationService().migrate(world).migratedFileCount());
            var migrated = CheckedSavedDataFile.readPayload(transfer, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
            assertEquals(3, migrated.getInt("schema_version"));
            assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData
                    .emptyFirstMoonLanding(), migrated.getCompound(ManagedSavedDataType.FIRST_MOON_LANDING_KEY));
            assertFalse(Files.exists(file(world))); assertEquals(0, new WorldDataMigrationService().migrate(world).migratedFileCount());
        }
    }

    @Test void structuralPreflightRunsBeforeGenericMigrationCopies() {
        var root = new CompoundTag() {
            @Override public CompoundTag copy() { throw new AssertionError("Unvalidated root was copied"); }
        };
        root.putInt("schema_version", 1); root.putString("format_epoch", "v1.8.0-rocket-rejoins");
        root.putLong("next_sequence", 0); root.put("batches", new ListTag()); root.put("entries", new ListTag());
        // The structural preflight refuses a foreign writer as well as excessive size/depth/node count.
        assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, root));
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Bounded filesystem fault injection, not a native server or hardware power-loss test. */
final class StationWorldMigrationTest {
    private static final ManagedSavedDataType STATIONS = ManagedSavedDataType.STATIONS;
    private static final String BACKUPS = "advancedrocketrycommunity-backups";

    @TempDir
    Path temporary;

    @Test
    void betaStationUpgradePreservesOtherAuthoritiesAndDocumentsEachFileSchema() throws Exception {
        Path world = world("beta");
        Map<ManagedSavedDataType, byte[]> originals = capture(world);
        Path old = file(world, STATIONS).resolveSibling(STATIONS.fileName() + "_old");
        byte[] oldBytes = {1, 3, 5, 7}; // Previous copies are backed up, not mistaken for authorities.
        Files.write(old, oldBytes);
        var report = new WorldDataMigrationService().migrate(world);
        assertEquals(5, report.managedFileCount());
        assertEquals(1, report.migratedFileCount());
        Path backup = world.resolve(BACKUPS).resolve(report.backupDirectory().orElseThrow());
        for (var type : ManagedSavedDataType.values()) {
            assertArrayEquals(originals.get(type), Files.readAllBytes(backup.resolve(type.fileName())));
            if (type != STATIONS) {
                assertArrayEquals(originals.get(type), Files.readAllBytes(file(world, type)));
            }
        }
        assertArrayEquals(oldBytes, Files.readAllBytes(old));
        assertArrayEquals(oldBytes, Files.readAllBytes(backup.resolve(old.getFileName())));
        CompoundTag expected = SavedDataSchemaMigrator.migrate(STATIONS, legacy()).payload();
        assertEquals(expected, NbtIo.readCompressed(file(world, STATIONS).toFile()).getCompound("data"));
        var manifest = JsonParser.parseString(Files.readString(backup.resolve("manifest.json"))).getAsJsonObject();
        assertEquals(2, manifest.get("manifestSchema").getAsInt());
        assertFalse(manifest.has("sourceSchema"));
        assertFalse(manifest.has("targetSchema"));
        assertEquals(6, manifest.getAsJsonArray("files").size());
        for (var entry : manifest.getAsJsonArray("files")) {
            var row = entry.getAsJsonObject();
            String name = row.get("file").getAsString();
            byte[] saved = Files.readAllBytes(backup.resolve(name));
            assertEquals(saved.length, row.get("bytes").getAsLong());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(saved)),
                    row.get("sha256").getAsString());
            if (name.endsWith("_old")) {
                assertEquals("previous-copy", row.get("role").getAsString());
                assertFalse(row.has("sourceSchema"));
                assertFalse(row.has("targetSchema"));
            } else {
                assertEquals("authority", row.get("role").getAsString());
                // v1.6: the satellite registry's current root is 3 (ADR-050 §10); stations migrate 2 -> 4.
                boolean satellites = name.equals(ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
                assertEquals(satellites ? 3 : 2, row.get("sourceSchema").getAsInt());
                assertEquals(name.equals(STATIONS.fileName()) ? 4 : satellites ? 3 : 2,
                        row.get("targetSchema").getAsInt());
            }
        }
        var migrated = capture(world);
        assertEquals(MigrationDiagnosticId.DATA_CURRENT, new WorldDataMigrationService().migrate(world).diagnosticId());
        assertFiles(world, migrated);
        try (var backups = Files.list(world.resolve(BACKUPS))) {
            assertEquals(1, backups.count());
        }
    }

    @Test
    void unsupportedGeometryMixedRecordsFutureAndDuplicateCellsFailBeforeBackup() throws Exception {
        int index = 0;
        for (Consumer<CompoundTag> mutation : java.util.List.<Consumer<CompoundTag>>of(
                root -> root.putInt("schema_version", 4),
                root -> root.putInt("schema_version", 5),
                root -> root.put("warp_energy", new net.minecraft.nbt.ListTag()),
                root -> root.putString("format_epoch", "unexpected"),
                root -> station(root).putInt("schema_version", 2),
                root -> station(root).putIntArray("region", new int[]{-2432, 2688, -1665, 3455}),
                root -> root.getList("stations", Tag.TAG_COMPOUND).add(station(root).copy()),
                root -> {
                    var reservation = root.getList("reservations", Tag.TAG_COMPOUND).getCompound(0);
                    reservation.putInt("cell_x", -2);
                    reservation.putInt("cell_z", 3);
                })) {
            Path world = world("invalid-" + index++);
            CompoundTag bad = legacy();
            mutation.accept(bad);
            write(file(world, STATIONS), bad);
            var originals = capture(world);
            assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
            assertFiles(world, originals);
            assertFalse(Files.exists(world.resolve(BACKUPS)));
        }
    }

    @Test
    void stagingWriteReadbackAndRuntimeFailuresNeverReachCommit() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            Path world = world("staging-" + mode);
            var originals = capture(world);
            AtomicInteger commits = new AtomicInteger();
            int fault = mode;
            var service = new WorldDataMigrationService(Clock.systemUTC(), (staged, target) -> {
                commits.incrementAndGet();
                throw new AssertionError("Commit reached before staging validation");
            }, (staged, bytes) -> {
                Files.write(staged, new byte[]{0, 1, 2});
                if (fault == 0) {
                    throw new IOException("injected partial staging write");
                }
                if (fault == 1) {
                    throw new IllegalStateException("injected runtime staging failure");
                }
            });
            var failure = assertThrows(SavedDataMigrationException.class, () -> service.migrate(world));
            assertEquals(MigrationDiagnosticId.STAGING_FAILED, failure.diagnosticId());
            assertEquals(0, commits.get());
            assertFiles(world, originals);
            assertNoStaged(world);
        }
    }

    @Test
    void unsupportedAtomicMoveHasNoFallbackAndPostMoveRuntimeFailureRollsBack() throws Exception {
        for (boolean moveFirst : new boolean[]{false, true}) {
            Path world = world("commit-" + moveFirst);
            var originals = capture(world);
            AtomicInteger commits = new AtomicInteger();
            var service = new WorldDataMigrationService(Clock.systemUTC(), (staged, target) -> {
                commits.incrementAndGet();
                if (moveFirst) {
                    atomicMove(staged, target);
                    throw new IllegalStateException("injected after replacement");
                }
                throw new AtomicMoveNotSupportedException(staged.toString(), target.toString(), "injected");
            });
            var failure = assertThrows(SavedDataMigrationException.class, () -> service.migrate(world));
            assertEquals(MigrationDiagnosticId.COMMIT_ROLLED_BACK, failure.diagnosticId());
            assertEquals(1, commits.get());
            assertFiles(world, originals);
            assertNoStaged(world);
        }
    }

    @Test
    void interruptedPartialBatchIsValidatedAndCompletedOnNextPreflight() throws Exception {
        Path world = world("interrupted");
        CompoundTag celestial = NbtIo.readCompressed(file(world, ManagedSavedDataType.CELESTIAL).toFile()).getCompound("data");
        celestial.putInt("schema_version", 1);
        celestial.remove("format_epoch");
        write(file(world, ManagedSavedDataType.CELESTIAL), celestial);
        var originals = capture(world);
        AtomicInteger commits = new AtomicInteger();
        var service = new WorldDataMigrationService(Clock.systemUTC(), (staged, target) -> {
            atomicMove(staged, target);
            if (commits.incrementAndGet() == 1) {
                throw new SimulatedProcessCut();
            }
        });
        assertThrows(SimulatedProcessCut.class, () -> service.migrate(world));
        assertEquals(2, NbtIo.readCompressed(file(world, ManagedSavedDataType.CELESTIAL).toFile())
                .getCompound("data").getInt("schema_version"));
        assertArrayEquals(originals.get(STATIONS), Files.readAllBytes(file(world, STATIONS)));
        var report = new WorldDataMigrationService().migrate(world);
        assertEquals(1, report.migratedFileCount());
        assertEquals(SavedDataSchemaMigrator.migrate(STATIONS, legacy()).payload(),
                NbtIo.readCompressed(file(world, STATIONS).toFile()).getCompound("data"));
        assertEquals(MigrationDiagnosticId.DATA_CURRENT, new WorldDataMigrationService().migrate(world).diagnosticId());
    }

    @Test
    void lostBackupDuringRollbackIsReportedAsIncompleteNotSuccess() throws Exception {
        Path world = world("rollback");
        var service = new WorldDataMigrationService(Clock.systemUTC(), (staged, target) -> {
            atomicMove(staged, target);
            try (var backups = Files.list(world.resolve(BACKUPS))) {
                Files.delete(backups.findFirst().orElseThrow().resolve(STATIONS.fileName()));
            }
            throw new IOException("injected concurrent backup loss");
        });
        var failure = assertThrows(SavedDataMigrationException.class, () -> service.migrate(world));
        assertEquals(MigrationDiagnosticId.ROLLBACK_FAILED, failure.diagnosticId());
        assertTrue(failure.getMessage().contains("-managed"));
        assertTrue(failure.getMessage().contains("manually"));
        assertNoStaged(world);
    }

    private Path world(String name) throws Exception {
        Path world = Files.createDirectory(temporary.resolve(name));
        Files.createDirectory(world.resolve("data"));
        for (var entry : PopulatedManagedDataFixture.currentPayloads().entrySet()) {
            write(file(world, entry.getKey()), entry.getKey() == STATIONS ? legacy() : entry.getValue());
        }
        return world;
    }

    private static void write(Path file, CompoundTag payload) throws IOException {
        CompoundTag outer = new CompoundTag();
        outer.put("data", payload.copy());
        outer.putInt("DataVersion", 3465);
        NbtIo.writeCompressed(outer, file.toFile());
    }

    private static Map<ManagedSavedDataType, byte[]> capture(Path world) throws IOException {
        Map<ManagedSavedDataType, byte[]> result = new EnumMap<>(ManagedSavedDataType.class);
        for (var type : ManagedSavedDataType.values()) {
            result.put(type, Files.readAllBytes(file(world, type)));
        }
        return result;
    }

    private static void assertFiles(Path world, Map<ManagedSavedDataType, byte[]> expected) throws IOException {
        for (var type : ManagedSavedDataType.values()) {
            assertArrayEquals(expected.get(type), Files.readAllBytes(file(world, type)), type.name());
        }
    }

    private static void assertNoStaged(Path world) throws IOException {
        try (var files = Files.list(world.resolve("data"))) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".")));
        }
    }

    private static Path file(Path world, ManagedSavedDataType type) {
        return world.resolve("data").resolve(type.fileName());
    }

    private static void atomicMove(Path from, Path to) throws IOException {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private static CompoundTag station(CompoundTag root) {
        return root.getList("stations", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static CompoundTag legacy() throws Exception {
        try (var input = StationWorldMigrationTest.class.getResourceAsStream("/migrations/v150/stations-v2.snbt")) {
            if (input == null) {
                throw new IllegalStateException("Missing synthetic station fixture");
            }
            return TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static final class SimulatedProcessCut extends Error {
    }
}

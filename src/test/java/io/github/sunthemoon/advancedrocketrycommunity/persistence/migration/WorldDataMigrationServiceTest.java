package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldDataMigrationServiceTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-09-03T04:05:06.007Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
    private static final Map<ManagedSavedDataType, String> FIXTURES = fixtures();

    @Test
    void noManagedDataDoesNotCreateBackup(@TempDir Path temporaryDirectory) throws IOException {
        Path world = createWorld(temporaryDirectory);

        WorldDataMigrationService.MigrationReport report = service().migrate(world);

        assertEquals(MigrationDiagnosticId.NO_MANAGED_DATA, report.diagnosticId());
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    @Test
    void currentDataIsIdempotentAndDoesNotCreateBackup(@TempDir Path temporaryDirectory) throws Exception {
        Path world = createWorld(temporaryDirectory);
        CompoundTag current = legacyFixture(ManagedSavedDataType.CELESTIAL);
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.CELESTIAL, current);
        writeSavedData(world, ManagedSavedDataType.CELESTIAL, current);
        byte[] original = Files.readAllBytes(dataFile(world, ManagedSavedDataType.CELESTIAL));

        WorldDataMigrationService.MigrationReport report = service().migrate(world);

        assertEquals(MigrationDiagnosticId.DATA_CURRENT, report.diagnosticId());
        assertEquals(1, report.managedFileCount());
        assertArrayEquals(original, Files.readAllBytes(dataFile(world, ManagedSavedDataType.CELESTIAL)));
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    @Test
    void allAlphaFixturesMigrateAfterByteExactBackup(@TempDir Path temporaryDirectory) throws Exception {
        Path world = createWorld(temporaryDirectory);
        Map<ManagedSavedDataType, byte[]> originals = new EnumMap<>(ManagedSavedDataType.class);
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            writeSavedData(world, type, legacyFixture(type));
            originals.put(type, Files.readAllBytes(dataFile(world, type)));
        }
        Path oldFile = world.resolve("data").resolve(ManagedSavedDataType.CELESTIAL.fileName() + "_old");
        Files.copy(dataFile(world, ManagedSavedDataType.CELESTIAL), oldFile);
        byte[] oldOriginal = Files.readAllBytes(oldFile);

        WorldDataMigrationService.MigrationReport report = service().migrate(world);

        assertEquals(MigrationDiagnosticId.MIGRATION_COMPLETE, report.diagnosticId());
        assertEquals(ManagedSavedDataType.values().length, report.managedFileCount());
        assertEquals(ManagedSavedDataType.values().length, report.migratedFileCount());
        Path backup = world.resolve("advancedrocketrycommunity-backups")
                .resolve(report.backupDirectory().orElseThrow());
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            assertArrayEquals(originals.get(type), Files.readAllBytes(backup.resolve(type.fileName())));
            CompoundTag migrated = readPayload(dataFile(world, type));
            assertEquals(SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION, migrated.getInt("schema_version"));
            assertEquals(SavedDataSchemaMigrator.FORMAT_EPOCH, migrated.getString("format_epoch"));
            assertEquals(SavedDataSchemaMigrator.LEGACY_SCHEMA_VERSION, migrated.getInt("migrated_from_schema"));
        }
        assertArrayEquals(oldOriginal, Files.readAllBytes(backup.resolve(oldFile.getFileName())));

        JsonObject manifest = JsonParser.parseString(
                Files.readString(backup.resolve("manifest.json"), StandardCharsets.UTF_8)
        ).getAsJsonObject();
        assertEquals(1, manifest.get("manifestSchema").getAsInt());
        assertEquals(FIXED_TIME.toString(), manifest.get("createdAt").getAsString());
        assertEquals(ManagedSavedDataType.values().length + 1, manifest.getAsJsonArray("files").size());
        assertFalse(Files.readString(backup.resolve("manifest.json")).contains(world.toString()));

        WorldDataMigrationService.MigrationReport second = service().migrate(world);
        assertEquals(MigrationDiagnosticId.DATA_CURRENT, second.diagnosticId());
        assertEquals(1L, backupDirectoryCount(world));
    }

    @Test
    void failedSecondCommitRestoresEveryOriginal(@TempDir Path temporaryDirectory) throws Exception {
        Path world = createWorld(temporaryDirectory);
        ManagedSavedDataType first = ManagedSavedDataType.CELESTIAL;
        ManagedSavedDataType second = ManagedSavedDataType.ROCKET_TRANSACTIONS;
        writeSavedData(world, first, legacyFixture(first));
        writeSavedData(world, second, legacyFixture(second));
        byte[] firstOriginal = Files.readAllBytes(dataFile(world, first));
        byte[] secondOriginal = Files.readAllBytes(dataFile(world, second));
        AtomicInteger commits = new AtomicInteger();
        WorldDataMigrationService migrations = new WorldDataMigrationService(CLOCK, (staged, target) -> {
            if (commits.incrementAndGet() == 2) {
                throw new IOException("injected second commit failure");
            }
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        });

        SavedDataMigrationException exception = assertThrows(
                SavedDataMigrationException.class,
                () -> migrations.migrate(world)
        );

        assertEquals(MigrationDiagnosticId.COMMIT_ROLLED_BACK, exception.diagnosticId());
        assertArrayEquals(firstOriginal, Files.readAllBytes(dataFile(world, first)));
        assertArrayEquals(secondOriginal, Files.readAllBytes(dataFile(world, second)));
        assertEquals(1L, backupDirectoryCount(world));
        try (var children = Files.list(world.resolve("data"))) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().contains(".migrating-")));
        }
    }

    @Test
    void futureAndMalformedSchemasFailBeforeBackupOrWrite(@TempDir Path temporaryDirectory) throws Exception {
        Path world = createWorld(temporaryDirectory);
        ManagedSavedDataType type = ManagedSavedDataType.CELESTIAL;
        CompoundTag future = legacyFixture(type);
        future.putInt("schema_version", SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION + 1);
        writeSavedData(world, type, future);
        byte[] original = Files.readAllBytes(dataFile(world, type));

        SavedDataMigrationException futureFailure = assertThrows(
                SavedDataMigrationException.class,
                () -> service().migrate(world)
        );
        assertEquals(MigrationDiagnosticId.FUTURE_SCHEMA, futureFailure.diagnosticId());
        assertArrayEquals(original, Files.readAllBytes(dataFile(world, type)));
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));

        CompoundTag malformed = legacyFixture(type);
        malformed.remove("schema_version");
        writeSavedData(world, type, malformed);
        byte[] malformedOriginal = Files.readAllBytes(dataFile(world, type));
        SavedDataMigrationException malformedFailure = assertThrows(
                SavedDataMigrationException.class,
                () -> service().migrate(world)
        );
        assertEquals(MigrationDiagnosticId.INVALID_SCHEMA, malformedFailure.diagnosticId());
        assertArrayEquals(malformedOriginal, Files.readAllBytes(dataFile(world, type)));
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));

        CompoundTag semanticFailure = legacyFixture(type);
        CompoundTag invalidBody = new CompoundTag();
        invalidBody.putString("id", "advancedrocketrycommunity:moon");
        invalidBody.putLong("discovered_at", -1L);
        semanticFailure.getList("bodies", CompoundTag.TAG_COMPOUND).add(invalidBody);
        writeSavedData(world, type, semanticFailure);
        SavedDataMigrationException semanticException = assertThrows(
                SavedDataMigrationException.class,
                () -> service().migrate(world)
        );
        assertEquals(MigrationDiagnosticId.INVALID_SCHEMA, semanticException.diagnosticId());
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    @Test
    void compressedAndExpandedSizeLimitsFailClosed(@TempDir Path temporaryDirectory) throws Exception {
        Path world = createWorld(temporaryDirectory);
        ManagedSavedDataType type = ManagedSavedDataType.CELESTIAL;
        byte[] oversizedFile = new byte[(int) type.maxCompressedBytes() + 1];
        Files.write(dataFile(world, type), oversizedFile);

        SavedDataMigrationException compressedFailure = assertThrows(
                SavedDataMigrationException.class,
                () -> service().migrate(world)
        );
        assertEquals(MigrationDiagnosticId.OVERSIZED_DATA, compressedFailure.diagnosticId());

        CompoundTag expanded = legacyFixture(type);
        expanded.put("compression_bomb", new ByteArrayTag(new byte[(int) type.maxCompressedBytes() + 1]));
        writeSavedData(world, type, expanded);
        SavedDataMigrationException expandedFailure = assertThrows(
                SavedDataMigrationException.class,
                () -> service().migrate(world)
        );
        assertEquals(MigrationDiagnosticId.OVERSIZED_DATA, expandedFailure.diagnosticId());
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    @Test
    void backupLimitBlocksBeforeAuthoritativeWrite(@TempDir Path temporaryDirectory) throws Exception {
        Path world = createWorld(temporaryDirectory);
        ManagedSavedDataType type = ManagedSavedDataType.CELESTIAL;
        writeSavedData(world, type, legacyFixture(type));
        byte[] original = Files.readAllBytes(dataFile(world, type));
        Path backups = Files.createDirectory(world.resolve("advancedrocketrycommunity-backups"));
        for (int index = 0; index < WorldDataMigrationService.MAX_BACKUPS; index++) {
            Files.createDirectory(backups.resolve("existing-" + index));
        }

        SavedDataMigrationException exception = assertThrows(
                SavedDataMigrationException.class,
                () -> service().migrate(world)
        );

        assertEquals(MigrationDiagnosticId.BACKUP_LIMIT, exception.diagnosticId());
        assertArrayEquals(original, Files.readAllBytes(dataFile(world, type)));
        assertEquals(WorldDataMigrationService.MAX_BACKUPS, backupDirectoryCount(world));
    }

    @Test
    void symbolicManagedFileIsRejectedWhenPlatformAllowsLinks(@TempDir Path temporaryDirectory) throws Exception {
        Path world = createWorld(temporaryDirectory);
        ManagedSavedDataType type = ManagedSavedDataType.CELESTIAL;
        Path outside = temporaryDirectory.resolve("outside.dat");
        writeOuter(outside, legacyFixture(type));
        try {
            Files.createSymbolicLink(dataFile(world, type), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
        }

        SavedDataMigrationException exception = assertThrows(
                SavedDataMigrationException.class,
                () -> service().migrate(world)
        );
        assertEquals(MigrationDiagnosticId.UNSAFE_PATH, exception.diagnosticId());
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    private static WorldDataMigrationService service() {
        return new WorldDataMigrationService(CLOCK, (staged, target) -> Files.move(
                staged,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
        ));
    }

    private static Path createWorld(Path temporaryDirectory) throws IOException {
        Path world = Files.createDirectory(temporaryDirectory.resolve("world"));
        Files.createDirectory(world.resolve("data"));
        return world;
    }

    private static void writeSavedData(
            Path world,
            ManagedSavedDataType type,
            CompoundTag payload
    ) throws IOException {
        writeOuter(dataFile(world, type), payload);
    }

    private static void writeOuter(Path path, CompoundTag payload) throws IOException {
        CompoundTag outer = new CompoundTag();
        outer.put("data", payload.copy());
        outer.putInt("DataVersion", 3465);
        NbtIo.writeCompressed(outer, path.toFile());
    }

    private static CompoundTag readPayload(Path path) throws IOException {
        return NbtIo.readCompressed(path.toFile()).getCompound("data");
    }

    private static Path dataFile(Path world, ManagedSavedDataType type) {
        return world.resolve("data").resolve(type.fileName());
    }

    private static CompoundTag legacyFixture(ManagedSavedDataType type) throws Exception {
        String resource = FIXTURES.get(type);
        try (InputStream input = WorldDataMigrationServiceTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing fixture " + resource);
            }
            return TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static long backupDirectoryCount(Path world) throws IOException {
        Path backups = world.resolve("advancedrocketrycommunity-backups");
        if (!Files.exists(backups)) {
            return 0L;
        }
        try (var children = Files.list(backups)) {
            return children.filter(Files::isDirectory).count();
        }
    }

    private static Map<ManagedSavedDataType, String> fixtures() {
        Map<ManagedSavedDataType, String> fixtures = new EnumMap<>(ManagedSavedDataType.class);
        fixtures.put(ManagedSavedDataType.CELESTIAL, "/migrations/v090/v030-celestial-v1.snbt");
        fixtures.put(ManagedSavedDataType.ROCKET_TRANSACTIONS, "/migrations/v090/v050-rocket-transactions-v1.snbt");
        fixtures.put(ManagedSavedDataType.ROCKET_TRANSFERS, "/migrations/v090/v060-rocket-transfers-v1.snbt");
        fixtures.put(ManagedSavedDataType.STATIONS, "/migrations/v090/v070-stations-v1.snbt");
        fixtures.put(ManagedSavedDataType.SATELLITE_MISSIONS, "/migrations/v090/v080-satellite-missions-v1.snbt");
        return Map.copyOf(fixtures);
    }
}

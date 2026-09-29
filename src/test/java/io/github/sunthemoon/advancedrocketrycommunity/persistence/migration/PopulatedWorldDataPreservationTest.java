package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class PopulatedWorldDataPreservationTest {
    @TempDir
    Path temporary;

    @Test
    void populatedCurrentStateSurvivesRepeatedPreflightWithoutDiskWrites() throws Exception {
        Map<ManagedSavedDataType, CompoundTag> payloads = PopulatedManagedDataFixture.currentPayloads();
        Path world = writeWorld(payloads);
        Map<ManagedSavedDataType, byte[]> originals = capture(world);

        for (int restart = 0; restart < 3; restart++) {
            WorldDataMigrationService.MigrationReport report = new WorldDataMigrationService().migrate(world);
            assertEquals(MigrationDiagnosticId.DATA_CURRENT, report.diagnosticId());
            assertEquals(5, report.managedFileCount());
            assertEquals(0, report.migratedFileCount());
            assertTrue(report.backupDirectory().isEmpty());
            assertFilesEqual(world, originals);
            for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
                CompoundTag restored = PopulatedManagedDataFixture.load(type, read(world, type))
                        .save(new CompoundTag());
                assertEquals(payloads.get(type), restored, type + " authority changed on reload");
            }
        }
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    @Test
    void populatedLegacyMigrationChangesOnlySchemaMetadataAndKeepsExactBackups() throws Exception {
        Map<ManagedSavedDataType, CompoundTag> current = PopulatedManagedDataFixture.currentPayloads();
        Path world = writeWorld(legacyPayloads());
        Map<ManagedSavedDataType, byte[]> originals = capture(world);

        WorldDataMigrationService.MigrationReport report = new WorldDataMigrationService().migrate(world);

        assertEquals(5, report.migratedFileCount());
        Path backup = world.resolve("advancedrocketrycommunity-backups")
                .resolve(report.backupDirectory().orElseThrow());
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            assertArrayEquals(originals.get(type), Files.readAllBytes(backup.resolve(type.fileName())));
            CompoundTag expected = current.get(type).copy();
            expected.putInt("migrated_from_schema", 1);
            assertEquals(expected, read(world, type), type + " migration changed authority state");
            assertEquals(current.get(type), PopulatedManagedDataFixture.load(type, read(world, type))
                    .save(new CompoundTag()));
        }
        Map<ManagedSavedDataType, byte[]> migrated = capture(world);
        assertEquals(0, new WorldDataMigrationService().migrate(world).migratedFileCount());
        assertFilesEqual(world, migrated);
    }

    @ParameterizedTest
    @EnumSource(ManagedSavedDataType.class)
    void everyPartialCommitFailureRestoresAllPopulatedAuthorities(ManagedSavedDataType failedType) throws Exception {
        Path world = writeWorld(legacyPayloads());
        Map<ManagedSavedDataType, byte[]> originals = capture(world);
        AtomicInteger calls = new AtomicInteger();
        WorldDataMigrationService service = new WorldDataMigrationService(Clock.systemUTC(), (staged, target) -> {
            if (calls.incrementAndGet() == failedType.ordinal() + 1) {
                throw new IOException("injected commit failure: " + failedType);
            }
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        });

        SavedDataMigrationException failure = assertThrows(SavedDataMigrationException.class, () -> service.migrate(world));

        assertEquals(MigrationDiagnosticId.COMMIT_ROLLED_BACK, failure.diagnosticId());
        assertEquals(failedType.ordinal() + 1, calls.get());
        assertFilesEqual(world, originals);
        try (var children = Files.list(world.resolve("data"))) {
            assertFalse(children.anyMatch(path -> path.getFileName().toString().contains(".migrating-")));
        }
        assertEquals(5, new WorldDataMigrationService().migrate(world).migratedFileCount());
    }

    @ParameterizedTest
    @EnumSource(ManagedSavedDataType.class)
    void oneFutureRootBlocksTheWholePopulatedUpgradeBeforeAnyWrite(ManagedSavedDataType futureType) throws Exception {
        Map<ManagedSavedDataType, CompoundTag> payloads = legacyPayloads();
        payloads.get(futureType).putInt("schema_version", futureType.currentSchemaVersion() + 1);
        assertBlockedWithoutWrites(payloads, MigrationDiagnosticId.FUTURE_SCHEMA);
    }

    @ParameterizedTest
    @EnumSource(ManagedSavedDataType.class)
    void malformedAuthorityElementCannotBeReplacedWithAnEmptyCollection(ManagedSavedDataType type) throws Exception {
        Map<ManagedSavedDataType, CompoundTag> payloads = legacyPayloads();
        String field = switch (type) {
            case CELESTIAL -> "bodies";
            case ROCKET_TRANSACTIONS -> "transactions";
            case ROCKET_TRANSFERS -> "transfers";
            case STATIONS -> "stations";
            case SATELLITE_MISSIONS -> "missions";
        };
        ListTag malformed = new ListTag();
        malformed.add(IntTag.valueOf(7));
        payloads.get(type).put(field, malformed);
        assertBlockedWithoutWrites(payloads, MigrationDiagnosticId.INVALID_SCHEMA);
    }

    @Test
    void pendingResearchAndActiveMissionKeepTheirBalanceAndDeadline() throws Exception {
        Path world = writeWorld(PopulatedManagedDataFixture.currentPayloads());
        new WorldDataMigrationService().migrate(world);
        SatelliteMissionSavedData data = SatelliteMissionSavedData.load(read(world, ManagedSavedDataType.SATELLITE_MISSIONS));
        assertEquals(MissionStatus.CLAIM_PENDING_DISCOVERY,
                data.mission(PopulatedManagedDataFixture.PENDING_MISSION).orElseThrow().status());
        assertEquals(20, data.account(PopulatedManagedDataFixture.OWNER).balance());
        assertEquals(SatelliteOperationCode.PENDING_DISCOVERY,
                data.claim(PopulatedManagedDataFixture.PENDING_MISSION, PopulatedManagedDataFixture.OWNER, 1_202L).code());
        assertEquals(20, data.account(PopulatedManagedDataFixture.OWNER).balance());
        assertEquals(0, data.completeDue(1_400L).completed());
        assertEquals(1, data.completeDue(1_401L).completed());
        assertEquals(MissionStatus.READY,
                data.mission(PopulatedManagedDataFixture.ACTIVE_MISSION).orElseThrow().status());
        assertEquals(0, SatelliteMissionSavedData.load(data.save(new CompoundTag())).completeDue(1_401L).completed());
    }

    private void assertBlockedWithoutWrites(
            Map<ManagedSavedDataType, CompoundTag> payloads, MigrationDiagnosticId expected
    ) throws Exception {
        Path world = writeWorld(payloads);
        Map<ManagedSavedDataType, byte[]> originals = capture(world);
        SavedDataMigrationException failure = assertThrows(
                SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world)
        );
        assertEquals(expected, failure.diagnosticId());
        assertFilesEqual(world, originals);
        assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
    }

    private Path writeWorld(Map<ManagedSavedDataType, CompoundTag> payloads) throws IOException {
        Path world = Files.createDirectory(temporary.resolve("world"));
        Files.createDirectory(world.resolve("data"));
        for (Map.Entry<ManagedSavedDataType, CompoundTag> entry : payloads.entrySet()) {
            CompoundTag outer = new CompoundTag();
            outer.putInt("DataVersion", 3465);
            outer.put("data", entry.getValue().copy());
            NbtIo.writeCompressed(outer, world.resolve("data").resolve(entry.getKey().fileName()).toFile());
        }
        return world;
    }

    private static Map<ManagedSavedDataType, CompoundTag> legacyPayloads() {
        Map<ManagedSavedDataType, CompoundTag> payloads = PopulatedManagedDataFixture.currentPayloads();
        payloads.values().forEach(payload -> {
            payload.putInt("schema_version", 1);
            payload.remove("format_epoch");
        });
        payloads.get(ManagedSavedDataType.STATIONS).getList("stations", CompoundTag.TAG_COMPOUND)
                .forEach(record -> ((CompoundTag) record).putInt("schema_version", 1));
        return payloads;
    }

    private static CompoundTag read(Path world, ManagedSavedDataType type) throws IOException {
        return NbtIo.readCompressed(world.resolve("data").resolve(type.fileName()).toFile()).getCompound("data");
    }

    private static Map<ManagedSavedDataType, byte[]> capture(Path world) throws IOException {
        Map<ManagedSavedDataType, byte[]> bytes = new EnumMap<>(ManagedSavedDataType.class);
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            bytes.put(type, Files.readAllBytes(world.resolve("data").resolve(type.fileName())));
        }
        return bytes;
    }

    private static void assertFilesEqual(Path world, Map<ManagedSavedDataType, byte[]> expected) throws IOException {
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            assertArrayEquals(expected.get(type), Files.readAllBytes(world.resolve("data").resolve(type.fileName())), type.name());
        }
    }
}

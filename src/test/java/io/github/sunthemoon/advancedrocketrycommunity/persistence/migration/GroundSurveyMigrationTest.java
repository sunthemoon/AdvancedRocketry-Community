package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceOrigin;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class GroundSurveyMigrationTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(ints = {1, 2, 3})
    void realMigratorAndPrestartServiceBackupOnceThenNeverWriteAgain(int schema) throws Exception {
        CompoundTag source = fixture(schema);
        CompoundTag original = source.copy();
        var migration = SavedDataSchemaMigrator.migrate(TYPE, source);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, migration.status());
        assertEquals(original, source, "migration must be detached");
        CompoundTag result = migration.payload();
        assertEquals(4, result.getInt("schema_version"));
        assertEquals("v1.8.0-ground-surveys", result.getString("format_epoch"));
        assertTrue(result.getList("ground_surveys", Tag.TAG_COMPOUND).isEmpty());
        assertEquals(schema == 3 ? 19 : 1, result.getLong("save_epoch"));
        if (schema == 3) {
            ListTag before = source.getList("instances", Tag.TAG_COMPOUND);
            ListTag after = result.getList("instances", Tag.TAG_COMPOUND);
            assertEquals(7, after.size());
            for (int index = 0; index < before.size(); index++) {
                CompoundTag projected = after.getCompound(index).copy();
                assertEquals("survey_mission", projected.getString("origin_kind"));
                projected.putUUID("source_mission", projected.getUUID("origin_id"));
                projected.remove("origin_id"); projected.remove("origin_kind"); projected.putInt("schema_version", 1);
                assertEquals(before.getCompound(index), projected);
            }
            for (String key : List.of("satellites", "missions", "research_accounts", "clock")) {
                assertEquals(source.get(key), result.get(key), key);
            }
            var loaded = SatelliteMissionSavedData.load(result);
            assertTrue(loaded.operational());
            assertFalse(loaded.flushPending());
            assertEquals(InstanceState.AVAILABLE, loaded.instance(id(1_002)).orElseThrow().state());
            assertEquals(InstanceState.ALLOCATED, loaded.instance(id(1_003)).orElseThrow().state());
            assertEquals(InstanceOrigin.survey(id(502)), loaded.instance(id(1_002)).orElseThrow().origin());
            assertEquals(77, loaded.account(OWNER).balance());
        }
        Path world = temporary.resolve("world" + schema);
        Files.createDirectories(world.resolve("data"));
        Path file = world.resolve("data").resolve(TYPE.fileName());
        write(file, source);
        byte[] before = Files.readAllBytes(file);
        var service = new WorldDataMigrationService();
        var report = service.migrate(world);
        assertEquals(1, report.migratedFileCount());
        assertArrayEquals(before, Files.readAllBytes(world.resolve("advancedrocketrycommunity-backups")
                .resolve(report.backupDirectory().orElseThrow()).resolve(TYPE.fileName())));
        assertEquals(result, CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        byte[] migrated = Files.readAllBytes(file);
        var time = Files.getLastModifiedTime(file);
        var again = service.migrate(world);
        assertEquals(0, again.migratedFileCount());
        assertTrue(again.backupDirectory().isEmpty());
        assertArrayEquals(migrated, Files.readAllBytes(file));
        assertEquals(time, Files.getLastModifiedTime(file));
        try (var backups = Files.list(world.resolve("advancedrocketrycommunity-backups"))) {
            assertEquals(1, backups.count());
        }
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT, SavedDataSchemaMigrator.migrate(TYPE, result).status());
    }

    @Test void historicalSectionsAndEpochAreCheckedIndependently() throws Exception {
        for (int schema : List.of(1, 2)) {
            for (String key : List.of("instances", "save_epoch", "ground_surveys")) {
                CompoundTag source = fixture(schema);
                if (key.equals("save_epoch")) { source.putLong(key, 1); } else { source.put(key, new ListTag()); }
                assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
            }
        }
        for (String change : List.of("epoch", "instances", "save_epoch", "ground_surveys")) {
            CompoundTag source = schema3();
            if (change.equals("epoch")) { source.putString("format_epoch", "v0.9.0-beta"); }
            else if (change.equals("ground_surveys")) { source.put(change, new ListTag()); }
            else { source.remove(change); }
            assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
        }
    }

    @Test void futureAndMalformedMigrationLeaveOriginalFileAndMakeNoBackup() throws Exception {
        for (String kind : List.of("root_future", "instance_future", "tagged_legacy", "extra_instance", "missing_source")) {
            CompoundTag source = schema3();
            CompoundTag instance = source.getList("instances", Tag.TAG_COMPOUND).getCompound(0);
            switch (kind) {
                case "root_future" -> source.putInt("schema_version", 5);
                case "instance_future" -> instance.putInt("schema_version", 3);
                case "tagged_legacy" -> instance.putUUID("origin_id", id(999));
                case "extra_instance" -> instance.putString("extra", "kept");
                case "missing_source" -> instance.remove("source_mission");
                default -> throw new AssertionError(kind);
            }
            Path world = temporary.resolve(kind); Files.createDirectories(world.resolve("data"));
            Path file = world.resolve("data").resolve(TYPE.fileName()); write(file, source);
            byte[] before = Files.readAllBytes(file);
            var exception = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
            assertEquals(kind.equals("root_future") ? MigrationDiagnosticId.FUTURE_SCHEMA
                    : MigrationDiagnosticId.INVALID_SCHEMA, exception.diagnosticId());
            assertArrayEquals(before, Files.readAllBytes(file));
            assertFalse(Files.exists(world.resolve("advancedrocketrycommunity-backups")));
            var blocked = SatelliteMissionSavedData.load(source);
            assertFalse(blocked.operational());
            assertEquals(source, blocked.save(new CompoundTag()));
        }
    }

    @Test void exactRootBoundAndGrowthOverflowAreValidatedWithTheDiskReadQuota() {
        CompoundTag source = schema3();
        var upgrade = SatelliteRegistryPayload.upgradeWithSnapshot(source, 3);
        assertEquals(7, upgrade.snapshot().instanceCount());
        assertEquals(25 * 7, upgrade.snapshot().instanceReservationAfter() - upgrade.snapshot().instanceReservationBefore());
        for (int delta : List.of(0, 1)) {
            CompoundTag padded = source.copy(); padded.putByteArray("padding", new byte[0]);
            CompoundTag projected = SatelliteRegistryPayload.upgrade(padded, 3);
            int projectedBytes = size(projected);
            padded.putByteArray("padding", new byte[SatelliteLimits.MAX_REGISTRY_NBT_BYTES + delta - projectedBytes]);
            assertTrue(size(padded) <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
            if (delta == 0) {
                CompoundTag result = SavedDataSchemaMigrator.migrate(TYPE, padded).payload();
                assertEquals(SatelliteLimits.MAX_REGISTRY_NBT_BYTES, size(result));
                BoundedSavedDataIo.validatePayload(result, TYPE);
            } else {
                assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, padded));
            }
        }
    }

    @Test void migratedPayloadIsNotTheRepairedDecoderOutput() {
        CompoundTag source = schema3();
        source.getList("instances", Tag.TAG_COMPOUND).getCompound(0).putUUID("source_mission", id(999));
        CompoundTag migrated = SavedDataSchemaMigrator.migrate(TYPE, source).payload();
        assertEquals("pending", migrated.getList("instances", Tag.TAG_COMPOUND).getCompound(0).getString("state"));
        assertEquals(InstanceState.QUARANTINED, SatelliteMissionSavedData.load(migrated).instance(id(1_000)).orElseThrow().state());
    }

    @Test void exportsDeterministicPreW21bNativeFixture() throws Exception {
        Path directory = Path.of("build", "w21b-fixtures"); Files.createDirectories(directory);
        CompoundTag source = schema3();
        Files.writeString(directory.resolve("satellite-root3.snbt"), source.toString() + "\n");
        write(directory.resolve(TYPE.fileName()), source);
        assertEquals(19, CheckedSavedDataFile.readPayload(directory.resolve(TYPE.fileName()), TYPE).orElseThrow().getLong("save_epoch"));
    }

    private static CompoundTag fixture(int schema) throws Exception {
        try (var stream = GroundSurveyMigrationTest.class.getResourceAsStream("/migrations/v180/satellite-root" + schema + ".snbt")) {
            assertNotNull(stream);
            CompoundTag fixture = TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            if (schema == 3) { assertEquals(schema3(), fixture); }
            return fixture;
        }
    }

    private static int size(CompoundTag tag) {
        try {
            var output = new java.io.ByteArrayOutputStream();
            NbtIo.write(tag, new java.io.DataOutputStream(output)); return output.size();
        } catch (java.io.IOException exception) { throw new AssertionError(exception); }
    }

    private static void write(Path file, CompoundTag payload) throws Exception {
        CompoundTag outer = new CompoundTag(); outer.put("data", payload); outer.putInt("DataVersion", 3465);
        Files.write(file, BoundedSavedDataIo.write(outer, TYPE));
    }
}

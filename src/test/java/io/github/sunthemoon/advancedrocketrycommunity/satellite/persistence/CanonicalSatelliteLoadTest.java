package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CanonicalSatelliteLoadTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path world;

    @Test void decoderAcceptedRecordCannotBecomeAnOperationalUnsavableRegistry() {
        CompoundTag raw = CanonicalSatelliteFixtures.recordRoot(4090, 1);
        assertDoesNotThrow(() -> SatelliteNbtCodec.decodeMission(raw.getList("missions", Tag.TAG_COMPOUND).getCompound(0)));
        var failure = assertThrows(RuntimeException.class, () -> SatelliteRegistryPayload.decodeCurrent(raw));
        assertRecordDiagnostic(failure.getMessage());
        var data = SatelliteMissionSavedData.load(raw);
        assertFalse(data.operational());
        assertFalse(data.isDirty());
        assertEquals(raw, data.save(new CompoundTag()));
    }

    @Test void startupRefusesRecordWithoutTouchingAnyFile() throws Exception {
        Path source = write(CanonicalSatelliteFixtures.recordRoot(4090, 1));
        byte[] before = Files.readAllBytes(source);
        Path neighbor = source.resolveSibling("unmanaged.dat");
        Files.writeString(neighbor, "untouched neighbor");
        var failure = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
        assertEquals(ManagedSavedDataType.SATELLITE_MISSIONS, failure.dataType().orElseThrow());
        assertRecordDiagnostic(failure.getMessage());
        assertArrayEquals(before, Files.readAllBytes(source));
        assertEquals("untouched neighbor", Files.readString(neighbor));
        try (var files = Files.list(world)) { assertEquals(1, files.count()); }
        try (var files = Files.list(source.getParent())) { assertEquals(2, files.count()); }
    }

    @Test void migrationResultGetsTheSameRefusalBeforeAnyWrites() throws Exception {
        Path source = write(CanonicalSatelliteFixtures.legacy(CanonicalSatelliteFixtures.recordRoot(4090, 1)));
        byte[] before = Files.readAllBytes(source);
        var failure = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
        assertRecordDiagnostic(failure.getMessage());
        assertArrayEquals(before, Files.readAllBytes(source));
        try (var files = Files.list(world)) { assertEquals(1, files.count()); }
    }

    @Test void canonicalRecordAtItsInclusiveBoundStillLoads() {
        CompoundTag raw = CanonicalSatelliteFixtures.recordRoot(3906, 1);
        var registry = SatelliteRegistryPayload.decodeCurrent(raw);
        assertFalse(registry.restoreReport().changed());
        assertEquals(4096, SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(registry.missions().get(0))));
        assertTrue(SatelliteMissionSavedData.load(raw).operational());
    }

    @Test void canonicalRootAtItsInclusiveBoundStillLoads() {
        CompoundTag raw = CanonicalSatelliteFixtures.exactRoot(0);
        assertEquals(SatelliteLimits.MAX_REGISTRY_NBT_BYTES, SatelliteNbtSize.uncompressedBytes(raw));
        var data = SatelliteMissionSavedData.load(raw);
        assertTrue(data.operational());
        assertEquals(raw, data.save(new CompoundTag()));
    }

    @Test void everyRecordFitsButCanonicalRootExceedsItsBound() throws Exception {
        CompoundTag raw = CanonicalSatelliteFixtures.exactRoot(10);
        assertEquals(SatelliteLimits.MAX_REGISTRY_NBT_BYTES, SatelliteNbtSize.uncompressedBytes(raw));
        for (Tag mission : raw.getList("missions", Tag.TAG_COMPOUND)) {
            assertDoesNotThrow(() -> SatelliteNbtCodec.encodeMission(SatelliteNbtCodec.decodeMission((CompoundTag) mission)));
        }
        Path source = write(raw);
        byte[] before = Files.readAllBytes(source);
        var failure = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
        assertTrue(failure.getMessage().contains("record=root"), failure::getMessage);
        assertTrue(failure.getMessage().contains("id=" + SatelliteMissionSavedData.DATA_NAME));
        assertTrue(failure.getMessage().contains("bytes=16777226 bound=16777216"));
        assertArrayEquals(before, Files.readAllBytes(source));
    }

    @Test void normalRootAndValidMigrationKeepTheirBehavior() throws Exception {
        CompoundTag normal = SatelliteMissionSavedData.create(0).save(new CompoundTag());
        Path source = write(normal);
        byte[] before = Files.readAllBytes(source);
        assertEquals(0, new WorldDataMigrationService().migrate(world).migratedFileCount());
        assertArrayEquals(before, Files.readAllBytes(source));
        assertEquals(normal, SatelliteMissionSavedData.load(normal).save(new CompoundTag()));
        assertTrue(SatelliteMissionSavedData.load(CanonicalSatelliteFixtures.legacy(normal)).operational());
    }

    @Test void manyBadRecordsStillReturnOnlyTheFirstFixedSizeReason() {
        CompoundTag one = CanonicalSatelliteFixtures.recordRoot(4090, 1);
        CompoundTag many = CanonicalSatelliteFixtures.recordRoot(4090, 1000);
        String first = assertThrows(RuntimeException.class, () -> SatelliteRegistryPayload.decodeCurrent(one)).getMessage();
        String repeated = assertThrows(RuntimeException.class, () -> SatelliteRegistryPayload.decodeCurrent(many)).getMessage();
        assertEquals(first, repeated);
        assertRecordDiagnostic(repeated);
        assertTrue(repeated.length() <= 192);
        assertFalse(repeated.contains("\n"));
        assertFalse(repeated.contains("\r"));
    }

    private Path write(CompoundTag payload) throws Exception {
        Files.createDirectories(world.resolve("data"));
        Path path = world.resolve("data").resolve(ManagedSavedDataType.SATELLITE_MISSIONS.fileName());
        CompoundTag outer = new CompoundTag();
        outer.put("data", payload);
        NbtIo.writeCompressed(outer, path.toFile());
        return path;
    }

    private static void assertRecordDiagnostic(String reason) {
        assertTrue(reason.contains("record=mission"), reason);
        assertTrue(reason.contains("id=00000000-0000-001f-0000-000000000000"), reason);
        assertTrue(reason.contains("bytes=4280 bound=4096"), reason);
    }
}

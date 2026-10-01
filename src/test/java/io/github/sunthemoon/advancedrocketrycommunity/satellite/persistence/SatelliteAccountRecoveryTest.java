package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Fixed load bounds also apply to repairs; use the actual checked NBT storage. */
final class SatelliteAccountRecoveryTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;

    @TempDir
    Path temporary;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @ParameterizedTest
    @CsvSource({"4095, 1, 1", "4095, 1, 3", "4094, 2, 2"})
    void repairsUniqueOwnersAtTheBoundaryAndReloadsIdempotently(int existing, int owners, int satellites) {
        CompoundTag source = root(existing, owners, satellites);
        Path file = write(source);
        SatelliteMissionSavedData repaired = SatelliteMissionSavedData.load(read(file));
        assertTrue(repaired.operational());
        assertTrue(repaired.flushPending());
        assertEquals(existing + owners, repaired.save(new CompoundTag())
                .getList("research_accounts", Tag.TAG_COMPOUND).size());
        assertEquals(ResearchAccount.empty(missingOwner(0)), repaired.account(missingOwner(0)));
        ResearchAccount retained = SatelliteNbtCodec.decodeAccount(
                source.getList("research_accounts", Tag.TAG_COMPOUND).getCompound(0));
        assertEquals(retained, repaired.account(retained.ownerId()));

        repaired.flush(file);
        CompoundTag persisted = read(file);
        assertEquals(existing + owners, persisted.getList("research_accounts", Tag.TAG_COMPOUND).size());
        assertTrue(SatelliteNbtSize.uncompressedBytes(persisted) <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
        SatelliteMissionSavedData restarted = SatelliteMissionSavedData.load(persisted);
        assertTrue(restarted.operational());
        assertFalse(restarted.flushPending());
        assertEquals(persisted, restarted.save(new CompoundTag()));
        restarted.flush(file);
        assertEquals(persisted, read(file));
        assertTrue(SatelliteMissionSavedData.load(read(file)).operational());
    }

    @ParameterizedTest
    @CsvSource({"4096, 1, 1", "4096, 1, 3", "4095, 2, 2"})
    void refusesTheWholeRepairAndPreservesOriginalFile(int existing, int owners, int satellites)
            throws IOException {
        CompoundTag source = root(existing, owners, satellites);
        assertThrows(IllegalArgumentException.class, () -> SatelliteRegistryPayload.decodeCurrent(source));
        assertBlockedFileUnchanged(source);
    }

    @Test
    void aFullRootWithExistingOwnerAccountsStillLoads() {
        CompoundTag source = root(SatelliteLimits.MAX_RESEARCH_ACCOUNTS, 0, 0);
        ListTag satellites = new ListTag();
        satellites.add(SatelliteNbtCodec.encodeSatellite(SatelliteState.launch(
                new UUID(4L, 0L), ModIdentity.id("data_satellite"), new UUID(1L, 0L), 0L)));
        source.put("satellites", satellites);
        SatelliteMissionSavedData loaded = SatelliteMissionSavedData.load(source);
        assertTrue(loaded.operational());
        assertFalse(loaded.flushPending());
        Path file = write(source);
        loaded.flush(file);
        assertTrue(SatelliteMissionSavedData.load(read(file)).operational());
    }

    @Test
    void anInputAtTheExactRootByteBoundCanRepairAndRoundTrip() {
        CompoundTag source = paddedRoot(SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
        Path file = write(source);
        SatelliteMissionSavedData loaded = SatelliteMissionSavedData.load(read(file));
        assertTrue(loaded.operational());
        assertEquals(ResearchAccount.empty(missingOwner(0)), loaded.account(missingOwner(0)));
        loaded.flush(file);
        assertTrue(SatelliteMissionSavedData.load(read(file)).operational());
    }

    @Test
    void anInputOneByteAboveTheRootBoundIsPreserved() throws IOException {
        assertBlockedFileUnchanged(paddedRoot(SatelliteLimits.MAX_REGISTRY_NBT_BYTES + 1));
    }

    private void assertBlockedFileUnchanged(CompoundTag source) throws IOException {
        Path file = write(source);
        byte[] original = Files.readAllBytes(file);
        SatelliteMissionSavedData blocked = SatelliteMissionSavedData.load(read(file));
        assertFalse(blocked.operational());
        assertFalse(blocked.isDirty());
        assertFalse(blocked.flushPending());
        assertEquals(source, blocked.preservedBlockedData().orElseThrow());
        assertEquals(source, blocked.save(new CompoundTag()));
        blocked.save(file.toFile());
        assertArrayEquals(original, Files.readAllBytes(file));
        // Even an explicit barrier must not serialize a partially repaired registry.
        blocked.flush(file);
        assertEquals(source, read(file));
        assertFalse(SatelliteMissionSavedData.load(read(file)).operational());
    }

    private Path write(CompoundTag source) {
        Path file = temporary.resolve(TYPE.fileName());
        CheckedSavedDataFile.replace(file, TYPE, () -> source);
        return file;
    }

    private static CompoundTag read(Path file) {
        return CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow();
    }

    private static CompoundTag paddedRoot(int size) {
        CompoundTag source = root(0, 1, 1);
        source.putByteArray("unrecognized_padding", new byte[0]);
        source.putByteArray("unrecognized_padding", new byte[size - SatelliteNbtSize.uncompressedBytes(source)]);
        assertEquals(size, SatelliteNbtSize.uncompressedBytes(source));
        return source;
    }

    private static CompoundTag root(int existing, int owners, int satelliteCount) {
        CompoundTag source = SatelliteMissionSavedData.create(0L).save(new CompoundTag());
        ListTag accounts = new ListTag();
        for (int index = 0; index < existing; index++) {
            accounts.add(SatelliteNbtCodec.encodeAccount(new ResearchAccount(
                    SatelliteLimits.RESEARCH_ACCOUNT_SCHEMA_VERSION, new UUID(1L, index), 11, 19L, 8L)));
        }
        source.put("research_accounts", accounts);
        ListTag satellites = new ListTag();
        for (int index = 0; index < satelliteCount; index++) {
            satellites.add(SatelliteNbtCodec.encodeSatellite(SatelliteState.launch(
                    new UUID(2L, index), ModIdentity.id("data_satellite"), missingOwner(index % owners), 0L)));
        }
        source.put("satellites", satellites);
        return source;
    }

    private static UUID missingOwner(int index) {
        return new UUID(3L, index);
    }
}

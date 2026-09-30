package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

/** ADR-044 §6: station root 4 carries the required warp energy list; root 3 upgrades by adding an empty one. */
final class StationWarpSchemaTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;
    private static final UUID STATION = new UUID(21L << 32, 1L);
    private static final UUID RESERVATION = new UUID(21L << 32, 5L);

    @Test
    void rootThreeUpgradeAddsOnlyAnEmptyBalanceListAndIsIdempotent() throws Exception {
        CompoundTag source = rootThree();
        CompoundTag original = source.copy();
        var result = SavedDataSchemaMigrator.migrate(TYPE, source);
        CompoundTag expected = source.copy();
        expected.putInt("schema_version", 4);
        expected.putString("format_epoch", "v1.5.0-station-warp");
        expected.put("warp_energy", new ListTag());
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, result.status());
        assertEquals(3, result.sourceSchema());
        assertEquals(expected, result.payload(), "Only the root version, epoch and an empty list change");
        assertEquals(original, source);
        assertEquals(1, result.payload().getInt("migrated_from_schema"), "Existing lineage is retained");
        var again = SavedDataSchemaMigrator.migrate(TYPE, result.payload());
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT, again.status());
        assertEquals(expected, again.payload());

        StationRegistrySavedData data = StationRegistrySavedData.load(result.payload());
        assertTrue(data.operational());
        var state = data.find(STATION).orElseThrow();
        assertEquals(768, state.region().width());
        assertEquals(400, state.environment().gravityMilli());
        assertEquals("advancedrocketrycommunity:earth", state.orbitBody().toString());
        assertEquals(0, data.warpEnergy(STATION));
        assertTrue(data.warpEnergyBalances().isEmpty());
        assertEquals(RESERVATION, data.reservations().get(0).stationId());
    }

    @Test
    void rootThreeIsNotLoadedAtRuntimeWithoutThePreStartUpgrade() throws Exception {
        CompoundTag source = rootThree();
        StationRegistrySavedData blocked = StationRegistrySavedData.load(source);
        assertFalse(blocked.operational());
        assertEquals(source, blocked.save(new CompoundTag()));
        assertFalse(blocked.isDirty());
        assertEquals(0, blocked.warpEnergy(STATION));
    }

    @Test
    void rootShapeAndEpochAreChosenBySchema() throws Exception {
        List<Consumer<CompoundTag>> invalid = List.of(
                root -> root.put("warp_energy", new ListTag()),
                root -> root.putString("format_epoch", "v1.5.0-station-warp"),
                root -> {
                    root.putInt("schema_version", 4);
                    root.putString("format_epoch", "v1.5.0-station-warp");
                },
                root -> {
                    root.putInt("schema_version", 4);
                    root.put("warp_energy", new ListTag());
                },
                root -> {
                    root.putInt("schema_version", 4);
                    root.putString("format_epoch", "v1.5.0-station-warp");
                    root.putInt("warp_energy", 0);
                }
        );
        // Review F5: the root-shape guard itself names the refused field (the payload decoder repeats it).
        CompoundTag carrying = rootThree();
        carrying.put("warp_energy", new ListTag());
        SavedDataMigrationException shape = assertThrows(SavedDataMigrationException.class,
                () -> SavedDataSchemaMigrator.migrate(TYPE, carrying));
        assertTrue(shape.getMessage().contains("schema 3 cannot carry warp_energy"), shape.getMessage());
        for (Consumer<CompoundTag> mutation : invalid) {
            CompoundTag source = rootThree();
            mutation.accept(source);
            CompoundTag original = source.copy();
            assertThrows(SavedDataMigrationException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
            assertEquals(original, source);
            assertBlockedUnchanged(source);
        }
        CompoundTag future = rootFour();
        future.putInt("schema_version", 5);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.FUTURE, SavedDataSchemaMigrator.migrate(TYPE, future).status());
        assertBlockedUnchanged(future);
    }

    @Test
    void rootFourBalancesRoundTripInStationOrder() throws Exception {
        CompoundTag source = rootFour();
        source.getList("warp_energy", Tag.TAG_COMPOUND).add(entry(STATION, StationLimits.MAX_WARP_ENERGY));
        StationRegistrySavedData data = StationRegistrySavedData.load(source);
        assertTrue(data.operational());
        assertEquals(StationLimits.MAX_WARP_ENERGY, data.warpEnergy(STATION));
        CompoundTag saved = data.save(new CompoundTag());
        assertEquals(source.get("warp_energy"), saved.get("warp_energy"));
        assertEquals(data.warpEnergyBalances(), StationRegistrySavedData.load(saved).warpEnergyBalances());
    }

    @Test
    void everyMalformedBalanceBlocksTheRegistryUnchanged() throws Exception {
        List<Consumer<ListTag>> mutations = List.of(
                list -> list.add(entry(STATION, 0)),
                list -> list.add(entry(STATION, -1)),
                list -> list.add(entry(STATION, StationLimits.MAX_WARP_ENERGY + 1)),
                list -> {
                    list.add(entry(STATION, 10));
                    list.add(entry(STATION, 20));
                },
                list -> list.add(entry(UUID.randomUUID(), 10)),
                list -> list.add(entry(RESERVATION, 10)),
                list -> {
                    CompoundTag wide = entry(STATION, 10);
                    wide.remove("energy");
                    wide.putLong("energy", 10L);
                    list.add(wide);
                },
                list -> {
                    CompoundTag extra = entry(STATION, 10);
                    extra.putString("core", "carried");
                    list.add(extra);
                },
                list -> {
                    CompoundTag missing = new CompoundTag();
                    missing.putInt("energy", 10);
                    list.add(missing);
                },
                list -> {
                    CompoundTag named = entry(STATION, 10);
                    named.remove("station_id");
                    named.putString("station_id", STATION.toString());
                    list.add(named);
                },
                list -> {
                    for (int index = 0; index <= StationLimits.MAX_WARP_ENERGY_ENTRIES; index++) {
                        list.add(entry(new UUID(99, index), 1));
                    }
                }
        );
        for (Consumer<ListTag> mutation : mutations) {
            CompoundTag source = rootFour();
            mutation.accept(source.getList("warp_energy", Tag.TAG_COMPOUND));
            assertBlockedUnchanged(source);
        }
        CompoundTag integers = rootFour();
        ListTag wrongType = new ListTag();
        wrongType.add(IntTag.valueOf(10));
        integers.put("warp_energy", wrongType);
        assertBlockedUnchanged(integers);
    }

    @Test
    void anEntryFitsItsByteBoundWithRoomToSpare() {
        CompoundTag largest = StationNbtCodec.encodeWarpEnergy(new UUID(-1L, -1L), StationLimits.MAX_WARP_ENERGY);
        assertTrue(StationNbtSize.uncompressedBytes(largest) <= StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES);
        assertEquals(new StationNbtCodec.WarpEnergyEntry(new UUID(-1L, -1L), StationLimits.MAX_WARP_ENERGY),
                StationNbtCodec.decodeWarpEnergy(largest));
        // A full balance list stays within the ADR-044 headroom reserved inside the registry bound.
        assertTrue((long) StationLimits.MAX_WARP_ENERGY_ENTRIES * StationLimits.MAX_WARP_ENERGY_ENTRY_NBT_BYTES
                <= StationLimits.WARP_ENERGY_HEADROOM_NBT_BYTES);
    }

    private static void assertBlockedUnchanged(CompoundTag source) {
        CompoundTag original = source.copy();
        StationRegistrySavedData blocked = StationRegistrySavedData.load(source);
        assertFalse(blocked.operational());
        assertTrue(blocked.stations().isEmpty());
        assertTrue(blocked.warpEnergyBalances().isEmpty());
        assertEquals(original, blocked.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> blocked.delete(STATION));
        assertEquals(0L, blocked.foldWarpCredits(java.util.Map.of()).credited());
    }

    private static CompoundTag entry(UUID stationId, int energy) {
        return StationNbtCodec.encodeWarpEnergy(stationId, energy);
    }

    private static CompoundTag rootFour() throws Exception {
        return SavedDataSchemaMigrator.migrate(TYPE, rootThree()).payload();
    }

    private static CompoundTag rootThree() throws Exception {
        try (var stream = StationWarpSchemaTest.class.getResourceAsStream("/migrations/v150/stations-v3.snbt")) {
            assertNotNull(stream);
            return TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

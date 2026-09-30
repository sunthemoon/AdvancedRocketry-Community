package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

/** Synthetic NBT fixture checks; packaged old-world migration is a separate test. */
final class StationSchemaMigrationTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.STATIONS;

    @Test
    void betaUpgradeChangesOnlyVersionsAndPreservesEveryOtherTag() throws Exception {
        CompoundTag source = legacy();
        CompoundTag original = source.copy();
        var result = SavedDataSchemaMigrator.migrate(TYPE, source);
        CompoundTag expected = source.copy();
        expected.putInt("schema_version", 4);
        expected.putString("format_epoch", "v1.5.0-station-warp");
        expected.put("warp_energy", new ListTag());
        station(expected).putInt("schema_version", 2);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, result.status());
        assertEquals(2, result.sourceSchema());
        assertEquals(expected, result.payload());
        assertEquals(original, source);
        assertEquals(2, station(result.payload()).getCompound("environment").getByte("vacuum"));
        assertEquals(source.get("reservations"), result.payload().get("reservations"));
        assertEquals(expected, SavedDataSchemaMigrator.migrate(TYPE, result.payload()).payload());
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT,
                SavedDataSchemaMigrator.migrate(TYPE, result.payload()).status());
    }

    @Test
    void alphaMigrationKeepsExistingLineageAndRequiresPreflightBeforeRuntimeLoad() throws Exception {
        CompoundTag source = legacy();
        source.putInt("schema_version", 1);
        source.remove("format_epoch");
        source.putInt("migrated_from_schema", 17);
        var blocked = StationRegistrySavedData.load(source);
        assertFalse(blocked.operational());
        assertFalse(blocked.isDirty());
        assertEquals(source, blocked.save(new CompoundTag()));
        var migrated = SavedDataSchemaMigrator.migrate(TYPE, source).payload();
        assertEquals(17, migrated.getInt("migrated_from_schema"));
        var data = StationRegistrySavedData.load(migrated);
        assertTrue(data.operational());
        assertFalse(data.isDirty());
        assertEquals(1, data.stations().size());
        assertEquals(1, data.reservations().size());
        var state = data.stations().get(0);
        assertEquals(new StationGridCell(-2, 3), state.cell());
        assertEquals(10_000, state.environment().gravityMilli());
        assertTrue(state.environment().vacuum());
        assertEquals("example:removed_body", state.orbitBody().toString());
        assertEquals(1, state.members().size());
        assertEquals(1, state.invitations().size());
        assertEquals(77L, state.createdAtGameTime());
        assertEquals(state, StationRegistrySavedData.load(data.save(new CompoundTag())).stations().get(0));
    }

    @Test
    void betaRuntimeLoadCannotSilentlyUpgradeOutsideBackupTransaction() throws Exception {
        CompoundTag source = legacy();
        var blocked = StationRegistrySavedData.load(source);
        assertFalse(blocked.operational());
        assertEquals(source, blocked.preservedBlockedData().orElseThrow());
        assertEquals(source, blocked.save(new CompoundTag()));
        assertFalse(blocked.isDirty());
    }

    @Test
    void currentExpandedStationKeepsCenterPadAndReservationVersion() throws Exception {
        CompoundTag current = migrated();
        station(current).putIntArray("region", new int[]{-2432, 2688, -1665, 3455});
        var data = StationRegistrySavedData.load(current);
        assertTrue(data.operational());
        var state = data.stations().get(0);
        assertEquals(768, state.region().width());
        assertEquals(state.cell().landingPad(), state.landingPad());
        assertEquals(state, data.findAt(-2432, 3455).orElseThrow());
        assertTrue(data.findAt(-2433, 3455).isEmpty());
        CompoundTag encoded = data.save(new CompoundTag());
        assertEquals(4, encoded.getInt("schema_version"));
        assertEquals(2, station(encoded).getInt("schema_version"));
        assertEquals(1, encoded.getList("reservations", Tag.TAG_COMPOUND).getCompound(0).getInt("schema_version"));
        assertEquals(state, StationRegistrySavedData.load(encoded).stations().get(0));
    }

    @Test
    void legacyExpandedOrMiscenteredGeometryDoesNotBecomeValidDuringUpgrade() throws Exception {
        for (int[] bounds : new int[][]{{-2432, 2688, -1665, 3455}, {-2303, 2816, -1792, 3327}}) {
            CompoundTag source = legacy();
            station(source).putIntArray("region", bounds);
            assertThrows(IllegalArgumentException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
            assertBlockedUnchanged(source);
        }
        CompoundTag source = legacy();
        station(source).putIntArray("landing_pad", new int[]{-2048, 129, 3072});
        assertThrows(IllegalArgumentException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
    }

    @Test
    void malformedLegacyRecordsCannotBypassStrictSemanticValidation() throws Exception {
        java.util.List<Consumer<CompoundTag>> mutations = java.util.List.of(
                tag -> station(tag).putInt("schema_version", 2),
                tag -> station(tag).putString("owner_id", "not-a-uuid"),
                tag -> station(tag).putLong("created_at_game_time", -1),
                tag -> station(tag).putString("orbit_body", "INVALID"),
                tag -> station(tag).putInt("cell_x", 1_000_001),
                tag -> station(tag).getCompound("environment").putInt("gravity_milli", 10_001),
                tag -> station(tag).getCompound("environment").putInt("vacuum", 1),
                tag -> station(tag).getList("members", Tag.TAG_COMPOUND)
                        .add(station(tag).getList("members", Tag.TAG_COMPOUND).getCompound(0).copy()),
                tag -> tag.getList("reservations", Tag.TAG_COMPOUND).getCompound(0).putInt("schema_version", 2),
                tag -> station(tag).putByteArray("too_large", new byte[StationLimits.MAX_STATION_RECORD_NBT_BYTES])
        );
        for (var mutation : mutations) {
            CompoundTag source = legacy();
            mutation.accept(source);
            CompoundTag original = source.copy();
            assertThrows(IllegalArgumentException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
            assertEquals(original, source);
            assertBlockedUnchanged(source);
        }
    }

    @Test
    void currentFutureMixedAndInvalidEpochRootsStayOpaqueOnRuntimeLoad() throws Exception {
        for (Consumer<CompoundTag> mutation : java.util.List.<Consumer<CompoundTag>>of(
                tag -> tag.putInt("schema_version", 5),
                tag -> tag.putString("format_epoch", "v0.9.0-beta"),
                tag -> tag.putString("format_epoch", "v1.5.0-orbital-station"),
                tag -> tag.remove("warp_energy"),
                tag -> station(tag).putInt("schema_version", 1),
                tag -> station(tag).putInt("schema_version", 3),
                tag -> tag.getList("reservations", Tag.TAG_COMPOUND).getCompound(0).putInt("schema_version", 2),
                tag -> tag.getList("stations", Tag.TAG_COMPOUND).add(station(tag).copy()),
                tag -> tag.putByteArray("too_large", new byte[StationLimits.MAX_REGISTRY_NBT_BYTES])
        )) {
            CompoundTag source = migrated();
            mutation.accept(source);
            assertBlockedUnchanged(source);
        }
    }

    @Test
    void memberInvitationAndReservationLimitsAreRetained() throws Exception {
        for (String field : java.util.List.of("members", "invitations")) {
            CompoundTag source = legacy();
            ListTag entries = new ListTag();
            for (int index = 0; index < 33; index++) {
                CompoundTag member = new CompoundTag();
                member.putUUID("id", new UUID(150, index));
                entries.add(member);
            }
            station(source).put(field, entries);
            assertThrows(IllegalArgumentException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
        }
        CompoundTag source = legacy();
        ListTag reservations = source.getList("reservations", Tag.TAG_COMPOUND);
        while (reservations.size() <= StationLimits.MAX_RESERVATIONS) {
            reservations.add(reservations.getCompound(0).copy());
        }
        assertThrows(IllegalArgumentException.class, () -> SavedDataSchemaMigrator.migrate(TYPE, source));
    }

    private static void assertBlockedUnchanged(CompoundTag source) {
        var blocked = StationRegistrySavedData.load(source);
        assertFalse(blocked.operational());
        assertTrue(blocked.stations().isEmpty());
        assertTrue(blocked.reservations().isEmpty());
        assertEquals(source, blocked.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, () -> blocked.delete(UUID.randomUUID()));
    }

    private static CompoundTag station(CompoundTag root) {
        return root.getList("stations", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static CompoundTag migrated() throws Exception {
        return SavedDataSchemaMigrator.migrate(TYPE, legacy()).payload();
    }

    private static CompoundTag legacy() throws Exception {
        try (var stream = StationSchemaMigrationTest.class.getResourceAsStream("/migrations/v150/stations-v2.snbt")) {
            assertNotNull(stream);
            return TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.saveddata.SavedData;
import org.junit.jupiter.api.Test;

final class ManagedSavedDataLoaderMigrationTest {
    private static final Map<ManagedSavedDataType, String> FIXTURES = fixtures();

    @Test
    void everyHistoricalRootBecomesCurrentThroughItsRequiredMigrationBoundary() throws Exception {
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            if (!type.hasLegacySchema()) {
                continue; // no historical root: covered by aRootWithoutHistoryIsCurrentFromItsFirstWrite
            }
            CompoundTag source = fixture(type);
            SavedData legacy = load(type, source);

            if (type == ManagedSavedDataType.STATIONS) {
                assertFalse(((StationRegistrySavedData) legacy).operational());
                assertFalse(legacy.isDirty(), "station upgrade must not bypass pre-start backups");
                assertEquals(source, legacy.save(new CompoundTag()));
                legacy = load(type, SavedDataSchemaMigrator.migrate(type, source).payload());
                assertFalse(legacy.isDirty());
            } else {
                assertTrue(legacy.isDirty(), type + " migration must schedule one canonical save");
            }

            assertOperational(type, legacy);
            CompoundTag canonical = legacy.save(new CompoundTag());
            assertEquals(type.currentSchemaVersion(),
                    canonical.getInt("schema_version"));
            assertEquals(type.formatEpoch(),
                    canonical.getString("format_epoch"));
            assertAuthorityCollections(type, canonical);

            SavedData current = load(type, canonical);
            assertFalse(current.isDirty(), type + " current schema must be idempotent");
            assertOperational(type, current);
            assertEquals(canonical, current.save(new CompoundTag()));
        }
    }

    private static SavedData load(ManagedSavedDataType type, CompoundTag payload) {
        return switch (type) {
            case CELESTIAL -> CelestialSavedData.load(payload);
            case ROCKET_TRANSACTIONS -> RocketTransactionSavedData.load(payload);
            case ROCKET_TRANSFERS -> RocketTransferSavedData.load(payload);
            case STATIONS -> StationRegistrySavedData.load(payload);
            case SATELLITE_MISSIONS -> SatelliteMissionSavedData.load(payload);
            case ENDGAME -> EndgameSavedData.load(payload);
        };
    }

    private static void assertOperational(ManagedSavedDataType type, SavedData data) {
        boolean operational = switch (type) {
            case CELESTIAL -> ((CelestialSavedData) data).isWritableSchema();
            case ROCKET_TRANSACTIONS -> ((RocketTransactionSavedData) data).operational();
            case ROCKET_TRANSFERS -> ((RocketTransferSavedData) data).operational();
            case STATIONS -> ((StationRegistrySavedData) data).operational();
            case SATELLITE_MISSIONS -> ((SatelliteMissionSavedData) data).operational();
            case ENDGAME -> ((EndgameSavedData) data).operational();
        };
        assertTrue(operational, type + " must remain operational after migration");
    }

    private static void assertAuthorityCollections(
            ManagedSavedDataType type,
            CompoundTag canonical
    ) {
        switch (type) {
            case CELESTIAL -> assertEquals(0, canonical.getList("bodies", CompoundTag.TAG_COMPOUND).size());
            case ROCKET_TRANSACTIONS -> assertEquals(0,
                    canonical.getList("transactions", CompoundTag.TAG_COMPOUND).size());
            case ROCKET_TRANSFERS -> assertEquals(0,
                    canonical.getList("transfers", CompoundTag.TAG_COMPOUND).size());
            case STATIONS -> {
                assertEquals(0, canonical.getList("stations", CompoundTag.TAG_COMPOUND).size());
                assertEquals(0, canonical.getList("reservations", CompoundTag.TAG_COMPOUND).size());
            }
            case SATELLITE_MISSIONS -> {
                assertEquals(0, canonical.getList("satellites", CompoundTag.TAG_COMPOUND).size());
                assertEquals(0, canonical.getList("missions", CompoundTag.TAG_COMPOUND).size());
                assertEquals(0, canonical.getList("research_accounts", CompoundTag.TAG_COMPOUND).size());
                assertEquals(1_200L, canonical.getCompound("clock").getLong("logical_game_time"));
            }
            case ENDGAME -> {
                assertEquals(0, canonical.getList("endpoints", CompoundTag.TAG_COMPOUND).size());
                assertEquals(0, canonical.getLongArray("dispatched_through").length);
                assertEquals(0, canonical.getList("zones", CompoundTag.TAG_COMPOUND).size());
            }
        }
    }

    /** ADR-054 section 10: the endgame root has no older schema; a fresh root is current and idempotent. */
    @Test
    void aRootWithoutHistoryIsCurrentFromItsFirstWrite() {
        CompoundTag canonical = EndgameSavedData.create().save(new CompoundTag());
        assertEquals(1, canonical.getInt("schema_version"));
        assertEquals("v1.7.0-endgame", canonical.getString("format_epoch"));
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT,
                SavedDataSchemaMigrator.migrate(ManagedSavedDataType.ENDGAME, canonical).status());
        SavedData current = load(ManagedSavedDataType.ENDGAME, canonical);
        assertFalse(current.isDirty());
        assertOperational(ManagedSavedDataType.ENDGAME, current);
        assertAuthorityCollections(ManagedSavedDataType.ENDGAME, canonical);
        assertEquals(canonical, current.save(new CompoundTag()));
    }

    private static CompoundTag fixture(ManagedSavedDataType type) throws Exception {
        String resource = FIXTURES.get(type);
        try (InputStream input = ManagedSavedDataLoaderMigrationTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing fixture " + resource);
            }
            return TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
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

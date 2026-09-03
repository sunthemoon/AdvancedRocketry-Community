package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import org.junit.jupiter.api.Test;

final class SavedDataSchemaMigratorTest {
    private static final Map<ManagedSavedDataType, String> FIXTURES = Map.of(
            ManagedSavedDataType.CELESTIAL, "v030-celestial-v1.snbt",
            ManagedSavedDataType.ROCKET_TRANSACTIONS, "v050-rocket-transactions-v1.snbt",
            ManagedSavedDataType.ROCKET_TRANSFERS, "v060-rocket-transfers-v1.snbt",
            ManagedSavedDataType.STATIONS, "v070-stations-v1.snbt",
            ManagedSavedDataType.SATELLITE_MISSIONS, "v080-satellite-missions-v1.snbt"
    );

    @Test
    void everyAlphaFixtureMigratesAndThenIsIdempotent() throws Exception {
        for (Map.Entry<ManagedSavedDataType, String> fixture : FIXTURES.entrySet()) {
            CompoundTag source = loadFixture(fixture.getValue());

            SavedDataSchemaMigrator.MigrationResult migrated =
                    SavedDataSchemaMigrator.migrate(fixture.getKey(), source);

            assertEquals(SavedDataSchemaMigrator.MigrationStatus.MIGRATED, migrated.status());
            assertEquals(1, migrated.sourceSchema());
            assertEquals(2, migrated.payload().getInt("schema_version"));
            assertEquals(SavedDataSchemaMigrator.FORMAT_EPOCH,
                    migrated.payload().getString("format_epoch"));
            assertEquals(1, migrated.payload().getInt("migrated_from_schema"));
            assertEquals(1, source.getInt("schema_version"), "source fixture must stay immutable");

            SavedDataSchemaMigrator.MigrationResult current =
                    SavedDataSchemaMigrator.migrate(fixture.getKey(), migrated.payload());
            assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT, current.status());
            assertFalse(current.changed());
            assertEquals(migrated.payload(), current.payload());
        }
    }

    @Test
    void futurePayloadRemainsOpaqueAndUnchanged() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        future.putString("opaque", "keep");

        SavedDataSchemaMigrator.MigrationResult result = SavedDataSchemaMigrator.migrate(
                ManagedSavedDataType.STATIONS,
                future
        );

        assertEquals(SavedDataSchemaMigrator.MigrationStatus.FUTURE, result.status());
        assertEquals(future, result.payload());
        assertFalse(result.changed());
    }

    @Test
    void missingUnknownAndUnstampedCurrentSchemasAreRejected() {
        CompoundTag missing = new CompoundTag();
        assertDiagnostic(MigrationDiagnosticId.INVALID_SCHEMA,
                () -> SavedDataSchemaMigrator.migrate(ManagedSavedDataType.CELESTIAL, missing));

        CompoundTag unknown = new CompoundTag();
        unknown.putInt("schema_version", 0);
        assertDiagnostic(MigrationDiagnosticId.INVALID_SCHEMA,
                () -> SavedDataSchemaMigrator.migrate(ManagedSavedDataType.CELESTIAL, unknown));

        CompoundTag unstamped = new CompoundTag();
        unstamped.putInt("schema_version", 2);
        unstamped.put("bodies", new net.minecraft.nbt.ListTag());
        assertDiagnostic(MigrationDiagnosticId.INVALID_SCHEMA,
                () -> SavedDataSchemaMigrator.migrate(ManagedSavedDataType.CELESTIAL, unstamped));
    }

    @Test
    void wrongRootShapeIsRejectedBeforeMigration() {
        CompoundTag source = new CompoundTag();
        source.putInt("schema_version", 1);
        source.putString("stations", "wrong-type");
        source.put("reservations", new net.minecraft.nbt.ListTag());

        assertDiagnostic(MigrationDiagnosticId.INVALID_SCHEMA,
                () -> SavedDataSchemaMigrator.migrate(ManagedSavedDataType.STATIONS, source));
    }

    @Test
    void resultAndInputAreDefensivelyCopied() throws Exception {
        CompoundTag source = loadFixture("v030-celestial-v1.snbt");
        SavedDataSchemaMigrator.MigrationResult result = SavedDataSchemaMigrator.migrate(
                ManagedSavedDataType.CELESTIAL,
                source
        );
        CompoundTag first = result.payload();
        first.putString("tampered", "yes");

        assertFalse(result.payload().contains("tampered"));
        assertTrue(result.changed());
    }

    @Test
    void subsystemRootSchemaConstantsStayAligned() {
        assertEquals(SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION,
                CelestialSavedData.CURRENT_SCHEMA_VERSION);
        assertEquals(SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION,
                RocketTransactionSavedData.SCHEMA_VERSION);
        assertEquals(SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION,
                RocketTransferSavedData.ROOT_SCHEMA_VERSION);
        assertEquals(SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION,
                StationLimits.REGISTRY_SCHEMA_VERSION);
        assertEquals(SavedDataSchemaMigrator.CURRENT_SCHEMA_VERSION,
                SatelliteLimits.REGISTRY_SCHEMA_VERSION);
    }

    private static CompoundTag loadFixture(String name) throws Exception {
        String path = "/migrations/v090/" + name;
        try (InputStream stream = SavedDataSchemaMigratorTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing fixture " + path);
            }
            return TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static void assertDiagnostic(
            MigrationDiagnosticId expected,
            org.junit.jupiter.api.function.Executable executable
    ) {
        SavedDataMigrationException exception = assertThrows(
                SavedDataMigrationException.class,
                executable
        );
        assertEquals(expected, exception.diagnosticId());
    }
}

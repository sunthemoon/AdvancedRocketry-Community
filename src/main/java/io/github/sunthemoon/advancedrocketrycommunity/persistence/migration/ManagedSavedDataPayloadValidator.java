package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import net.minecraft.nbt.CompoundTag;

/** Executes each subsystem's semantic decoder before any disk migration begins. */
final class ManagedSavedDataPayloadValidator {
    private ManagedSavedDataPayloadValidator() {
    }

    static void validate(ManagedSavedDataType type, CompoundTag payload) {
        boolean operational;
        try {
            operational = switch (type) {
                case CELESTIAL -> CelestialSavedData.load(payload).isWritableSchema();
                case ROCKET_TRANSACTIONS -> RocketTransactionSavedData.load(payload).operational();
                case ROCKET_TRANSFERS -> RocketTransferSavedData.load(payload).operational();
                case STATIONS -> StationRegistrySavedData.load(payload).operational();
                case SATELLITE_MISSIONS -> SatelliteMissionSavedData.load(payload).operational();
            };
        } catch (RuntimeException exception) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " failed semantic validation",
                    exception
            );
        }
        if (!operational) {
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " failed semantic validation"
            );
        }
    }
}

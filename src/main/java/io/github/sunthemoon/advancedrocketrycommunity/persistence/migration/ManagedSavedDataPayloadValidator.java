package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
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
        validate(type, payload, null);
    }

    static void validate(ManagedSavedDataType type, CompoundTag payload, java.nio.file.Path source) {
        boolean operational;
        try {
            operational = switch (type) {
                case CELESTIAL -> CelestialSavedData.load(payload).isWritableSchema();
                case ROCKET_TRANSACTIONS -> RocketTransactionSavedData.load(payload).operational();
                case ROCKET_TRANSFERS -> RocketTransferSavedData.load(payload).operational();
                case STATIONS -> StationRegistrySavedData.load(payload).operational();
                case SATELLITE_MISSIONS -> {
                    var loaded = SatelliteMissionSavedData.load(payload);
                    loaded.canonicalBoundsFailure().ifPresent(failure -> { throw failure; });
                    yield loaded.operational();
                }
                case ENDGAME -> EndgameSavedData.load(payload).operational();
                case ROCKET_REJOINS -> {
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodec.decode(payload);
                    yield true;
                }
                case CLASSIC_PROGRESSION -> io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent
                        .ClassicProgressionSavedData.load(payload).operational();
                case RESOURCE_ESCROW -> {
                    var loaded = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCodec.decode(payload);
                    if (source != null) {
                        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedgerBaseline.require(source, loaded);
                    }
                    yield true;
                }
                // W27A-DESIGN section 2: a future or invalid receipt root refuses startup and keeps its bytes, as escrow does.
                case BAY_RECEIPTS -> {
                    var loaded = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptCodec.decode(payload);
                    if (source != null) {
                        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptBaseline.require(source, loaded);
                    }
                    yield true;
                }
            };
        } catch (RuntimeException exception) {
            var bounds = io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence
                    .SatelliteCanonicalBoundsException.find(exception);
            throw new SavedDataMigrationException(
                    MigrationDiagnosticId.INVALID_SCHEMA,
                    bounds.map(Throwable::getMessage).orElse(type.dataName() + " failed semantic validation"),
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

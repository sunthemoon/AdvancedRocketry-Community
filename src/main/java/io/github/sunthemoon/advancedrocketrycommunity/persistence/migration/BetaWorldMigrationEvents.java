package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.nio.file.Path;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

/** Runs the Beta migration transaction before any managed SavedData is loaded. */
public final class BetaWorldMigrationEvents {
    static final String MIGRATION_HINT = "Keep the world stopped and restore the newest complete migration backup if recovery is required.";
    static final String ESCROW_HINT = "Keep the world stopped. Escrow recovery requires a consistent whole-world backup including all terrain, entities, POI, journals and player data; never restore or delete only the escrow file.";
    private final WorldDataMigrationService migrations;
    static final String REJOIN_HINT = "Keep the world stopped. Use compatible software or validated whole-world repair including all Levels, entities, POI, journals, rejoins, player data and metadata; never delete the sidecar or restore isolated files.";

    public BetaWorldMigrationEvents() {
        this(new WorldDataMigrationService());
    }

    BetaWorldMigrationEvents(WorldDataMigrationService migrations) {
        this.migrations = migrations;
    }

    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path worldRoot = event.getServer().getWorldPath(LevelResource.ROOT);
        try {
            WorldDataMigrationService.MigrationReport report = migrations.migrate(worldRoot);
            AdvancedRocketryCommunity.LOGGER.info(
                    "[{}] Beta world data check complete: managed={}, migrated={}, backup={}",
                    report.diagnosticId().code(),
                    report.managedFileCount(),
                    report.migratedFileCount(),
                    report.backupDirectory().orElse("none")
            );
        } catch (SavedDataMigrationException exception) {
            if (logSatelliteBoundsRefusal(exception)) { throw exception; }
            if (exception.dataType().orElse(null) == ManagedSavedDataType.ROCKET_REJOINS
                    || recoveryHint(worldRoot).equals(REJOIN_HINT)) {
                AdvancedRocketryCommunity.LOGGER.error("[{}] Beta world data check blocked startup; type={}; reason={}. {}",
                        exception.diagnosticId().code(), exception.dataType().map(Enum::name).orElse("WORLD"),
                        boundedReason(exception.getMessage()), REJOIN_HINT, exception);
                throw exception;
            }
            if (recoveryHint(worldRoot).equals(ESCROW_HINT)) {
                logEscrowRefusal(exception);
                throw exception;
            }
            AdvancedRocketryCommunity.LOGGER.error(
                    "[{}] Beta world data check blocked startup: {}. {}",
                    exception.diagnosticId().code(),
                    exception.getMessage(),
                    recoveryHint(worldRoot),
                    exception
            );
            throw exception;
        }
    }

    /** D15: one fixed-field diagnostic, before generic/whole-world hints could obscure the named record. */
    static boolean logSatelliteBoundsRefusal(SavedDataMigrationException exception) {
        var bounds = io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence
                .SatelliteCanonicalBoundsException.find(exception);
        if (bounds.isEmpty()) { return false; }
        AdvancedRocketryCommunity.LOGGER.error("[{}] Beta world data check blocked startup: {}. "
                        + "Keep the server stopped, back up the complete world, repair the named record and dependent "
                        + "references offline until its canonical encoding fits, then restart; never restore isolated authority files.",
                exception.diagnosticId().code(), bounds.orElseThrow().getMessage());
        return true;
    }

    static void logEscrowRefusal(SavedDataMigrationException exception) {
        AdvancedRocketryCommunity.LOGGER.error("[{}] Beta world data check blocked startup; type={}; reason={}. {}",
                exception.diagnosticId().code(), exception.dataType().map(Enum::name).orElse("WORLD"),
                boundedReason(exception.getMessage()), ESCROW_HINT);
    }
    private static String boundedReason(String message) {
        if (message == null) { return "No diagnostic message"; }
        var line = new StringBuilder();
        for (int i = 0; i < Math.min(160, message.length()); i++) {
            char value = message.charAt(i);
            line.append(Character.isISOControl(value) || Character.isWhitespace(value) || Character.isSpaceChar(value) ? ' ' : value);
        }
        if (message.length() > 160) { line.append("..."); }
        return line.toString();
    }

    /**
     * A world with an escrow ledger or bay receipts (R-039) must never roll back managed files alone, whichever type
     * refused startup. W328: a ledger or receipt file whose presence is unknown counts as present.
     */
    static String recoveryHint(Path worldRoot) {
        return recoveryHint(worldRoot, CheckedSavedDataFile::presence);
    }

    /** The hint over a given presence check; unit tests inject a failing status check. */
    static String recoveryHint(Path worldRoot, java.util.function.Function<Path, CheckedSavedDataFile.Presence> presence) {
        Path data = worldRoot.resolve("data");
        if (presence.apply(data.resolve(ManagedSavedDataType.ROCKET_REJOINS.fileName())) != CheckedSavedDataFile.Presence.ABSENT) {
            return REJOIN_HINT;
        }
        return presence.apply(data.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName())) != CheckedSavedDataFile.Presence.ABSENT
                || presence.apply(data.resolve(ManagedSavedDataType.BAY_RECEIPTS.fileName())) != CheckedSavedDataFile.Presence.ABSENT
                ? ESCROW_HINT : MIGRATION_HINT;
    }
}

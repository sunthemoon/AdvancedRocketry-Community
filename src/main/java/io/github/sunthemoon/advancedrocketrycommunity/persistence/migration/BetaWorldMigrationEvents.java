package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

/** Runs the Beta migration transaction before any managed SavedData is loaded. */
public final class BetaWorldMigrationEvents {
    static final String MIGRATION_HINT = "Keep the world stopped and restore the newest complete migration backup if recovery is required.";
    static final String ESCROW_HINT = "Keep the world stopped. Escrow recovery requires a consistent whole-world backup including all terrain, entities, POI, journals and player data; never restore or delete only the escrow file.";
    private final WorldDataMigrationService migrations;

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
            if (recoveryHint(worldRoot).equals(ESCROW_HINT)) {
                logEscrowRefusal(exception.diagnosticId());
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

    static void logEscrowRefusal(MigrationDiagnosticId diagnostic) {
        AdvancedRocketryCommunity.LOGGER.error("[{}] Beta world data check blocked startup; escrow authority unavailable. {}",
                diagnostic.code(), ESCROW_HINT);
    }

    /** A world with an escrow ledger must never roll back managed files alone, whichever type refused startup. */
    static String recoveryHint(Path worldRoot) {
        Path ledger = worldRoot.resolve("data").resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        return Files.exists(ledger, LinkOption.NOFOLLOW_LINKS) ? ESCROW_HINT : MIGRATION_HINT;
    }
}

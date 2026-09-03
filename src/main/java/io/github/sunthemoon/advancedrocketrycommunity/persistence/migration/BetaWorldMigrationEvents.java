package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

/** Runs the Beta migration transaction before any managed SavedData is loaded. */
public final class BetaWorldMigrationEvents {
    private final WorldDataMigrationService migrations;

    public BetaWorldMigrationEvents() {
        this(new WorldDataMigrationService());
    }

    BetaWorldMigrationEvents(WorldDataMigrationService migrations) {
        this.migrations = migrations;
    }

    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        try {
            WorldDataMigrationService.MigrationReport report = migrations.migrate(
                    event.getServer().getWorldPath(LevelResource.ROOT)
            );
            AdvancedRocketryCommunity.LOGGER.info(
                    "[{}] Beta world data check complete: managed={}, migrated={}, backup={}",
                    report.diagnosticId().code(),
                    report.managedFileCount(),
                    report.migratedFileCount(),
                    report.backupDirectory().orElse("none")
            );
        } catch (SavedDataMigrationException exception) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "[{}] Beta world data check blocked startup: {}. Keep the world stopped and restore the newest complete migration backup if recovery is required.",
                    exception.diagnosticId().code(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }
}

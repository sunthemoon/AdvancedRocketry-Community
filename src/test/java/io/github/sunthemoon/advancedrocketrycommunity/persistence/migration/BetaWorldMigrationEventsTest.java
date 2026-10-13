package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Review W11b1 finding 11: the escrow whole-world hint applies only to worlds that hold an escrow ledger. */
class BetaWorldMigrationEventsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path world;

    @Test void startupRefusalHintFollowsTheEscrowLedgerPresence() throws Exception {
        assertEquals(BetaWorldMigrationEvents.MIGRATION_HINT, BetaWorldMigrationEvents.recoveryHint(world));
        Path data = Files.createDirectory(world.resolve("data"));
        Files.writeString(data.resolve(ManagedSavedDataType.STATIONS.fileName()), "stations");
        assertEquals(BetaWorldMigrationEvents.MIGRATION_HINT, BetaWorldMigrationEvents.recoveryHint(world));
        Files.writeString(data.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), "ledger");
        assertEquals(BetaWorldMigrationEvents.ESCROW_HINT, BetaWorldMigrationEvents.recoveryHint(world));
        assertTrue(BetaWorldMigrationEvents.ESCROW_HINT.contains("never restore or delete only the escrow file"));
        assertTrue(BetaWorldMigrationEvents.MIGRATION_HINT.contains("restore the newest complete migration backup"));
    }
}

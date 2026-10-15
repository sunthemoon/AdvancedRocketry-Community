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
    @Test void escrowPreStartRefusalEmitsOneBoundedDiagnosticWithoutAStackTrace() {
        var events = new java.util.ArrayList<org.apache.logging.log4j.core.LogEvent>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger("advancedrocketrycommunity");
        var context = logger.getContext(); var config = context.getConfiguration().getRootLogger();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("prestart-baseline-test", null, null, true,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start(); config.addAppender(appender, null, null); context.updateLoggers();
        try { BetaWorldMigrationEvents.logEscrowRefusal(MigrationDiagnosticId.INVALID_SCHEMA); }
        finally { config.removeAppender(appender.getName()); context.updateLoggers(); appender.stop(); }
        assertEquals(1, events.size()); assertNull(events.get(0).getThrown());
        var line = events.get(0).getMessage().getFormattedMessage();
        assertTrue(line.contains("blocked startup")); assertTrue(line.contains(BetaWorldMigrationEvents.ESCROW_HINT));
        assertTrue(line.length() < 512); assertFalse(line.contains("\n"));
    }
}

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
        for (var type : ManagedSavedDataType.values()) {
            var failure = new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA,
                    type.dataName() + " failed semantic validation\r\n\t" + "x".repeat(2_000)).forType(type);
            var event = diagnostic(failure);
            var line = event.getMessage().getFormattedMessage();
            assertTrue(line.contains("type=" + type.name()));
            assertTrue(line.contains(type.dataName() + " failed semantic validation"));
            assertTrue(line.endsWith(BetaWorldMigrationEvents.ESCROW_HINT));
            assertFalse(line.contains("escrow authority unavailable"));
            assertTrue(line.contains("..."));
        }
        var noMessage = diagnostic(new SavedDataMigrationException(MigrationDiagnosticId.INVALID_SCHEMA, null));
        assertTrue(noMessage.getMessage().getFormattedMessage().contains("type=WORLD; reason=No diagnostic message"));
    }
    @Test void actualPreStartFailuresNameTheirTypeAndLeaveEveryRawFileUntouched() throws Exception {
        for (var type : java.util.List.of(ManagedSavedDataType.STATIONS, ManagedSavedDataType.RESOURCE_ESCROW)) {
            var root = Files.createDirectory(world.resolve(type.name())); var data = Files.createDirectory(root.resolve("data"));
            var ledger = data.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
            Files.writeString(ledger, "preserved escrow bytes");
            var failing = data.resolve(type.fileName()); Files.writeString(failing, "not compressed NBT");
            byte[] ledgerBefore = Files.readAllBytes(ledger), failingBefore = Files.readAllBytes(failing);
            var failure = assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(root));
            assertEquals(type, failure.dataType().orElseThrow());
            assertTrue(diagnostic(failure).getMessage().getFormattedMessage().contains("type=" + type.name()));
            assertArrayEquals(ledgerBefore, Files.readAllBytes(ledger)); assertArrayEquals(failingBefore, Files.readAllBytes(failing));
        }
    }
    private static org.apache.logging.log4j.core.LogEvent diagnostic(SavedDataMigrationException failure) {
        var events = new java.util.ArrayList<org.apache.logging.log4j.core.LogEvent>();
        var logger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger("advancedrocketrycommunity");
        var context = logger.getContext(); var config = context.getConfiguration().getRootLogger();
        var appender = new org.apache.logging.log4j.core.appender.AbstractAppender("prestart-baseline-test", null, null, true,
                org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
            @Override public void append(org.apache.logging.log4j.core.LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start(); config.addAppender(appender, null, null); context.updateLoggers();
        try { BetaWorldMigrationEvents.logEscrowRefusal(failure); }
        finally { config.removeAppender(appender.getName()); context.updateLoggers(); appender.stop(); }
        assertEquals(1, events.size()); assertNull(events.get(0).getThrown());
        var line = events.get(0).getMessage().getFormattedMessage();
        assertTrue(line.contains("blocked startup")); assertTrue(line.contains(BetaWorldMigrationEvents.ESCROW_HINT));
        assertTrue(line.length() < 512); assertFalse(line.contains("\n"));
        assertFalse(line.contains("\r")); assertFalse(line.contains("\t"));
        return events.get(0);
    }
}

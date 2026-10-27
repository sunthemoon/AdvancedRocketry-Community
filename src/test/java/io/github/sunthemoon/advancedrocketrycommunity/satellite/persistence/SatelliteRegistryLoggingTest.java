package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** ADR-074 item 12: the registry writer reports each failure streak once and its end once, with no total cap. */
@Timeout(120)
final class SatelliteRegistryLoggingTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void oneErrorPerFailureStreakAndOneInfoWhenACommitEndsIt() {
        var server = live(); var data = registry(server);
        Path file = directory.resolve(REGISTRY.fileName());
        var fail = ATOMIC.withCommitter((staged, target) -> { throw new IOException("W129b injected failure"); });
        var unknown = new io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy(REGISTRY,
                (staged, target) -> { CheckedSavedDataFile.atomicMove(staged, target); throw new IOException("W129b ambiguous"); },
                (target, type) -> { throw new IllegalStateException("W129b unreadable"); });
        try (var log = new SatelliteRegistryCapacityTest.ErrorLines(); var info = new InfoLines()) {
            launch(data); write(server, data, file, fail);
            launch(data); write(server, data, file, fail);
            launch(data); write(server, data, file, unknown);
            assertEquals(1, log.lines().size(), "A streak logs once: " + log.lines());
            assertTrue(log.lines().get(0).startsWith("ERROR ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=FAILED sequence="));
            write(server, data, file, ATOMIC);
            assertEquals(1, info.lines().size());
            assertTrue(info.lines().get(0).startsWith("INFO ARCE_SATELLITE_REGISTRY_ACKNOWLEDGED sequence="));
            write(server, data, file, ATOMIC);
            assertEquals(1, info.lines().size(), "An ordinary commit logs nothing");
            launch(data); write(server, data, file, unknown);
            assertEquals(2, log.lines().size(), "A new streak logs again");
            assertTrue(log.lines().get(1).startsWith("ERROR ARCE_SATELLITE_REGISTRY_UNACKNOWLEDGED outcome=UNKNOWN"));
        }
    }

    /** INFO lines of the registry writer. */
    static final class InfoLines implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger)
                org.apache.logging.log4j.LogManager.getLogger(SatelliteRegistryWriter.class);
        private final List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.appender.AbstractAppender appender =
                new org.apache.logging.log4j.core.appender.AbstractAppender("w129b-info", null, null, true,
                        org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                    @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                        if (event.getLevel() == org.apache.logging.log4j.Level.INFO) {
                            lines.add("INFO " + event.getMessage().getFormattedMessage());
                        }
                    }
                };
        InfoLines() { appender.start(); logger.addAppender(appender); }
        List<String> lines() { return List.copyOf(lines); }
        @Override public void close() { logger.removeAppender(appender); appender.stop(); }
    }
}

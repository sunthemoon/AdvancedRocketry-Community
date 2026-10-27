package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint.Outcome.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-074 item 12 and design section 5.3 (R-040): the transaction writer's log lines, counted exactly. */
class RocketTransactionLoggingTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    private static final CheckedSavedDataPolicy FAILING = ATOMIC.withCommitter((staged, file) -> {
        throw new IOException("W129a before replacement");
    });
    private static final CheckedSavedDataPolicy UNREADABLE = new CheckedSavedDataPolicy(TYPE, (staged, file) -> {
        CheckedSavedDataFile.atomicMove(staged, file); throw new IOException("W129a after replacement");
    }, (file, type) -> { throw new IllegalStateException("W129a unreadable"); });
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void oneErrorAtTheFirstFailureOfAStreakAndOneInfoAtTheCommitThatEndsIt() {
        try (var log = new LogLines()) {
            // Each step changes the content, so that a failed replacement is distinguishable from the file on disk.
            var data = new RocketTransactionSavedData();
            assertEquals(COMMITTED, data.saveChecked(target(), ATOMIC));
            assertEquals(List.of(), log.lines(), "A commit outside a streak logs nothing");
            RocketTransactionShutdownTest.insert(data, 1); long failed = data.mutationSequence();
            assertEquals(NOT_COMMITTED, data.saveChecked(target(), FAILING));
            RocketTransactionShutdownTest.insert(data, 2);
            assertEquals(UNKNOWN, data.saveChecked(target(), UNREADABLE));
            RocketTransactionShutdownTest.insert(data, 3);
            assertEquals(UNKNOWN, data.saveChecked(target(), FAILING));
            RocketTransactionShutdownTest.insert(data, 4); long committed = data.mutationSequence();
            assertEquals(COMMITTED, data.saveChecked(target(), ATOMIC));
            RocketTransactionShutdownTest.insert(data, 5); long again = data.mutationSequence();
            assertEquals(NOT_COMMITTED, data.saveChecked(target(), FAILING));
            assertEquals(List.of(
                    "ERROR ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=NOT_COMMITTED sequence=" + failed + " acknowledged=1",
                    "INFO ARCE_TRANSACTION_JOURNAL_ACKNOWLEDGED sequence=" + committed + " after an unacknowledged write",
                    "ERROR ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=NOT_COMMITTED sequence=" + again
                            + " acknowledged=" + committed), log.lines());
        }
    }

    @Test void anUnexpectedWriterThrowableLogsOneErrorAndStartsTheUnknownStreak() {
        try (var log = new LogLines()) {
            var data = new RocketTransactionSavedData(parent -> { throw new AssertionError("W129a injected writer Error"); });
            assertEquals(UNKNOWN, data.saveChecked(target(), ATOMIC));
            assertEquals(List.of(
                    "ERROR ARCE_CHECKED_WRITER_THROWABLE root=rocket_transactions sequence=1",
                    "ERROR ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN sequence=1 acknowledged=none"), log.lines());
            assertTrue(data.acknowledgement().isEmpty()); assertTrue(data.isDirty());
        }
    }

    @Test void refusalsOfBlockedOrUnreadableJournalsAddNoLogLoop() {
        try (var log = new LogLines()) {
            var future = new RocketTransactionSavedData().save(new CompoundTag()); future.putInt("schema_version", 999);
            var blocked = RocketTransactionSavedData.load(future); blocked.setDirty();
            var unreadable = RocketTransactionSavedData.created(ManagedRootPresence.UNREADABLE);
            for (int attempt = 0; attempt < 3; attempt++) {
                blocked.save(target().toFile()); assertEquals(NOT_COMMITTED, unreadable.saveChecked(target(), ATOMIC));
            }
            assertEquals(List.of(), log.lines(), "The load-time diagnostic is the only report of a held root");
        }
    }

    private Path target() { return directory.resolve(TYPE.fileName()); }

    /** INFO and higher lines of the journal and the writer core, as "LEVEL message". */
    static final class LogLines implements AutoCloseable {
        private final List<org.apache.logging.log4j.core.Logger> loggers = List.of(
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(RocketTransactionSavedData.class),
                (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getLogger(CheckedRootWriter.class));
        private final List<String> lines = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.appender.AbstractAppender appender =
                new org.apache.logging.log4j.core.appender.AbstractAppender("w129a-logging", null, null, true,
                        org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                    @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                        if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.INFO)
                                && !event.getMessage().getFormattedMessage().startsWith("ARCE_CHECKED_WRITER_DRAINED")) {
                            lines.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
                        }
                    }
                };
        LogLines() {
            appender.start();
            for (var logger : loggers) { logger.setLevel(org.apache.logging.log4j.Level.INFO); logger.addAppender(appender); }
        }
        List<String> lines() { return List.copyOf(lines); }
        @Override public void close() { loggers.forEach(logger -> logger.removeAppender(appender)); appender.stop(); }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCaptureVetoDiagnosticTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void identicalClassAndMessageLogOnceButDifferentMessagesAndClassesLogAgain() {
        try (var logs = new CaptureLogs(); var barrier = new EscrowBarrier()) {
            var first = new IllegalArgumentException("frontier mismatch", new IllegalStateException("first cause"));
            var repeat = new IllegalArgumentException("frontier mismatch", new IllegalStateException("different cause"));
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(first));
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(repeat));
            assertEquals(1, logs.events.size());
            assertSame(first, logs.events.get(0).getThrown());
            assertEquals(Level.INFO, logs.events.get(0).getLevel());
            assertEquals("ARCE_ESCROW_CAPTURE_VETO reason=BARRIER_VETO", logs.events.get(0).getMessage().getFormattedMessage());

            var otherMessage = new IllegalArgumentException("identity mismatch");
            var otherClass = new IllegalStateException("frontier mismatch");
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(otherMessage));
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(otherClass));
            assertEquals(3, logs.events.size());
            assertSame(otherMessage, logs.events.get(1).getThrown());
            assertSame(otherClass, logs.events.get(2).getThrown());
        }
    }

    @Test void nullAndLiteralNullMessagesAreDistinct() {
        try (var logs = new CaptureLogs(); var barrier = new EscrowBarrier()) {
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(new IllegalArgumentException()));
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(new IllegalArgumentException("null")));
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(new IllegalArgumentException()));
            assertEquals(2, logs.events.size());
        }
    }

    @Test void atMost64DistinctVetoesAndOneOverflowNoticeLogPerSession() {
        try (var logs = new CaptureLogs(); var barrier = new EscrowBarrier()) {
            assertEquals(EscrowReason.SUPERSEDING_SAVE, barrier.captureReason(new EscrowChunkStore.SupersededCapture()));
            assertEquals(EscrowReason.BARRIER_BOUNDS, barrier.captureReason(new EscrowChunkStore.CaptureBounds("bound")));
            assertTrue(logs.events.isEmpty());
            for (int i = 0; i < 64; i++) {
                assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(new IllegalArgumentException("veto " + i)));
            }
            assertEquals(64, logs.events.size());
            assertTrue(logs.events.stream().allMatch(event -> event.getThrown() != null && event.getLevel() == Level.INFO));
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(new IllegalArgumentException("veto 0")));
            assertEquals(64, logs.events.size());
            assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(new IllegalArgumentException("overflow")));
            assertEquals(65, logs.events.size());
            var limit = logs.events.get(64);
            assertEquals(Level.INFO, limit.getLevel());
            assertEquals("ARCE_ESCROW_CAPTURE_VETO_LIMIT distinct_limit=64 further_diagnostics=suppressed", limit.getMessage().getFormattedMessage());
            assertNull(limit.getThrown());
            for (int i = 0; i < 256; i++) {
                assertEquals(EscrowReason.BARRIER_VETO, barrier.captureReason(new IllegalArgumentException("veto " + i)));
            }
            assertEquals(65, logs.events.size());
        }
    }

    @Test void diagnosticsAreSessionLocalAndClosedBarriersStaySilent() {
        try (var logs = new CaptureLogs(); var first = new EscrowBarrier(); var second = new EscrowBarrier()) {
            var failure = new IllegalArgumentException("frontier mismatch");
            assertEquals(EscrowReason.BARRIER_VETO, first.captureReason(failure));
            assertEquals(EscrowReason.BARRIER_VETO, second.captureReason(failure));
            assertEquals(2, logs.events.size());
            first.close();
            assertEquals(EscrowReason.BARRIER_VETO, first.captureReason(new IllegalArgumentException("after stop")));
            assertEquals(2, logs.events.size());
            try (var restarted = new EscrowBarrier()) {
                assertEquals(EscrowReason.BARRIER_VETO, restarted.captureReason(failure));
                assertEquals(3, logs.events.size());
            }
        }
    }

    @Test void repeatedCaptureFailuresStillRefuseAndCountEveryAttempt() {
        var failure = new IllegalArgumentException("frontier mismatch");
        var replies = new ArrayList<EscrowBarrier.Result>();
        try (var logs = new CaptureLogs(); var barrier = new EscrowBarrier(label -> { throw failure; })) {
            var operation = EscrowTestFixtures.operation(1);
            for (int tick : List.of(0, 20)) {
                assertTrue(barrier.begin(null, operation.source(), null, root -> true, tick, replies::add));
            }
            assertEquals(2, replies.size());
            assertTrue(replies.stream().allMatch(result -> result.proof() == null && result.reason() == EscrowReason.BARRIER_VETO));
            assertEquals(2, barrier.refusals());
            assertEquals(0, barrier.ioFailures());
            assertEquals(1, logs.events.size());
        }
    }

    private static final class CaptureLogs implements AutoCloseable {
        private final Logger logger = (Logger) LogManager.getLogger(EscrowBarrier.class);
        private final Level previous = logger.getLevel();
        private final List<LogEvent> events = new ArrayList<>();
        private final AbstractAppender appender = new AbstractAppender("w251-fix-capture-veto", null, null, true, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        };

        private CaptureLogs() { appender.start(); logger.addAppender(appender); logger.setLevel(Level.INFO); }
        @Override public void close() { logger.removeAppender(appender); logger.setLevel(previous); appender.stop(); }
    }
}

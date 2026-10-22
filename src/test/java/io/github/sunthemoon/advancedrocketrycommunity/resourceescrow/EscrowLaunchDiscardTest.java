package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

class EscrowLaunchDiscardTest {
    @Test void reportsChangedMissingAndAddedFieldsWithoutMutatingOrPrintingTheirValues() {
        var expected = new CompoundTag();
        expected.putString("motion", "old-private-value"); expected.putInt("missing", 1); expected.putInt("same", 2);
        var observed = expected.copy();
        observed.putString("motion", "new-private-value"); observed.remove("missing"); observed.putInt("added", 3);
        var before = expected.copy(); var after = observed.copy();
        assertEquals("added,missing,motion", EscrowLaunchDiscard.differences(expected, observed));
        assertEquals(before, expected); assertEquals(after, observed);
        assertEquals("", EscrowLaunchDiscard.differences(expected, expected.copy()));
        assertEquals("unavailable", EscrowLaunchDiscard.differences(expected, null));
    }

    @Test void missingEnvelopeFieldDetailStillLogsTheReasonAndAllowsCompletion() {
        try (var logs = new Logs()) {
            assertEquals("completed", assertDoesNotThrow(() -> {
                EscrowLaunchDiscard.note("F1_AFTER_ARM", () -> "reason=refused fields="
                        + EscrowLaunchDiscard.detail(() -> EscrowLaunchDiscard.differences(
                                Optional.<CompoundTag>empty().orElseThrow(), new CompoundTag())));
                return "completed";
            }));
            assertEquals(1, logs.events.size());
            assertEquals(Level.INFO, logs.events.get(0).getLevel());
            assertEquals("ARCE_LAUNCH_DISCARD site=F1_AFTER_ARM reason=refused fields=unavailable",
                    logs.events.get(0).getMessage().getFormattedMessage());
        }
    }

    @Test void failingMessageDetailStillLogsUnavailableAndReturnsNormally() {
        try (var logs = new Logs()) {
            assertDoesNotThrow(() -> EscrowLaunchDiscard.note("F2_PERMIT", () -> {
                throw new IllegalStateException("detail is unavailable");
            }));
            assertEquals(1, logs.events.size());
            assertEquals(Level.INFO, logs.events.get(0).getLevel());
            assertEquals("ARCE_LAUNCH_DISCARD site=F2_PERMIT unavailable",
                    logs.events.get(0).getMessage().getFormattedMessage());
        }
    }

    @Test void diagnosticErrorsAndMissingDetailsAreUnavailable() {
        assertEquals("unavailable", EscrowLaunchDiscard.detail(() -> { throw new AssertionError("broken formatter"); }));
        assertEquals("unavailable", EscrowLaunchDiscard.detail(() -> null));
        assertEquals("unavailable", EscrowLaunchDiscard.detail(null));
        assertEquals("available", EscrowLaunchDiscard.detail(() -> "available"));
    }

    @Test void failingLoggingSinkDoesNotPreventContinuation() {
        assertEquals("completed", assertDoesNotThrow(() -> {
            EscrowLaunchDiscard.log(message -> { throw new IllegalStateException("broken sink"); }, "event", () -> "detail");
            EscrowLaunchDiscard.log(message -> { throw new AssertionError("broken sink"); }, "event", () -> {
                throw new IllegalArgumentException("broken detail");
            });
            return "completed";
        }));
    }

    private static final class Logs extends AbstractAppender implements AutoCloseable {
        private final Logger logger = (Logger) LogManager.getRootLogger();
        private final List<LogEvent> events = new ArrayList<>();

        Logs() {
            super("launch-discard-test", null, null, true, Property.EMPTY_ARRAY);
            start(); logger.addAppender(this);
        }

        @Override public void append(LogEvent event) {
            if (event.getMessage().getFormattedMessage().startsWith("ARCE_LAUNCH_DISCARD ")) {
                events.add(event.toImmutable());
            }
        }

        @Override public void close() { logger.removeAppender(this); stop(); }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/**
 * W179: every INFO, WARN and ERROR event of any logger while open, on any thread. Unlike {@link AuditLogCapture} it
 * attaches to the root configuration, which every additive logger reaches, so vanilla's reload listeners are seen.
 */
final class ReloadLogCapture extends AbstractAppender implements AutoCloseable {
    /** At most this many causes of a logged throwable are kept. */
    private static final int MAX_CAUSES = 8;

    /** One event: its level, logger, formatted message and the messages of its throwable's causes. */
    record Event(Level level, String logger, String message, List<String> causes) {
        boolean problem() {
            return level.isMoreSpecificThan(Level.WARN);
        }

        /** The longest text this event wrote: its message or any cause's message. */
        int longest() {
            return Math.max(message.length(), causes.stream().mapToInt(String::length).max().orElse(0));
        }
    }

    private final List<Event> events = new CopyOnWriteArrayList<>();
    private final LoggerContext context;
    private final LoggerConfig root;

    ReloadLogCapture() {
        super("arce-reload-capture-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY);
        context = ((Logger) LogManager.getRootLogger()).getContext();
        root = context.getConfiguration().getRootLogger();
        start();
        root.addAppender(this, Level.INFO, null);
        context.updateLoggers();
    }

    @Override
    public void append(LogEvent event) {
        if (!event.getLevel().isMoreSpecificThan(Level.INFO)) {
            return;
        }
        List<String> causes = new ArrayList<>();
        for (Throwable cause = event.getThrown(); cause != null && causes.size() < MAX_CAUSES; cause = cause.getCause()) {
            causes.add(String.valueOf(cause.getMessage()));
        }
        events.add(new Event(event.getLevel(), event.getLoggerName(), event.getMessage().getFormattedMessage(),
                List.copyOf(causes)));
    }

    List<Event> events() {
        return List.copyOf(events);
    }

    @Override
    public void close() {
        root.removeAppender(getName());
        context.updateLoggers();
        stop();
    }
}

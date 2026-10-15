package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

/**
 * Captures the mod's {@code ARCE_STATION_*} audit lines during a GameTest, or the lines with another prefix. Silent
 * sources and FakePlayers receive no replies, so their outcome is read from these lines (ADR-046 UI-03).
 */
public final class AuditLogCapture extends AbstractAppender implements AutoCloseable {
    private final List<String> lines = new CopyOnWriteArrayList<>();
    private final String prefix;
    private final LoggerContext context;
    private final LoggerConfig config;

    AuditLogCapture() {
        this("ARCE_STATION_");
    }

    /**
     * Attaches to the logger configuration that already governs the mod's logger. Adding the appender
     * to the logger itself would create a new configuration for it and could detach the file appenders.
     */
    public AuditLogCapture(String prefix) {
        super("arce-audit-capture-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY);
        this.prefix = prefix;
        Logger logger = (Logger) LogManager.getLogger(AdvancedRocketryCommunity.class.getName());
        context = logger.getContext();
        config = context.getConfiguration().getLoggerConfig(logger.getName());
        start();
        config.addAppender(this, null, null);
        context.updateLoggers();
    }

    @Override
    public void append(LogEvent event) {
        String message = event.getMessage().getFormattedMessage();
        if (message.startsWith(prefix)) {
            lines.add(message);
        }
    }

    public List<String> lines() {
        return List.copyOf(lines);
    }

    void clear() {
        lines.clear();
    }

    @Override
    public void close() {
        config.removeAppender(getName());
        context.updateLoggers();
        stop();
    }
}

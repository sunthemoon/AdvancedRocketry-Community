package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W44 A0 case e (N2): one ERROR with a stack trace per failure streak, counted repeats, one recovery INFO. */
class FirstEventRetryDiagnosticsTest {
    @TempDir Path root;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void theAcknowledgementBacksOffFrom200To6000TicksAndTheBarrierStaysAt200() {
        var acknowledgement = new FirstEventRetryStreak(200L, 6_000L);
        List<Long> intervals = new ArrayList<>();
        long now = 0L;
        for (int failure = 0; failure < 8; failure++) {
            assertTrue(acknowledgement.due(now));
            assertEquals(failure == 0, acknowledgement.failed(now), "only the first failure of a streak logs");
            intervals.add(acknowledgement.next() - now);
            assertFalse(acknowledgement.due(now + 199L));
            now = acknowledgement.next();
        }
        assertEquals(List.of(200L, 400L, 800L, 1_600L, 3_200L, 6_000L, 6_000L, 6_000L), intervals);
        assertEquals(8, acknowledgement.succeeded());
        assertTrue(acknowledgement.due(now));
        acknowledgement.failed(now);
        assertEquals(200L, acknowledgement.next() - now, "a new streak starts again at 200 ticks");
        var barrier = FirstEventRetryStreak.fixed(200L);
        barrier.failed(0L);
        barrier.failed(200L);
        assertEquals(400L, barrier.next());
    }

    @Test void theReceiptBarrierLogsOnceWithItsStackThenCountsThenRecoversOnce() throws Exception {
        var data = ClassicProgressionSavedData.provenAbsent();
        data.insert(warp(1, List.of(id(2))));
        Path file = root.resolve(ManagedSavedDataType.CLASSIC_PROGRESSION.fileName());
        var streak = FirstEventRetryStreak.fixed(200L);
        List<LogEvent> events = capture(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                assertFalse(ClassicFirstEventDelivery.barrierStep(data, file, (staged, target) -> {
                    throw new IOException("injected receipt move failure");
                }, directory -> { }, streak, attempt * 200L));
            }
            assertEquals(3, streak.failures());
            assertTrue(ClassicFirstEventDelivery.barrierStep(data, file, CheckedSavedDataFile::atomicMove,
                    directory -> { }, streak, 600L));
        });
        List<LogEvent> failed = matching(events, "ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED");
        assertEquals(1, failed.size());
        assertEquals(Level.ERROR, failed.get(0).getLevel());
        assertNotNull(failed.get(0).getThrown(), "the first failure carries its stack trace");
        assertEquals("ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED kinds=first_station_warp stage=flush",
                failed.get(0).getMessage().getFormattedMessage());
        List<LogEvent> recovered = matching(events, "ARCE_PROGRESSION_RECEIPT_BARRIER_RECOVERED");
        assertEquals(1, recovered.size());
        assertEquals("ARCE_PROGRESSION_RECEIPT_BARRIER_RECOVERED failures=3", recovered.get(0).getMessage()
                .getFormattedMessage());
    }

    @Test void theStationAcknowledgementLogsOnceWithItsStackThenCountsThenRecoversOnce() {
        var event = warp(1, List.of());
        var streak = new FirstEventRetryStreak(200L, 6_000L);
        var log = new ClassicFirstEventDelivery.AcknowledgementLog(FirstEventKind.FIRST_STATION_WARP, event, streak);
        List<LogEvent> events = capture(() -> {
            for (int attempt = 0; attempt < 4; attempt++) {
                log.beforeReplace(new IOException("injected acknowledgement failure"));
                ClassicFirstEventDelivery.acknowledged(FirstEventKind.FIRST_STATION_WARP, false, streak, attempt);
            }
            ClassicFirstEventDelivery.acknowledged(FirstEventKind.FIRST_STATION_WARP, true, streak, 99L);
        });
        List<LogEvent> failed = matching(events, "ARCE_FIRST_EVENT_ACK_WRITE_FAILED");
        assertEquals(1, failed.size());
        assertNotNull(failed.get(0).getThrown());
        assertEquals("ARCE_FIRST_EVENT_ACK_WRITE_FAILED kind=first_station_warp event=" + event.eventId(),
                failed.get(0).getMessage().getFormattedMessage());
        List<LogEvent> recovered = matching(events, "ARCE_FIRST_EVENT_ACK_RECOVERED");
        assertEquals(List.of("ARCE_FIRST_EVENT_ACK_RECOVERED kind=first_station_warp failures=4"),
                recovered.stream().map(value -> value.getMessage().getFormattedMessage()).toList());
    }

    private static List<LogEvent> matching(List<LogEvent> events, String marker) {
        return events.stream().filter(event -> event.getMessage().getFormattedMessage().startsWith(marker)).toList();
    }

    private static List<LogEvent> capture(Runnable action) {
        List<LogEvent> events = new ArrayList<>();
        Logger logger = (Logger) LogManager.getRootLogger();
        var appender = new AbstractAppender("w44-retry-diagnostics", null, PatternLayout.createDefaultLayout(), false,
                Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        return events;
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedDispatch;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketGovernedAcknowledgementTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void acknowledgementWaitIsSilentHeldAndOnlyBecomesLiveAfterCheckedAcknowledgement() throws Exception {
        var record = GovernedTransferFixture.create().record();
        var arm = record.nativeTransfer().orElseThrow().armed();
        var root = new EscrowLedger(arm.ledger(), 1, arm.generation(), List.of(), List.of(), List.of(),
                arm.serial(), List.of(), List.of(arm));
        var journal = new RocketTransferSavedData(); journal.put(record);
        var fences = new RocketTransferFences(); var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        try (var dispatch = new EscrowGovernedDispatch(); var logs = new Logs()) {
            for (int tick = 0; tick < 20; tick++) {
                live.add(record.transferId()); settled.add(record.transferId());
                var decision = classify(dispatch, root, journal, record);
                assertEquals("GOVERNED_JOURNAL_UNACKNOWLEDGED", decision.reason());
                fences.classifyGoverned(record, decision, live, settled);
                assertTrue(live.isEmpty()); assertTrue(settled.isEmpty());
                assertTrue(fences.ids().contains(record.transferId()));
                assertFalse(fences.permit(null, journal, record, live, settled));
                assertNull(RocketTransferRecoveryService.step(List.of(record), live, settled, fences.ids(), tick,
                        ignored -> { fail("Acknowledgement wait entered legacy readiness"); return true; }).candidate());
            }
            assertTrue(logs.events.isEmpty(), logs.events.toString());
            journal.save(directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()).toFile());
            for (int tick = 0; tick < 1_000 && !journal.acknowledgedCurrent(); tick++) {
                journal.pollWriter(tick); Thread.sleep(2);
            }
            assertTrue(journal.acknowledgedCurrent());
            var acknowledged = classify(dispatch, root, journal, record);
            assertEquals(EscrowGovernedDispatch.Route.ACTIVE, acknowledged.route());
            fences.classifyGoverned(record, acknowledged, live, settled);
            assertEquals(java.util.Set.of(record.transferId()), live);
            assertTrue(settled.isEmpty()); assertTrue(fences.ids().isEmpty());
            assertNull(fences.reason(record.transferId()));
            assertTrue(logs.events.isEmpty(), logs.events.toString());
        } finally { journal.stop(); }
    }

    @Test void otherHoldsWarnOnceAndInterleavedAcknowledgementWaitsPreserveTheirReason() {
        var record = GovernedTransferFixture.create().record();
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var waiting = new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD,
                "GOVERNED_JOURNAL_UNACKNOWLEDGED");
        for (String reason : List.of("GOVERNED_LEDGER_UNAVAILABLE", "GOVERNED_LEDGER_MISMATCH",
                "GOVERNED_ARM_MISSING", "GOVERNED_ACTION_REQUIRED", "GOVERNED_CLEANUP_REQUIRED")) {
            var fences = new RocketTransferFences();
            var held = reason.equals("GOVERNED_CLEANUP_REQUIRED")
                    ? new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.CLEANUP, null)
                    : new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD, reason);
            try (var logs = new Logs()) {
                fences.classifyGoverned(record, waiting, live, settled);
                for (int tick = 0; tick < 20; tick++) {
                    live.add(record.transferId()); settled.add(record.transferId());
                    fences.classifyGoverned(record, held, live, settled);
                    fences.classifyGoverned(record, waiting, live, settled);
                    fences.classifyGoverned(record, new EscrowGovernedDispatch.Decision(EscrowGovernedDispatch.Route.HOLD,
                            "GOVERNED_LEDGER_PENDING"), live, settled);
                    assertEquals(reason, fences.reason(record.transferId()));
                    assertTrue(fences.ids().contains(record.transferId()));
                    assertTrue(live.isEmpty()); assertTrue(settled.isEmpty());
                }
                assertEquals(1, logs.events.size(), logs.events.toString());
                assertEquals(Level.WARN, logs.events.get(0).getLevel());
                assertEquals("ARCE_TRANSFER_FENCED transfer=" + record.transferId() + " reason=" + reason
                        + " repair=offline-whole-world", logs.events.get(0).getMessage().getFormattedMessage());
            }
        }
    }

    private static EscrowGovernedDispatch.Decision classify(EscrowGovernedDispatch dispatch, EscrowLedger root,
            RocketTransferSavedData journal, RocketTransferRecord record) throws Exception {
        // Exercise the real checked dispatcher without exposing its owner-thread test seam as public API.
        var method = EscrowGovernedDispatch.class.getDeclaredMethod("classify", Supplier.class, BooleanSupplier.class,
                RocketTransferSavedData.class, RocketTransferRecord.class);
        method.setAccessible(true);
        return (EscrowGovernedDispatch.Decision) method.invoke(dispatch, (Supplier<EscrowLedger>) () -> root,
                (BooleanSupplier) () -> true, journal, record);
    }

    private static final class Logs extends AbstractAppender implements AutoCloseable {
        private final Logger logger = (Logger) LogManager.getRootLogger();
        private final List<LogEvent> events = new ArrayList<>();
        Logs() {
            super("governed-acknowledgement-test", null, null, true, Property.EMPTY_ARRAY);
            start(); logger.addAppender(this);
        }
        @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        @Override public void close() { logger.removeAppender(this); stop(); }
    }
}

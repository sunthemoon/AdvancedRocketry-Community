package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** R10 diagnostic: real F7 writer/classification with explicit A0 proof stubs, not native F6 evidence. */
class EscrowGovernedTerminalDiagnosticsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void healthyTerminalWriteAndCleanupProduceTheOtherWarningsR10Requires() throws Exception {
        var fixture = GovernedTransferFixture.create(); var record = fixture.record();
        var arm = record.nativeTransfer().orElseThrow().armed();
        var root = EscrowGovernedDispatchTest.root(record);
        var data = ResourceEscrowSavedData.load(EscrowCodec.encode(root));
        var file = directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName());
        var journal = new RocketTransferSavedData(); journal.put(record);
        EscrowArmedRestartBoundaryTest.acknowledge(journal, directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
        var fenceClass = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransferFences");
        var constructor = fenceClass.getDeclaredConstructor(); constructor.setAccessible(true);
        var fences = constructor.newInstance();
        var classify = fenceClass.getDeclaredMethod("classifyGoverned", RocketTransferRecord.class,
                EscrowGovernedDispatch.Decision.class, Set.class, Set.class); classify.setAccessible(true);
        var live = new HashSet<UUID>(); var settled = new HashSet<UUID>();
        var completed = new ArrayList<Boolean>();
        try (var writer = new EscrowLedgerWriter(file, root.writerGeneration());
             var coordinator = new EscrowCoordinator(data, writer); var dispatch = new EscrowGovernedDispatch()) {
            assertEquals(EscrowGovernedDispatch.Route.ACTIVE,
                    dispatch.classify(coordinator::ledger, () -> coordinator.governedAvailable(arm.logical()), journal, record).route());
            // Native proof coverage is tested separately. This test isolates the queue and checked F7 acknowledgement.
            var lease = journal.acknowledgedLease();
            assertTrue(coordinator.flightWrite(arm.logical(), (candidate, generation) -> EscrowFlightAuthority.finish(candidate, arm,
                    EscrowFlightTables.Outcome.CANCELLED, arm.source(), arm.epoch(), record.sourceFlightData().fuel().resourceSignature(),
                    EscrowInvariant.rocket(arm.source(), arm.logical(), arm.owner(), EscrowAdapterIds.ROCKET, record.sourceSnapshot())),
                    lease, completed::add));
            assertTrue(journal.acknowledgedCurrent()); assertTrue(writer.idle()); assertFalse(writer.unavailable());
            assertEquals(root, coordinator.ledger()); assertTrue(completed.isEmpty());
            try (var logs = new Logs()) {
                var pending = dispatch.classify(coordinator::ledger, () -> coordinator.governedAvailable(arm.logical()), journal, record);
                assertEquals("GOVERNED_LEDGER_UNAVAILABLE", pending.reason());
                classify.invoke(fences, record, pending, live, settled);
                assertTrue(live.isEmpty()); assertTrue(settled.isEmpty());
                for (long tick = 0; tick < 1_000 && completed.isEmpty(); tick++) { coordinator.tick(tick); Thread.sleep(2); }
                assertEquals(List.of(true), completed); assertFalse(writer.unavailable());
                assertEquals(EscrowFlightTables.Outcome.CANCELLED, coordinator.ledger().armedFlights().get(0).outcome());
                assertEquals(coordinator.ledger(), EscrowCodec.decode(CheckedSavedDataFile.readPayload(file,
                        ManagedSavedDataType.RESOURCE_ESCROW).orElseThrow()));
                var terminal = dispatch.classify(coordinator::ledger, () -> coordinator.governedAvailable(arm.logical()), journal, record);
                assertEquals(EscrowGovernedDispatch.Route.CLEANUP, terminal.route());
                classify.invoke(fences, record, terminal, live, settled);
                assertTrue(live.isEmpty()); assertTrue(settled.isEmpty());
                var warnings = logs.events.stream().filter(event -> event.getLevel() == Level.WARN).toList();
                assertEquals(List.of("ARCE_TRANSFER_FENCED transfer=" + record.transferId()
                                + " reason=GOVERNED_LEDGER_UNAVAILABLE repair=offline-whole-world",
                        "ARCE_TRANSFER_FENCED transfer=" + record.transferId()
                                + " reason=GOVERNED_CLEANUP_REQUIRED repair=offline-whole-world"),
                        warnings.stream().map(event -> event.getMessage().getFormattedMessage()).toList());
            }
        } finally { journal.stop(); }
    }

    private static final class Logs extends AbstractAppender implements AutoCloseable {
        private final Logger logger = (Logger) LogManager.getRootLogger();
        private final List<LogEvent> events = new ArrayList<>();
        Logs() { super("governed-terminal-diagnostic", null, null, true, Property.EMPTY_ARRAY); start(); logger.addAppender(this); }
        @Override public void append(LogEvent event) { events.add(event.toImmutable()); }
        @Override public void close() { logger.removeAppender(this); stop(); }
    }
}

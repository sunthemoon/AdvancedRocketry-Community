package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 item 5, design section 4.3: finite-long admission at E = h = MAX - 1 and MAX. Every refusal happens before
 * RAM changes; exhaustion is reported once per streak, keeps its work dirty and unproved, and never retries.
 */
@Timeout(120)
final class SatelliteRegistryCapacityTest {
    private static final long MAX = Long.MAX_VALUE;
    private static final UUID TERMINAL = id(90), GAS_ONE = id(1), GAS_TWO = id(2), GAS_THREE = id(3);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void atMaxMinusOneEveryStampSiteIsAdmittedAndCaptureMaxCoversThemWhileItsFlightRefusesEverything() throws Exception {
        var server = live(); var data = loadedAt(MAX - 1, server);
        Path file = fresh("max");
        UUID launched = launch(data);
        assertEquals(MAX - 1, startEpoch(data, launched));
        assertTrue(gas(data, GAS_TWO, id(12), 3_000).success());
        assertEquals(MAX - 1, data.mission(id(12)).orElseThrow().startEpoch());
        assertTrue(data.reconcile(TERMINAL, id(11), DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 3_000).changed());
        assertEquals(MAX - 1, ack(data, id(11)));
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.start(ground(data, 1, 3_000), 3_000)).code());
        assertEquals(MAX - 1, data.groundSurveyQuery(g -> g.job(id(101)).orElseThrow().startEpoch()).longValue());
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.claim(id(100), OWNER, device(300), 3_000)).code());
        assertEquals(MAX - 1, data.groundSurveyQuery(g -> g.job(id(100)).orElseThrow().analysis().claim().orElseThrow().epoch()).longValue());

        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(file, paused.policy));
        paused.awaitEntered(server, data);
        CompoundTag before = data.save(new CompoundTag());
        long sequence = data.mutationSequence();
        try (var log = new ErrorLines()) {
            assertEquals(SatelliteOperationCode.CAPACITY_REACHED, data.launch(UUID.randomUUID(), UUID.randomUUID(), OWNER,
                    SatelliteRegistryProofTest.definition(), io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("earth"),
                    3_001, false).code(), "A stamp of MAX is never admitted");
            assertEquals(SatelliteOperationCode.CAPACITY_REACHED, gas(data, GAS_THREE, id(13), 3_001).code());
            assertEquals(GroundSurveys.Code.CAPACITY_REACHED,
                    data.groundSurveys(g -> g.start(ground(data, 2, 3_001), 3_001)).code());
            assertEquals(GroundSurveys.Code.CAPACITY_REACHED,
                    data.groundSurveys(g -> g.cancel(id(101), OWNER, false, 3_001)).code(), "Unstamped changes also need capacity");
            assertEquals(SatelliteOperationCode.CAPACITY_REACHED, data.decommission(GAS_THREE, OWNER, true, true).code());
            assertEquals(DeliveryReconciliation.Action.NONE_REGISTRY_BLOCKED, data.reconcile(TERMINAL, id(11),
                    DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 3_001).action());
            assertEquals(before, data.save(new CompoundTag()), "A refusal changed RAM");
            assertEquals(sequence, data.mutationSequence(), "A refusal is not even a clock mutation");
            assertEquals(1, log.lines().stream().filter(line -> line.contains("ARCE_SATELLITE_REGISTRY_EPOCH_EXHAUSTED")).count(),
                    "One diagnostic per exhaustion streak, not per refusal");
        }
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(MAX, data.saveEpoch());
        assertTrue(startEpoch(data, launched) < data.saveEpoch() && data.mission(id(12)).orElseThrow().startEpoch() < MAX);
        assertEquals(DeliveryReconciliation.RegistryView.CLAIMED_ACK_DURABLE,
                DeliveryReconciliation.view(true, data.mission(id(11)), TERMINAL, data.saveEpoch()));

        // At E = MAX no changed capture or stamped mutation is admissible; a clock-only write keeps E.
        assertEquals(SatelliteOperationCode.CAPACITY_REACHED, gas(data, GAS_THREE, id(13), 3_002).code());
        assertFalse(data.completeDue(4_000).changed());
        assertTrue(data.isDirty()); assertFalse(data.flushPending());
        write(server, data, file, ATOMIC);
        assertFalse(data.isDirty()); assertEquals(MAX, data.saveEpoch());
        CompoundTag disk = CheckedSavedDataFile.readPayload(file, REGISTRY).orElseThrow();
        assertEquals(MAX, disk.getLong("save_epoch"));
        var reloaded = SatelliteMissionSavedData.load(disk);
        assertFalse(reloaded.isDirty());
        assertArrayEquals(RegistryWriterFixtures.bytes(disk), RegistryWriterFixtures.bytes(reloaded.save(new CompoundTag())),
                "An unchanged reload at the limit writes the same bytes");
    }

    @Test void unknownAtMaxAfterEarlierAdmissionReportsExhaustionOnceKeepsTheWorkAndNeverRetries() throws Exception {
        var server = live(); var data = loadedAt(MAX - 1, server);
        Path file = fresh("unknown");
        UUID launched = launch(data);
        var watch = data.watch(launched);
        try (var log = new ErrorLines()) {
            var paused = new Paused(Ends.UNKNOWN);
            assertTrue(data.requestWrite(file, paused.policy)); paused.awaitEntered(server, data);
            paused.release.countDown();
            pump(server, data, () -> !data.writerBusy());
            assertEquals(MAX - 1, data.saveEpoch());
            assertTrue(data.acknowledgement().isEmpty());
            long writes = data.writerForTests().completedWrites();
            assertThrows(ArithmeticException.class, data::captureForTests, "UNKNOWN reserved the last epoch");
            assertTrue(data.requestWrite(file, ATOMIC), "The request waits out the failure backoff");
            for (int tick = 0; tick < 450; tick++) { at(server, server.getTickCount() + 1); data.pollWriter(); }
            assertFalse(data.writerBusy(), "The refused capture dropped its request; nothing retries");
            assertFalse(data.requestWrite(file, ATOMIC), "After the backoff a request is refused at its capture");
            for (int tick = 0; tick < 50; tick++) { at(server, server.getTickCount() + 1); data.pollWriter(); }
            assertEquals(writes, data.writerForTests().completedWrites(), "Exhaustion started another write");
            assertTrue(data.isDirty()); assertTrue(data.flushPending());
            assertTrue(data.satellite(launched).isPresent(), "Existing data is retained, never reset or dropped");
            assertEquals(SatelliteOperationCode.CAPACITY_REACHED, gas(data, GAS_THREE, id(13), 3_100).code());
            assertEquals(1, log.lines().stream().filter(line -> line.contains("ARCE_SATELLITE_REGISTRY_EPOCH_EXHAUSTED")).count());
            flags(server, false, false);
            data.save(file.toFile());
            var report = data.drainReport().orElseThrow();
            assertFalse(report.success()); assertEquals(SatelliteRegistryWriter.EPOCH_CAPACITY_EXHAUSTED, report.reason());
            assertTrue(data.isDirty());
            assertFalse(data.acknowledgement().isPresent() && data.acknowledgement().orElseThrow().proves(watch));
            data.close();
        }
    }

    @Test void failedAtMaxReservesOnlyAfterAnInFlightMutationAndAClockMutationCounts() throws Exception {
        for (boolean clockDuringFlight : List.of(false, true)) {
            var server = live(); var data = loadedAt(MAX - 1, server);
            Path file = fresh("failed-" + clockDuringFlight);
            launch(data);
            var paused = new Paused(Ends.FAIL);
            assertTrue(data.requestWrite(file, paused.policy)); paused.awaitEntered(server, data);
            if (clockDuringFlight) {
                assertEquals(0, data.completeDue(5_000).completed());
                assertTrue(data.mutationSequence() > 0);
            }
            paused.release.countDown();
            pump(server, data, () -> !data.writerBusy());
            assertEquals(MAX - 1, data.saveEpoch());
            if (clockDuringFlight) {
                assertThrows(ArithmeticException.class, data::captureForTests, "The clock mutation made FAILED reserve MAX");
                assertTrue(data.requestWrite(file, ATOMIC));
                long writes = data.writerForTests().completedWrites();
                for (int tick = 0; tick < 450; tick++) { at(server, server.getTickCount() + 1); data.pollWriter(); }
                assertFalse(data.writerBusy()); assertEquals(writes, data.writerForTests().completedWrites());
                assertTrue(data.isDirty());
            } else {
                assertEquals(MAX, data.captureForTests().epoch(), "An unreserved MAX may be carried again");
                write(server, data, file, ATOMIC);
                assertEquals(MAX, data.saveEpoch()); assertFalse(data.isDirty());
            }
        }
    }

    /** A clean registry whose file carries {@code epoch}; every record was stamped at most 3, so all are durable. */
    private SatelliteMissionSavedData loadedAt(long epoch, MinecraftServer server) throws Exception {
        var source = SatelliteMissionSavedData.create(0);
        Path file = fresh("source-" + epoch + "-" + UUID.randomUUID());
        for (UUID craft : List.of(GAS_ONE, GAS_TWO, GAS_THREE)) {
            assertTrue(source.launchIdle(time -> RegistryWriterFixtures.idle(craft, time), 0).success());
        }
        assertTrue(gas(source, GAS_ONE, id(11), 0).success());
        assertEquals(GroundSurveys.Code.SUCCESS, source.groundSurveys(g -> g.start(ground(source, 0, 1), 1)).code());
        source.flush(file);
        assertTrue(source.resources(m -> m.claimResource(id(11), OWNER, TERMINAL, rewards -> null, 2_000)).success());
        for (int tick = 2; tick < 22; tick++) {
            final int now = tick;
            assertEquals(GroundSurveys.Code.SUCCESS, source.groundSurveys(g -> g.advanceSurvey(id(100), device(200), 24_000, now)).code());
        }
        CompoundTag root = source.save(new CompoundTag());
        root.putLong("save_epoch", epoch);
        var data = SatelliteMissionSavedData.load(root);
        assertTrue(data.operational()); assertFalse(data.isDirty());
        data.bindServerForTests(server);
        return data;
    }

    private static SatelliteOperationResult gas(SatelliteMissionSavedData data, UUID satellite, UUID mission, long time) {
        return data.resources(m -> m.startResource(new ResourceMissions.ResourceStart(satellite, mission, OWNER,
                MissionKind.GAS, SYSTEM, Optional.empty(), YIELD, "gas-v1/test", 1000, 1, TERMINAL, Optional.empty()), time));
    }

    private static GroundSurveys.Start ground(SatelliteMissionSavedData data, long index, long observed) {
        long created = data.groundSurveyQuery(g -> g.previewTime(observed));
        return RegistryWriterFixtures.ground(index, created);
    }

    private static long ack(SatelliteMissionSavedData data, UUID mission) {
        return ((MissionPayload.Resource) data.mission(mission).orElseThrow().payload()).ackEpoch().orElseThrow();
    }

    private Path fresh(String name) {
        try { return Files.createDirectories(directory.resolve(name)).resolve(REGISTRY.fileName()); }
        catch (java.io.IOException exception) { throw new java.io.UncheckedIOException(exception); }
    }

    /** ERROR lines of the registry writer. */
    static final class ErrorLines implements AutoCloseable {
        private final org.apache.logging.log4j.core.Logger logger = (org.apache.logging.log4j.core.Logger)
                org.apache.logging.log4j.LogManager.getLogger(SatelliteRegistryWriter.class);
        private final List<String> lines = new CopyOnWriteArrayList<>();
        private final org.apache.logging.log4j.core.appender.AbstractAppender appender =
                new org.apache.logging.log4j.core.appender.AbstractAppender("w129b-capacity", null, null, true,
                        org.apache.logging.log4j.core.config.Property.EMPTY_ARRAY) {
                    @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
                        if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.WARN)) {
                            lines.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
                        }
                    }
                };
        ErrorLines() { appender.start(); logger.addAppender(appender); }
        List<String> lines() { return new ArrayList<>(lines); }
        @Override public void close() { logger.removeAppender(appender); appender.stop(); }
    }

}

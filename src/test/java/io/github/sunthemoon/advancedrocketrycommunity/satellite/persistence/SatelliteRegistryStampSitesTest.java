package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.RegistryWriterHarness.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * ADR-074 item 5, design section 4.3: while a capture carrying w = E + 1 is in flight, every stamp site reads the
 * snapshot epoch w and every durability check reads E. Each site below would give the opposite answer if it read the
 * other epoch. ResearchService's two checks read {@code saveEpoch()}, which is E by construction.
 */
@Timeout(120)
final class SatelliteRegistryStampSitesTest {
    private static final UUID TERMINAL = id(90), GAS_ONE = id(1), GAS_TWO = id(2), GAS_THREE = id(3), GAS_FOUR = id(4),
            GAS_FIVE = id(5), SURVEY = id(6);
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyStampReadsTheSnapshotEpochAndEveryCheckReadsTheDurableEpochDuringAFlight() {
        var server = live(); var data = registry(server);
        Path file = directory.resolve(REGISTRY.fileName());
        for (UUID craft : List.of(GAS_ONE, GAS_TWO, GAS_THREE, GAS_FOUR, GAS_FIVE)) {
            assertTrue(data.launchIdle(time -> RegistryWriterFixtures.idle(craft, time), 0).success());
        }
        assertTrue(data.launchIdle(time -> survey(time), 0).success());
        UUID dataSatellite = launch(data);
        UUID owner = data.satellite(dataSatellite).orElseThrow().ownerId();
        assertTrue(data.cancel(SatelliteManager.initialMissionId(dataSatellite), owner, false, 1).success());
        assertTrue(gas(data, GAS_ONE, id(11), 1).success());
        assertTrue(gas(data, GAS_TWO, id(12), 1).success());
        assertTrue(gas(data, GAS_FOUR, id(14), 1).success());
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.start(ground(data, 0), 1)).code());
        write(server, data, file, ATOMIC);
        for (int tick = 2; tick < 22; tick++) {
            final int now = tick;
            assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.advanceSurvey(id(100), device(200), 24_000, now)).code());
        }
        assertTrue(data.resources(m -> m.claimResource(id(11), OWNER, TERMINAL, rewards -> null, 2_000)).success());
        assertTrue(data.resources(m -> m.claimResource(id(12), OWNER, TERMINAL, rewards -> null, 2_000)).success());
        assertTrue(data.resources(m -> m.claimResource(id(14), OWNER, TERMINAL, rewards -> null, 2_000)).success());
        assertTrue(data.reconcile(TERMINAL, id(12), DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_000).changed());
        // Stamped while idle, so at exactly E: still waiting for a later file during the flight.
        assertTrue(gas(data, GAS_FIVE, id(15), 2_001).success());
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.start(ground(data, 1), 2_001)).code());
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.claim(id(100), OWNER, device(300), 2_001)).code());
        long e = data.saveEpoch();
        assertEquals(e, ack(data, id(12)), "The idle acknowledgement stamps E");

        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(file, paused.policy));
        paused.awaitEntered(server, data);
        long w = e + 1;

        // Stamps read w.
        UUID launched = launch(data);
        assertEquals(w, startEpoch(data, launched), "SatelliteLaunchAdmission.java:35");
        UUID dataMission = UUID.randomUUID();
        assertTrue(data.startMission(dataSatellite, dataMission, owner, SatelliteRegistryProofTest.definition(),
                ModIdentity.id("earth"), 2_002, false).success());
        assertEquals(w, data.mission(dataMission).orElseThrow().startEpoch(), "SatelliteMissionRegistry.java:354");
        assertTrue(gas(data, GAS_THREE, id(13), 2_002).success());
        assertEquals(w, data.mission(id(13)).orElseThrow().startEpoch(), "ResourceMissions.java:153");
        UUID surveyMission = id(16);
        assertTrue(data.resources(m -> m.startSurvey(surveyStart(surveyMission), 2_002)).success());
        assertEquals(w, data.mission(surveyMission).orElseThrow().startEpoch(), "ResourceMissions.java:107");
        assertEquals(DeliveryReconciliation.Action.ACKNOWLEDGE, data.reconcile(TERMINAL, id(14),
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_002).action());
        assertEquals(w, ack(data, id(14)), "ResourceMissions.java:298");
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.start(ground(data, 2), 2_002)).code());
        assertEquals(w, data.groundSurveyQuery(g -> g.job(id(102)).orElseThrow().startEpoch()).longValue(), "GroundSurveys.java:125");
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.rebindAnalysis(id(100), OWNER, device(301), 2_002)).code());
        assertEquals(w, claimEpoch(data, id(100)), "GroundSurveys.java:232");

        // Checks read E.
        assertEquals(DeliveryReconciliation.Action.WAIT, data.reconcile(TERMINAL, id(12),
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_002).action(),
                "ResourceMissions.java:290: an acknowledgement stamped E is not durable yet");
        assertEquals(SatelliteOperationCode.AWAITING_WORLD_SAVE,
                data.resources(m -> m.claimResource(id(15), OWNER, TERMINAL, rewards -> null, 4_000)).code(),
                "ResourceMissions.java:220");
        assertEquals(GroundSurveys.Code.WAITING_SAVE,
                data.groundSurveys(g -> g.advanceSurvey(id(101), device(201), 24_000, 2_003)).code(), "GroundSurveys.java:154");
        assertEquals(GroundSurveys.Code.WAITING_SAVE,
                data.groundSurveys(g -> g.advanceAnalysis(id(100), device(301), 2_003)).code(), "GroundSurveys.java:249");
        assertTrue(data.mission(id(12)).isPresent(), "SatelliteMissionRegistry.java:376: no pruning before durability");

        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
        assertEquals(w, data.saveEpoch());
        // After publication the E stamps are durable and the w stamps still wait for the next file.
        assertEquals(DeliveryReconciliation.Action.DROP_RECEIPT, data.reconcile(TERMINAL, id(12),
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_004).action());
        assertEquals(DeliveryReconciliation.Action.WAIT, data.reconcile(TERMINAL, id(14),
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_004).action());
        assertEquals(GroundSurveys.Code.WAITING_SAVE,
                data.groundSurveys(g -> g.advanceAnalysis(id(100), device(301), 2_005)).code());
        write(server, data, file, ATOMIC);
        assertEquals(w + 1, data.saveEpoch());
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.advanceAnalysis(id(100), device(301), 2_006)).code());
        assertEquals(DeliveryReconciliation.Action.DROP_RECEIPT, data.reconcile(TERMINAL, id(14),
                DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(), 2_006).action());
    }

    @Test void theClaimStampReadsTheSnapshotEpoch() {
        var server = live(); var data = registry(server);
        Path file = directory.resolve(REGISTRY.fileName());
        assertTrue(data.launchIdle(time -> RegistryWriterFixtures.idle(GAS_ONE, time), 0).success());
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.start(ground(data, 0), 1)).code());
        write(server, data, file, ATOMIC);
        for (int tick = 2; tick < 22; tick++) {
            final int now = tick;
            assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.advanceSurvey(id(100), device(200), 24_000, now)).code());
        }
        long e = data.saveEpoch();
        var paused = new Paused(Ends.COMMIT);
        assertTrue(data.requestWrite(file, paused.policy));
        paused.awaitEntered(server, data);
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.claim(id(100), OWNER, device(300), 30)).code());
        assertEquals(e + 1, claimEpoch(data, id(100)), "GroundSurveys.java:214");
        paused.release.countDown();
        pump(server, data, () -> !data.writerBusy());
    }

    private static long ack(SatelliteMissionSavedData data, UUID mission) {
        return ((MissionPayload.Resource) data.mission(mission).orElseThrow().payload()).ackEpoch().orElseThrow();
    }

    private static long claimEpoch(SatelliteMissionSavedData data, UUID job) {
        return data.groundSurveyQuery(g -> g.job(job).orElseThrow().analysis().claim().orElseThrow().epoch()).longValue();
    }

    private static SatelliteOperationResult gas(SatelliteMissionSavedData data, UUID satellite, UUID mission, long time) {
        return data.resources(m -> m.startResource(new ResourceMissions.ResourceStart(satellite, mission, OWNER,
                MissionKind.GAS, SYSTEM, Optional.empty(), YIELD, "gas-v1/test", 1000, 1, TERMINAL, Optional.empty()), time));
    }

    private static GroundSurveys.Start ground(SatelliteMissionSavedData data, long index) {
        long created = data.groundSurveyQuery(g -> g.previewTime(index == 0 ? 1 : index == 1 ? 2_001 : 2_002));
        return RegistryWriterFixtures.ground(index, created);
    }

    private static SatelliteState survey(long time) {
        return SatelliteState.launchIdle(SURVEY, ModIdentity.id("survey_satellite"), OWNER, time, SYSTEM,
                new SatelliteBlueprint(List.of(TYPE), false, new SatelliteStats(4, 10_720, 1_000, 1, 10)),
                new SatelliteKindState.Survey(0, 0, 1_000, 32, 8));
    }

    private static ResourceMissions.SurveyStart surveyStart(UUID mission) {
        return new ResourceMissions.SurveyStart(SURVEY, mission, OWNER, SYSTEM, VERSION, 1, 100, 1,
                (count, createdAt) -> java.util.stream.IntStream.range(0, count).<AsteroidInstance>mapToObj(index -> new AsteroidInstance(
                        SatelliteLimits.INSTANCE_SCHEMA_VERSION, new UUID(77, index), OWNER, SYSTEM, TYPE, VERSION, VERSION,
                        index, YIELD, createdAt, OptionalLong.empty(), InstanceState.PENDING, InstanceOrigin.survey(mission),
                        Optional.empty())).toList());
    }

}

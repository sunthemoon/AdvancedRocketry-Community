package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** N2: durability stamps must wait for a file containing them, including after an UNKNOWN checkpoint. */
final class SatelliteRegistryEpochTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    private static final CheckedSavedDataPolicy NORMAL = CheckedSavedDataPolicy.atomic(TYPE);
    @TempDir Path temporary;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void freshResolutionCarriesMissionAcknowledgementAndGroundSurveyStampsAcrossReload() {
        for (boolean unknown : List.of(false, true)) {
            for (String entry : List.of("checkpoint", "barrier", "coalesced", "autosave", "stop")) {
                Path file = temporary.resolve(TYPE.fileName());
                SatelliteMissionSavedData data = fixture(file);
                long epoch = data.saveEpoch();
                assertTrue(data.resources(m -> m.claimResource(id(11), OWNER, id(90), rewards -> null, 1000)).success());
                var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                    if (unknown) { CheckedSavedDataFile.atomicMove(staged, target); }
                    throw new IOException("registry cut");
                }, (path, type) -> {
                    if (unknown) { throw new IllegalStateException("readback unavailable"); }
                    return CheckedSavedDataFile.readPayload(path, type);
                });
                var watch = data.watch(id(1));
                assertEquals(unknown ? SatelliteRegistryProof.Outcome.UNKNOWN : SatelliteRegistryProof.Outcome.FAILED,
                        data.checkpoint(file, List.of(watch), policy, directory -> { }).outcome());

                assertTrue(data.reconcile(id(90), id(11), DeliveryReconciliation.ReceiptView.PERSISTED,
                        Optional.empty(), 1001).changed());
                assertTrue(data.resources(m -> m.startResource(start(id(2), id(12)), 1001)).success());
                assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.start(ground(1), 1001)).code());
                assertEquals(GroundSurveys.Code.SUCCESS,
                        data.groundSurveys(g -> g.claim(id(100), OWNER, device(300), 1001)).code());
                assertEquals(epoch, data.saveEpoch());
                assertEquals(epoch, data.mission(id(12)).orElseThrow().startEpoch());
                assertEquals(epoch, ((MissionPayload.Resource) data.mission(id(11)).orElseThrow().payload()).ackEpoch().orElseThrow());
                assertEquals(epoch, data.groundSurveyQuery(g -> g.job(id(101)).orElseThrow().startEpoch()).longValue());
                assertEquals(DeliveryReconciliation.Action.WAIT, decide(data, DeliveryReconciliation.ReceiptView.PERSISTED));
                assertEquals(GroundSurveys.Code.WAITING_SAVE,
                        data.groundSurveys(g -> g.advanceSurvey(id(101), device(201), 24000, 1002)).code());
                assertEquals(GroundSurveys.Code.WAITING_SAVE,
                        data.groundSurveys(g -> g.advanceAnalysis(id(100), device(300), 1002)).code());
                assertEquals(SatelliteOperationCode.AWAITING_WORLD_SAVE,
                        data.resources(m -> m.claimResource(id(12), OWNER, id(90), rewards -> null, 2001)).code());
                data.applyLimits(new RegistryLimits(1024, 64, 1, 3072, 4096, 256, 2048, 16, 10, 2));
                data.completeDue(10000);
                assertTrue(data.mission(id(11)).isPresent(), "Pending acknowledgement is not prunable");
                assertTrue(data.isDirty()); assertTrue(data.flushPending());

                switch (entry) {
                    case "checkpoint" -> assertTrue(data.checkpoint(file, List.of(watch), NORMAL, directory -> { }).proves(watch));
                    case "autosave", "stop" -> data.save(file.toFile());
                    default -> data.flush(file);
                }
                assertEquals(epoch + (unknown ? 2 : 1), data.saveEpoch(), entry);
                assertFalse(data.isDirty()); assertFalse(data.flushPending());
                var loaded = SatelliteMissionSavedData.load(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
                assertTrue(loaded.operational()); assertFalse(loaded.isDirty());
                assertEquals(data.save(new CompoundTag()), loaded.save(new CompoundTag()));
                assertEquals(DeliveryReconciliation.Action.DROP_RECEIPT, decide(loaded, DeliveryReconciliation.ReceiptView.PERSISTED));
                assertEquals(DeliveryReconciliation.Action.NONE, decide(loaded, DeliveryReconciliation.ReceiptView.NONE),
                        "Dropping the terminal receipt cannot rematerialize the acknowledged reward after restart");
                assertEquals(SatelliteOperationCode.SUCCESS,
                        loaded.resources(m -> m.claimResource(id(12), OWNER, id(90), rewards -> null, 10001)).code());
                assertEquals(GroundSurveys.Code.SUCCESS,
                        loaded.groundSurveys(g -> g.advanceSurvey(id(101), device(201), 24000, 10001)).code());
                assertEquals(GroundSurveys.Code.SUCCESS,
                        loaded.groundSurveys(g -> g.advanceAnalysis(id(100), device(300), 10001)).code());
                assertEquals(InstanceOrigin.ground(id(101)), loaded.instance(id(1001)).orElseThrow().origin());
            }
        }
    }

    private SatelliteMissionSavedData fixture(Path file) {
        var data = SatelliteMissionSavedData.create(0);
        for (long id : List.of(1L, 2L)) {
            assertTrue(data.launchIdle(time -> SatelliteState.launchIdle(id(id), ModIdentity.id("gas_harvester"), OWNER,
                    time, SYSTEM, new SatelliteBlueprint(List.of(GroundSurveyFixtures.TYPE), false, new SatelliteStats(4, 10720, 1000, 1, 10)),
                    new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER)), 0).success());
        }
        assertTrue(data.resources(m -> m.startResource(start(id(1), id(11)), 0)).success());
        assertEquals(GroundSurveys.Code.SUCCESS, data.groundSurveys(g -> g.start(ground(0), 0)).code());
        data.flush(file);
        for (int tick = 1; tick <= 20; tick++) {
            final int now = tick;
            assertEquals(GroundSurveys.Code.SUCCESS,
                    data.groundSurveys(g -> g.advanceSurvey(id(100), device(200), 24000, now)).code());
        }
        data.flush(file);
        return data;
    }
    private static GroundSurveys.Start ground(long index) {
        return new GroundSurveys.Start(id(100 + index), OWNER, device(200 + index),
                pending(index, OWNER, InstanceOrigin.ground(id(100 + index))), 20, 20, 2, 20, 120);
    }
    private static ResourceMissions.ResourceStart start(UUID satellite, UUID mission) {
        return new ResourceMissions.ResourceStart(satellite, mission, OWNER, MissionKind.GAS, SYSTEM, Optional.empty(),
                YIELD, "gas-v1/test", 1000, 1, id(90), Optional.empty());
    }
    private static DeliveryReconciliation.Action decide(SatelliteMissionSavedData data, DeliveryReconciliation.ReceiptView receipt) {
        return DeliveryReconciliation.decide(DeliveryReconciliation.view(true, data.mission(id(11)), id(90), data.saveEpoch()), receipt);
    }
}

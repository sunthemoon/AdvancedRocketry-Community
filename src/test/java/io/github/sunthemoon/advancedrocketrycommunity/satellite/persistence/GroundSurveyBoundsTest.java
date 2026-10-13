package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GroundSurveyBoundsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void encodedJobCountAccepts2048AndPreservesAnOverBudgetRootButRefuses2049() {
        List<GroundSurveyJob> jobs = new ArrayList<>();
        for (int i = 0; i < 2_048; i++) { jobs.add(ready(i, OWNER)); }
        CompoundTag root = root(jobs, List.of());
        var loaded = SatelliteMissionSavedData.load(root);
        assertTrue(loaded.operational());
        assertEquals(2_048, loaded.groundSurveys().size());
        assertEquals(root, loaded.save(new CompoundTag()));
        root.getList("ground_surveys", Tag.TAG_COMPOUND).add(GroundSurveyCodec.encode(ready(2_048, OWNER)));
        loaded = SatelliteMissionSavedData.load(root);
        assertFalse(loaded.operational());
        assertEquals(root, loaded.save(new CompoundTag()));
    }

    @Test void encodedRecordExactly4096BytesAndOneByteAboveAreCheckedBeforeAnyDecode() {
        CompoundTag atLimit = GroundSurveyCodec.encode(active(0, OWNER));
        atLimit.putByteArray("unknown_padding", new byte[0]);
        atLimit.putByteArray("unknown_padding", new byte[4_096 - SatelliteNbtSize.uncompressedBytes(atLimit)]);
        assertEquals(4_096, SatelliteNbtSize.uncompressedBytes(atLimit));
        // Even at the byte bound the exact-key rule remains in force; the extra tag is never ignored.
        assertThrows(IllegalArgumentException.class, () -> GroundSurveyCodec.decode(atLimit));
        atLimit.putByteArray("unknown_padding", new byte[atLimit.getByteArray("unknown_padding").length + 1]);
        assertEquals(4_097, SatelliteNbtSize.uncompressedBytes(atLimit));
        assertThrows(IllegalArgumentException.class, () -> GroundSurveyCodec.decode(atLimit));
    }

    @Test void eachNumericBoundHasItsExactEndpointsAndAdjacentRefusals() {
        for (int ticks : List.of(1, 72_000)) {
            for (int energy : List.of(1, 10_000)) {
                for (int credit : List.of(1, 10_000)) {
                    var analysis = new GroundSurveyJob.Analysis(GroundSurveyJob.AnalysisState.WAITING,
                            ticks, energy, credit, 0, Optional.empty());
                    assertEquals(ticks, analysis.ticks());
                    assertEquals(energy, analysis.energy());
                    assertEquals(credit, analysis.credit());
                }
            }
        }
        for (int ticks : List.of(0, 72_001)) {
            assertThrows(IllegalArgumentException.class, () -> new GroundSurveyJob.Analysis(
                    GroundSurveyJob.AnalysisState.WAITING, ticks, 1, 1, 0, Optional.empty()));
        }
        for (int value : List.of(0, 10_001)) {
            assertThrows(IllegalArgumentException.class, () -> new GroundSurveyJob.Analysis(
                    GroundSurveyJob.AnalysisState.WAITING, 1, value, 1, 0, Optional.empty()));
            assertThrows(IllegalArgumentException.class, () -> new GroundSurveyJob.Analysis(
                    GroundSurveyJob.AnalysisState.WAITING, 1, 1, value, 0, Optional.empty()));
        }
        assertThrows(IllegalArgumentException.class, () -> new GroundSurveyJob.Claim(0, device(300)));
        assertEquals(1, new GroundSurveyJob.Claim(1, device(300)).epoch());
        assertThrows(IllegalArgumentException.class, () -> new GroundSurveyJob.Quarantine("", GroundSurveyJob.Phase.ACTIVE));
    }

    @Test void processorBusyAndRebindBusyRefuseAndLifetimeCreditCapacityDoesNotResetTheClaim() {
        GroundSurveyJob first = running(0, OWNER, device(300));
        GroundSurveyJob second = ready(1, OWNER);
        var registry = SatelliteRegistryPayload.decodeCurrent(root(List.of(first, second),
                List.of(instance(first, InstanceState.AVAILABLE), instance(second, InstanceState.AVAILABLE))));
        assertEquals(GroundSurveys.Code.PROCESSOR_BUSY, registry.groundSurveys().claim(second.jobId(), OWNER,
                device(300), 21).code());
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().claim(second.jobId(), OWNER, device(301), 21).code());
        assertEquals(GroundSurveys.Code.PROCESSOR_BUSY, registry.groundSurveys().rebindAnalysis(second.jobId(), OWNER,
                device(300), 22).code());
        GroundSurveyJob last = first.analyzed(first.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 1,
                first.analysis().claim()), 20);
        var full = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        full.restoreAccount(new ResearchAccount(1, OWNER, 0, 1_000_000_000, 1_000_000_000));
        full.restoreInstance(instance(last, InstanceState.AVAILABLE)); full.groundSurveys().restore(last);
        CompoundTag before = SatelliteRegistryPayload.encodeCurrent(full, new CompoundTag());
        assertEquals(GroundSurveys.Code.ACCOUNT_FULL, full.groundSurveys().advanceAnalysis(last.jobId(), device(300), 30).code());
        assertEquals(before, SatelliteRegistryPayload.encodeCurrent(full, new CompoundTag()));
    }

    @Test void lifecycleAndRootPreflightRefusalsCreateNoAccountInstanceReservationOrClockChange() {
        var sizer = new RecordSizer() {
            public int satelliteBytes(SatelliteState state) { return 1; }
            public int missionBytes(MissionState state) { return 1; }
            public int instanceBytes(AsteroidInstance instance) { return 4_097; }
        };
        var registry = SatelliteMissionRegistry.create(0, sizer);
        var start = new GroundSurveys.Start(id(100), OWNER, device(200), pending(0, OWNER, InstanceOrigin.ground(id(100))),
                20, 20, 2, 20, 120);
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, registry.groundSurveys().start(start, 0).code());
        assertTrue(registry.instances().isEmpty()); assertTrue(registry.accounts().isEmpty());
        assertEquals(0, registry.reservedBytes("GROUND_SURVEYS"));
        var rootRefuser = new RecordSizer() {
            public int satelliteBytes(SatelliteState state) { return 1; }
            public int missionBytes(MissionState state) { return 1; }
            public int instanceBytes(AsteroidInstance instance) { return 4_096; }
            public boolean groundMutationFits(SatelliteMissionRegistry r, GroundSurveyJob j, AsteroidInstance i,
                    boolean remove, ResearchAccount account, long now, long observedTime) { return false; }
        };
        registry = SatelliteMissionRegistry.create(0, rootRefuser);
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, registry.groundSurveys().start(start, 0).code());
        assertTrue(registry.instances().isEmpty()); assertTrue(registry.accounts().isEmpty());
        assertEquals(0, registry.reservedBytes("GROUND_SURVEYS"));
        assertEquals(1, registry.saveEpoch()); assertEquals(0, registry.logicalGameTime());
    }

    @Test void fullAccountCountRefusesNewOwnerAndRetainsAll4096Accounts() {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        for (int i = 0; i < SatelliteLimits.MAX_RESEARCH_ACCOUNTS; i++) {
            registry.restoreAccount(ResearchAccount.empty(id(10_000 + i)));
        }
        var start = new GroundSurveys.Start(id(100), OWNER, device(200), pending(0, OWNER, InstanceOrigin.ground(id(100))),
                20, 20, 2, 20, 120);
        assertEquals(GroundSurveys.Code.CAPACITY_REACHED, registry.groundSurveys().start(start, 0).code());
        assertEquals(4_096, registry.accounts().size());
        assertTrue(registry.instances().isEmpty());
    }

    @Test void groundInstanceReleaseChecksRootAndEpochBeforePublishingOrAdvancingTime() {
        var rootRefuser = new RecordSizer() {
            public int satelliteBytes(SatelliteState state) { return 1; }
            public int missionBytes(MissionState state) { return 1; }
            public int instanceBytes(AsteroidInstance instance) { return 4_096; }
            public boolean groundMutationFits(SatelliteMissionRegistry r, GroundSurveyJob j, AsteroidInstance i,
                    boolean remove, ResearchAccount account, long now, long observedTime) { return false; }
        };
        for (var registry : List.of(SatelliteMissionRegistry.create(0, rootRefuser),
                SatelliteMissionRegistry.restore(0, 0, Long.MAX_VALUE, CodecRecordSizer.INSTANCE))) {
            GroundSurveyJob job = ready(0, OWNER);
            registry.groundSurveys().restore(job);
            registry.restoreInstance(instance(job, InstanceState.AVAILABLE).quarantine());
            AsteroidInstance before = registry.instance(job.instanceId()).orElseThrow();
            var result = registry.resources().releaseInstance(job.instanceId(), 24_000, 50);
            assertFalse(result.changed());
            assertTrue(result.code() == SatelliteOperationCode.STORAGE_BUDGET
                    || result.code() == SatelliteOperationCode.CAPACITY_REACHED);
            assertEquals(before, registry.instance(job.instanceId()).orElseThrow());
            assertEquals(0, registry.logicalGameTime());
            assertEquals(0, registry.lastObservedGameTime());
        }
    }

    @Test void retainedBadCancellationAndUnrepresentableRetentionDeadlineAreNeverPruned() {
        GroundSurveyJob cancelled = active(0, OWNER).cancelled(1);
        var registry = SatelliteRegistryPayload.decodeCurrent(root(List.of(cancelled),
                List.of(instance(cancelled, InstanceState.PENDING))));
        registry.completeDue(1_201);
        assertEquals(1, registry.groundSurveys().all().size());
        assertEquals(InstanceState.QUARANTINED, registry.instance(cancelled.instanceId()).orElseThrow().state());
        var overflow = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        overflow.groundSurveys().restore(active(0, OWNER).cancelled(Long.MAX_VALUE));
        overflow.completeDue(Long.MAX_VALUE);
        assertEquals(1, overflow.groundSurveys().all().size());
    }
}

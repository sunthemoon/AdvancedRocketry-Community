package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class GroundPreflightTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({"0,false", "0,true", "1,false", "1,true", "8192,false", "8192,true"})
    void startCountsBothNewRecordsAndOnlyANewAccount(int excess, boolean hasAccount) {
        var sizer = new SizedMissions();
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        if (hasAccount) { registry.restoreAccount(ResearchAccount.empty(OWNER)); }
        var request = GroundPreflightBenchmark.start();
        long added = sizer.groundSurveyBytes(active(0, OWNER)) + sizer.instanceBytes(request.instance())
                + (hasAccount ? 0 : RecordSizer.ACCOUNT_BYTES);
        fillReservations(registry, sizer, SatelliteLimits.MAX_REGISTRY_NBT_BYTES + (long) excess - added);
        long before = GroundPreflightBenchmark.reservationBound(registry);
        assertEquals(SatelliteLimits.MAX_REGISTRY_NBT_BYTES + (long) excess, before + added);
        GroundSurveyJob candidate = active(0, OWNER);
        assertTrue(CodecRecordSizer.INSTANCE.groundMutationFits(registry, candidate, request.instance(), false,
                registry.account(OWNER), 0, 0), "The previous whole-root admission accepts the same candidate");

        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(request, 0).code());
        assertEquals(excess == 0 ? 0 : 1, sizer.rootChecks);
        assertEquals(before + added, GroundPreflightBenchmark.reservationBound(registry));
        assertEquals(1, registry.accounts().size());
        assertEquals(InstanceState.PENDING, registry.instance(request.instance().instanceId()).orElseThrow().state());
        assertEquals(1, registry.groundSurveys().all().size());
        assertRoundTrip(registry);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 8192})
    void finalAnalysisCountsTheNewAccountWithoutReservingExistingRecordsAgain(int excess) {
        var sizer = new SizedMissions();
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        GroundSurveyJob job = lastAnalysis();
        registry.restoreInstance(instance(job, InstanceState.AVAILABLE));
        registry.groundSurveys().restore(job);
        fillReservations(registry, sizer, SatelliteLimits.MAX_REGISTRY_NBT_BYTES + (long) excess - RecordSizer.ACCOUNT_BYTES);
        assertTrue(registry.accounts().isEmpty());
        long before = GroundPreflightBenchmark.reservationBound(registry);
        var candidate = job.analyzed(job.analysis().with(GroundSurveyJob.AnalysisState.CREDITED, 2, job.analysis().claim()), 30);
        assertTrue(CodecRecordSizer.INSTANCE.groundMutationFits(registry, candidate, null, false,
                ResearchAccount.empty(OWNER).creditAndSpend(120, 0), 30, 30));

        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().advanceAnalysis(job.jobId(), device(300), 30).code());
        assertEquals(excess == 0 ? 0 : 1, sizer.rootChecks);
        assertEquals(before + RecordSizer.ACCOUNT_BYTES, GroundPreflightBenchmark.reservationBound(registry));
        assertEquals(120, registry.account(OWNER).balance());
        assertEquals(GroundSurveyJob.Phase.PROCESSED, registry.groundSurveys().job(job.jobId()).orElseThrow().phase());
        assertRoundTrip(registry);
    }

    @ParameterizedTest
    @ValueSource(strings = {"START", "ACCOUNT", "ADVANCE"})
    void failedWholeRootFallbackLeavesAllStateAndReservationsUnchanged(String operation) {
        var sizer = new SizedMissions();
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        if (!operation.equals("ACCOUNT")) { registry.restoreAccount(ResearchAccount.empty(OWNER)); }
        GroundSurveyJob job = operation.equals("ACCOUNT") ? lastAnalysis() : active(0, OWNER);
        if (!operation.equals("START")) {
            registry.restoreInstance(instance(job, operation.equals("ACCOUNT") ? InstanceState.AVAILABLE : InstanceState.PENDING));
            registry.groundSurveys().restore(job);
        }
        fillReservations(registry, sizer, SatelliteLimits.MAX_REGISTRY_NBT_BYTES + 8192L);
        CompoundTag before = encoded(registry);
        long bound = GroundPreflightBenchmark.reservationBound(registry);
        long version = registry.groundSurveys().version();
        long sequence = registry.epochs().stateChanges();
        sizer.refuse = true;
        GroundSurveys.Result result = switch (operation) {
            case "START" -> registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0);
            case "ACCOUNT" -> registry.groundSurveys().advanceAnalysis(job.jobId(), device(300), 30);
            default -> registry.groundSurveys().advanceSurvey(job.jobId(), job.source(), 24_000, 1);
        };
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, result.code());
        assertFalse(result.changed());
        assertEquals(1, sizer.rootChecks);
        assertEquals(before, encoded(registry));
        assertEquals(bound, GroundPreflightBenchmark.reservationBound(registry));
        assertEquals(version, registry.groundSurveys().version());
        assertEquals(sequence, registry.epochs().stateChanges());
    }

    private static GroundSurveyJob lastAnalysis() {
        GroundSurveyJob job = running(0, OWNER, device(300));
        return job.analyzed(job.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 1, job.analysis().claim()), 20);
    }

    /** Valid per-record upper bounds, but a restored mission section above its admission budget. */
    private static void fillReservations(SatelliteMissionRegistry registry, SizedMissions sizer, long target) {
        long remaining = target - GroundPreflightBenchmark.reservationBound(registry);
        int index = 0;
        while (remaining > 0) {
            int reservation = (int) Math.min(4096, remaining);
            if (remaining > 4096 && remaining < 6144) { reservation = 2048; }
            MissionState mission = new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, new UUID(168, index),
                    new UUID(169, index++), OWNER, TYPE, MissionKind.DATA, SYSTEM, Optional.empty(), 0, 0, 20, 1,
                    MissionStatus.CLAIMED, OptionalLong.of(20), OptionalLong.of(20), MissionState.DATA_REWARD_VERSION,
                    Optional.empty(), new MissionPayload.Data(1, 1, false));
            assertTrue(reservation >= CodecRecordSizer.INSTANCE.missionBytes(mission));
            sizer.reservation = reservation;
            registry.restoreMission(mission);
            remaining -= reservation;
        }
        assertEquals(target, GroundPreflightBenchmark.reservationBound(registry));
    }

    private static CompoundTag encoded(SatelliteMissionRegistry registry) {
        return SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
    }

    private static void assertRoundTrip(SatelliteMissionRegistry registry) {
        CompoundTag root = encoded(registry);
        assertEquals(root, encoded(SatelliteRegistryPayload.decodeCurrent(root)));
    }

    private static final class SizedMissions extends GroundPreflightBenchmark.CountingSizer {
        int reservation;
        boolean refuse;
        @Override public int missionBytes(MissionState value) { return reservation; }
        @Override public boolean groundMutationFits(SatelliteMissionRegistry registry, GroundSurveyJob job,
                AsteroidInstance instance, boolean remove, ResearchAccount account, long now, long observedTime) {
            if (refuse) { rootChecks++; return false; }
            return super.groundMutationFits(registry, job, instance, remove, account, now, observedTime);
        }
    }
}

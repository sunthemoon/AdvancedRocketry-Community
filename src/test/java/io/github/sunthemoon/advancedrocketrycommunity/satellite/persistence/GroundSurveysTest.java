package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class GroundSurveysTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void surveyAndAnalysisFinalTickPublishTogetherAndReplaysDoNotPay() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        GroundSurveys jobs = registry.groundSurveys();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(0, OWNER, 0), 0).code());
        assertEquals(GroundSurveys.Code.DEVICE_BUSY, jobs.start(new GroundSurveys.Start(id(999), OWNER,
                device(200), pending(999, OWNER, InstanceOrigin.ground(id(999))), 20, 20, 2, 20, 120), 0).code());
        assertEquals(GroundSurveys.Code.IDENTITY_CONFLICT, jobs.start(start(0, OWNER, 0), 0).code());
        assertEquals(GroundSurveys.Code.WAITING_SAVE, jobs.advanceSurvey(id(100), device(200), 24_000, 1).code());
        registry.markPersisted();
        assertEquals(GroundSurveys.Code.SOURCE_MISMATCH, jobs.advanceSurvey(id(100), device(999), 24_000, 1).code());
        for (int tick = 1; tick < 20; tick++) {
            assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceSurvey(id(100), device(200), 24_000, tick).code());
        }
        assertEquals(19, jobs.job(id(100)).orElseThrow().surveyProgress());
        assertEquals(InstanceState.PENDING, registry.instance(id(1_000)).orElseThrow().state());
        CompoundTag beforeFinal = encoded(registry);
        registry.markPersisted();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceSurvey(id(100), device(200), 24_000, 20).code());
        assertEquals(GroundSurveyJob.Phase.READY, jobs.job(id(100)).orElseThrow().phase());
        assertEquals(InstanceState.AVAILABLE, registry.instance(id(1_000)).orElseThrow().state());
        assertEquals(List.of(id(1_000)), registry.resources().available(OWNER, SYSTEM).stream()
                .map(AsteroidInstance::instanceId).toList());
        CompoundTag completed = encoded(registry);
        assertEquals(GroundSurveys.Code.NOT_READY, jobs.advanceSurvey(id(100), device(200), 24_000, 21).code());
        assertEquals(completed, encoded(registry));
        SatelliteMissionRegistry replay = SatelliteRegistryPayload.decodeCurrent(beforeFinal);
        assertEquals(GroundSurveys.Code.SUCCESS, replay.groundSurveys().advanceSurvey(id(100), device(200), 24_000, 20).code());
        assertEquals(completed, encoded(replay));

        assertEquals(GroundSurveys.Code.UNAUTHORIZED, jobs.claim(id(100), id(9), device(300), 21).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.claim(id(100), OWNER, device(300), 21).code());
        assertEquals(GroundSurveys.Code.ALREADY_CLAIMED, jobs.claim(id(100), OWNER, device(301), 21).code());
        assertEquals(GroundSurveys.Code.WAITING_SAVE, jobs.advanceAnalysis(id(100), device(300), 22).code());
        registry.markPersisted();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceAnalysis(id(100), device(300), 22).code());
        CompoundTag beforeCredit = encoded(registry);
        registry.markPersisted();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceAnalysis(id(100), device(300), 23).code());
        assertEquals(120, registry.account(OWNER).balance());
        assertEquals(GroundSurveyJob.Phase.PROCESSED, jobs.job(id(100)).orElseThrow().phase());
        CompoundTag credited = encoded(registry);
        assertEquals(GroundSurveys.Code.ALREADY_CLAIMED, jobs.advanceAnalysis(id(100), device(300), 24).code());
        assertEquals(credited, encoded(registry));
        SatelliteMissionRegistry creditReplay = SatelliteRegistryPayload.decodeCurrent(beforeCredit);
        assertEquals(GroundSurveys.Code.SUCCESS, creditReplay.groundSurveys().advanceAnalysis(id(100), device(300), 23).code());
        assertEquals(credited, encoded(creditReplay));
    }

    @Test void partialCancelPreservesPaidProgressAndPrunesOnlyAtTombstoneDeadline() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        var jobs = registry.groundSurveys();
        jobs.start(start(0, OWNER, 0), 0);
        registry.markPersisted();
        jobs.advanceSurvey(id(100), device(200), 24_000, 1);
        assertEquals(GroundSurveys.Code.UNAUTHORIZED, jobs.cancel(id(100), id(999), false, 5).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.cancel(id(100), OWNER, false, 5).code());
        assertEquals(1, jobs.job(id(100)).orElseThrow().surveyProgress());
        assertTrue(registry.instance(id(1_000)).isEmpty());
        assertEquals(0, registry.resources().liveInstances(OWNER));
        assertEquals(0, registry.reservedBytes("INSTANCES"));
        assertEquals(GroundSurveys.Code.CANCELLED, jobs.cancel(id(100), OWNER, false, 6).code());
        registry.completeDue(1_204);
        assertTrue(jobs.job(id(100)).isPresent());
        registry.completeDue(1_205);
        assertTrue(jobs.job(id(100)).isEmpty());
        assertEquals(0, registry.reservedBytes("GROUND_SURVEYS"));
        assertEquals(GroundSurveys.Code.JOB_NOT_FOUND, jobs.cancel(id(100), OWNER, false, 1_206).code());
    }

    @Test void rebindPreservesProgressAndClaimRebindWaitsForAnotherSave() {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        var jobs = registry.groundSurveys();
        jobs.start(start(0, OWNER, 0), 0); registry.markPersisted();
        jobs.advanceSurvey(id(100), device(200), 24_000, 1);
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.rebindSurvey(id(100), OWNER, device(201), 2).code());
        assertEquals(1, jobs.job(id(100)).orElseThrow().surveyProgress());
        assertEquals(GroundSurveys.Code.IDEMPOTENT, jobs.rebindSurvey(id(100), OWNER, device(201), 2).code());
        assertEquals(GroundSurveys.Code.SOURCE_MISMATCH, jobs.advanceSurvey(id(100), device(200), 24_000, 3).code());
        for (int tick = 2; tick <= 20; tick++) { jobs.advanceSurvey(id(100), device(201), 24_000, tick); }
        jobs.claim(id(100), OWNER, device(300), 21); registry.markPersisted();
        jobs.advanceAnalysis(id(100), device(300), 22);
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.rebindAnalysis(id(100), OWNER, device(301), 23).code());
        assertEquals(1, jobs.job(id(100)).orElseThrow().analysis().progress());
        assertEquals(GroundSurveys.Code.WAITING_SAVE, jobs.advanceAnalysis(id(100), device(301), 24).code());
        registry.markPersisted();
        assertEquals(GroundSurveys.Code.SOURCE_MISMATCH, jobs.advanceAnalysis(id(100), device(300), 24).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceAnalysis(id(100), device(301), 24).code());
        assertEquals(120, registry.account(OWNER).balance());
    }

    @Test void fullAccountAndArithmeticOverflowRefuseWithoutChangingAnyRegistryField() {
        GroundSurveyJob running = running(0, OWNER, device(300));
        running = running.analyzed(running.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 1,
                running.analysis().claim()), 20);
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        registry.restoreInstance(instance(running, InstanceState.AVAILABLE));
        registry.groundSurveys().restore(running);
        registry.restoreAccount(new ResearchAccount(1, OWNER, 1_000_000, 1_000_000, 0));
        CompoundTag before = encoded(registry);
        assertEquals(GroundSurveys.Code.ACCOUNT_FULL, registry.groundSurveys().advanceAnalysis(id(100), device(300), 30).code());
        assertEquals(before, encoded(registry));
        var epochOverflow = SatelliteMissionRegistry.restore(0, 0, Long.MAX_VALUE, CodecRecordSizer.INSTANCE);
        assertEquals(GroundSurveys.Code.CAPACITY_REACHED, epochOverflow.groundSurveys().start(start(0, OWNER, 0), 0).code());
        assertTrue(epochOverflow.instances().isEmpty());
        assertTrue(epochOverflow.accounts().isEmpty());
        assertEquals(0, epochOverflow.reservedBytes("GROUND_SURVEYS"));

        GroundSurveyJob last = active(0, OWNER);
        for (int tick = 1; tick < 20; tick++) { last = last.advanced(tick); }
        var expiryOverflow = SatelliteRegistryPayload.decodeCurrent(root(List.of(last), List.of(instance(last, InstanceState.PENDING))));
        before = encoded(expiryOverflow);
        assertEquals(GroundSurveys.Code.CAPACITY_REACHED, expiryOverflow.groundSurveys().advanceSurvey(id(100), device(200),
                24_000, Long.MAX_VALUE).code());
        assertEquals(before, encoded(expiryOverflow));
    }

    @Test void sharedOwnerLiveLimitAndEveryJobPhaseCountAtFifteenAndSixteen() {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        for (int i = 0; i < 15; i++) {
            registry.restoreInstance(pending(i, OWNER, InstanceOrigin.survey(id(900 + i)))
                    .transition(InstanceState.AVAILABLE, OptionalLong.of(24_000), Optional.empty()));
        }
        var jobs = registry.groundSurveys();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(15, OWNER, 0), 0).code());
        assertEquals(GroundSurveys.Code.OWNER_LIMIT, jobs.start(start(16, OWNER, 0), 0).code());
        assertEquals(16, registry.resources().liveInstances(OWNER));
        var tombstones = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        for (int i = 0; i < 15; i++) { tombstones.groundSurveys().restore(active(i, OWNER).cancelled(0)); }
        assertEquals(GroundSurveys.Code.SUCCESS, tombstones.groundSurveys().start(start(15, OWNER, 0), 0).code());
        assertEquals(GroundSurveys.Code.OWNER_LIMIT, tombstones.groundSurveys().start(start(16, OWNER, 0), 0).code());
    }

    @Test void globalCountsAt2047And2048AndDuplicateRestoreAreStrict() {
        var jobsRegistry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        for (int i = 0; i < 2_047; i++) { jobsRegistry.groundSurveys().restore(ready(i, id(10_000 + i))); }
        jobsRegistry.groundSurveys().restore(ready(2_047, id(12_047)));
        assertEquals(2_048, jobsRegistry.groundSurveys().all().size());
        assertEquals(GroundSurveys.Code.CAPACITY_REACHED, jobsRegistry.groundSurveys().start(start(3_000, id(20_000), 0), 0).code());
        assertThrows(IllegalArgumentException.class, () -> jobsRegistry.groundSurveys().restore(ready(3_000, OWNER)));
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        for (int i = 0; i < 2_047; i++) { registry.restoreInstance(pending(i, id(10_000 + i), InstanceOrigin.survey(id(900)))
                .transition(InstanceState.EXPIRED, OptionalLong.of(24_000), Optional.empty())); }
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(start(3_000, OWNER, 0), 0).code());
        assertEquals(2_048, registry.instances().size());
        assertEquals(GroundSurveys.Code.CAPACITY_REACHED, registry.groundSurveys().start(start(3_001, OWNER, 0), 0).code());
    }

    @Test void groundLifecycleBudgetAtOneMiBAndInstanceBudgetAtTwoMiBRefuseBeforePublication() {
        RecordSizer fixed = RecordSizer.BOUNDS;
        var registry = SatelliteMissionRegistry.create(0, fixed);
        for (int i = 0; i < 255; i++) { registry.groundSurveys().restore(active(i, id(10_000 + i)).cancelled(0)); }
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(start(3_000, OWNER, 0), 0).code());
        assertEquals(1_048_576, registry.reservedBytes("GROUND_SURVEYS"));
        int count = registry.instances().size();
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, registry.groundSurveys().start(start(3_001, OWNER, 0), 0).code());
        assertEquals(count, registry.instances().size());
        var instances = SatelliteMissionRegistry.create(0, fixed);
        for (int i = 0; i < 511; i++) { instances.restoreInstance(pending(i, id(10_000 + i), InstanceOrigin.survey(id(900)))
                .transition(InstanceState.EXPIRED, OptionalLong.of(24_000), Optional.empty())); }
        assertEquals(GroundSurveys.Code.SUCCESS, instances.groundSurveys().start(start(3_000, OWNER, 0), 0).code());
        assertEquals(2_097_152, instances.reservedBytes("INSTANCES"));
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, instances.groundSurveys().start(start(3_001, OWNER, 0), 0).code());
    }

    @Test void retentionKeepsProcessedUntilInstanceIsGoneAndUsesSixteenRemovalBudget() {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        GroundSurveyJob processed = processed(0, OWNER);
        registry.restoreInstance(instance(processed, InstanceState.EXPIRED));
        registry.groundSurveys().restore(processed);
        registry.completeDue(1_230);
        assertTrue(registry.groundSurveys().job(processed.jobId()).isPresent());
        registry.completeDue(25_200);
        assertTrue(registry.instances().isEmpty());
        assertTrue(registry.groundSurveys().job(processed.jobId()).isEmpty());
        for (int i = 0; i < 33; i++) { registry.groundSurveys().restore(active(i, id(10_000 + i)).cancelled(0)); }
        registry.completeDue(25_201);
        assertEquals(17, registry.groundSurveys().all().size());
        registry.completeDue(25_202);
        assertEquals(1, registry.groundSurveys().all().size());
    }

    /** W21b review F3, its probe case: the PENDING instance of a healthy ACTIVE job is not held. */
    @Test void instanceReleaseOfAHealthyActivePairIsIdempotentAndChangesNothing() {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(start(0, OWNER, 0), 0).code());
        CompoundTag before = encoded(registry);
        var release = registry.resources().releaseInstance(id(1_000), 24_000, 5);
        assertEquals(SatelliteOperationCode.IDEMPOTENT, release.code());
        assertFalse(release.changed());
        assertEquals(InstanceState.PENDING, registry.instance(id(1_000)).orElseThrow().state());
        assertEquals(before, encoded(registry));
    }

    private static GroundSurveys.Start start(long index, UUID owner, long time) {
        AsteroidInstance original = pending(index, owner, InstanceOrigin.ground(id(100 + index)));
        AsteroidInstance instance = new AsteroidInstance(2, original.instanceId(), owner, SYSTEM, TYPE, VERSION, VERSION,
                index, YIELD, time, OptionalLong.empty(), InstanceState.PENDING, original.origin(), Optional.empty());
        return new GroundSurveys.Start(id(100 + index), owner, device(200 + index), instance, 20, 20, 2, 20, 120);
    }

    private static CompoundTag encoded(SatelliteMissionRegistry registry) {
        return SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
    }
}

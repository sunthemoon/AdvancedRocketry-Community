package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class GroundSurveyInvariantsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Stream<String> mismatches() {
        return Stream.of("missing_job", "missing_instance", "origin_id", "instance_id", "owner", "system",
                "type", "table", "fingerprint", "seed", "yield", "wrong_origin", "wrong_job_phase",
                "available_active", "duplicate_device", "duplicate_instance", "cancelled_instance");
    }

    @ParameterizedTest @MethodSource("mismatches")
    void eachBacklinkAndSnapshotFailureHoldsRawRecordsWithoutCreditOrDeletion(String mismatch) {
        GroundSurveyJob job = active(0, OWNER);
        CompoundTag root = root(List.of(job), List.of(instance(job, InstanceState.PENDING)));
        ListTag jobs = root.getList("ground_surveys", Tag.TAG_COMPOUND);
        ListTag instances = root.getList("instances", Tag.TAG_COMPOUND);
        CompoundTag raw = jobs.getCompound(0);
        CompoundTag instance = instances.getCompound(0);
        switch (mismatch) {
            case "missing_job" -> jobs.clear();
            case "missing_instance" -> instances.clear();
            case "origin_id" -> instance.putUUID("origin_id", id(999));
            case "instance_id" -> raw.putUUID("instance_id", id(999));
            case "owner" -> raw.putUUID("owner_id", id(999));
            case "system" -> raw.putString("system", "x:other");
            case "type" -> raw.putString("asteroid_type", "x:other");
            case "table" -> raw.putString("table_version", "fedcba9876543210");
            case "fingerprint" -> raw.putString("candidate_fingerprint", "fedcba9876543210");
            case "seed" -> raw.putLong("seed", 99);
            case "yield" -> raw.putString("yield_digest", "fedcba9876543210");
            case "wrong_origin" -> instance.putString("origin_kind", "survey_mission");
            case "wrong_job_phase" -> { jobs.clear(); jobs.add(GroundSurveyCodec.encode(ready(0, OWNER))); }
            case "available_active" -> { instances.clear(); instances.add(SatelliteNbtCodec.encodeInstance(instance(job, InstanceState.AVAILABLE))); }
            case "duplicate_device" -> {
                GroundSurveyJob other = active(1, OWNER).rebound(job.source());
                jobs.add(GroundSurveyCodec.encode(other));
                instances.add(SatelliteNbtCodec.encodeInstance(instance(other, InstanceState.PENDING)));
            }
            case "duplicate_instance" -> {
                CompoundTag copy = raw.copy(); copy.putUUID("job_id", id(101)); jobs.add(copy);
            }
            case "cancelled_instance" -> { jobs.clear(); jobs.add(GroundSurveyCodec.encode(job.advanced(1).cancelled(2))); }
            default -> throw new AssertionError(mismatch);
        }
        int jobCount = jobs.size(), instanceCount = instances.size();
        SatelliteMissionSavedData loaded = SatelliteMissionSavedData.load(root);
        assertTrue(loaded.operational(), mismatch);
        assertEquals(jobCount, loaded.groundSurveys().size());
        assertEquals(instanceCount, loaded.instances().size());
        assertEquals(77, loaded.account(OWNER).balance());
        assertTrue(loaded.instances().stream().allMatch(value -> value.state() == InstanceState.QUARANTINED), mismatch);
        if (!mismatch.equals("cancelled_instance")) {
            assertTrue(loaded.groundSurveys().stream().allMatch(value -> value.phase() == GroundSurveyJob.Phase.QUARANTINED), mismatch);
        }
        CompoundTag held = loaded.save(new CompoundTag());
        SatelliteMissionSavedData second = SatelliteMissionSavedData.load(held);
        assertTrue(second.operational());
        assertFalse(second.flushPending(), mismatch);
        assertEquals(held, second.save(new CompoundTag()));
        assertEquals(held, SatelliteMissionSavedData.load(second.save(new CompoundTag())).save(new CompoundTag()));
    }

    @Test void activeQuarantinedPairHasNoOnlineReleaseInEitherOrderAndChangesNothing() {
        GroundSurveyJob active = active(0, OWNER);
        CompoundTag root = root(List.of(active), List.of(instance(active, InstanceState.PENDING)));
        root.getList("ground_surveys", Tag.TAG_COMPOUND).getCompound(0).putLong("seed", 55);
        SatelliteMissionSavedData data = SatelliteMissionSavedData.load(root);
        CompoundTag before = data.save(new CompoundTag());
        for (int order = 0; order < 2; order++) {
            var release = data.groundSurveys(jobs -> jobs.release(active.jobId()));
            assertEquals(GroundSurveys.Code.OFFLINE_REPAIR_REQUIRED, release.code());
            assertTrue(release.reason().contains("ACTIVE") && release.reason().contains("PENDING"));
            assertEquals(SatelliteOperationCode.OFFLINE_REPAIR_REQUIRED,
                    data.resources(resources -> resources.releaseInstance(active.instanceId(), 24_000, 99)).code());
        }
        assertEquals(before, data.save(new CompoundTag()));
    }

    @Test void duplicateRunningClaimsKeepAvailabilityButBothReleasePathsRefuse() {
        GroundSurveyJob first = running(0, OWNER, device(300));
        GroundSurveyJob second = running(1, OWNER, device(300));
        SatelliteMissionSavedData data = SatelliteMissionSavedData.load(root(List.of(first, second),
                List.of(instance(first, InstanceState.AVAILABLE), instance(second, InstanceState.AVAILABLE))));
        assertTrue(data.operational());
        assertEquals(2, data.instances().stream().filter(value -> value.state() == InstanceState.AVAILABLE).count());
        for (GroundSurveyJob job : data.groundSurveys()) {
            assertEquals(GroundSurveyJob.Phase.READY, job.effectivePhase());
            assertEquals(GroundSurveys.Code.OFFLINE_REPAIR_REQUIRED,
                    data.groundSurveys(jobs -> jobs.release(job.jobId())).code());
            assertTrue(data.groundSurveyQuery(jobs -> jobs.instanceReleaseProblem(job.instanceId())).contains("RUNNING"));
            assertEquals(SatelliteOperationCode.OFFLINE_REPAIR_REQUIRED,
                    data.resources(resources -> resources.releaseInstance(job.instanceId(), 24_000, 99)).code());
        }
        CompoundTag held = data.save(new CompoundTag());
        // Also hold the instances, as after an offline diagnostic; release cannot bypass the conflicting claims.
        for (Tag raw : held.getList("instances", Tag.TAG_COMPOUND)) { ((CompoundTag) raw).putString("state", "quarantined"); }
        data = SatelliteMissionSavedData.load(held);
        CompoundTag before = data.save(new CompoundTag());
        for (GroundSurveyJob job : data.groundSurveys()) {
            assertEquals(SatelliteOperationCode.OFFLINE_REPAIR_REQUIRED,
                    data.resources(resources -> resources.releaseInstance(job.instanceId(), 24_000, 99)).code());
        }
        assertEquals(before, data.save(new CompoundTag()));
        assertEquals(before, SatelliteMissionSavedData.load(before).save(new CompoundTag()));
    }

    /** W21b review F2: records the load would hold, left unrepaired as an online bug would leave them. */
    @Test void anUnrelatedBrokenRecordDoesNotStopAnotherJobButTheBrokenPairStillRefusesWithItsCause() {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        registry.restoreAccount(new ResearchAccount(1, OWNER, 77, 100, 23));
        GroundSurveyJob healthy = active(0, OWNER);
        GroundSurveyJob broken = active(1, OWNER);
        registry.restoreInstance(instance(healthy, InstanceState.PENDING));
        registry.restoreInstance(instance(broken, InstanceState.AVAILABLE));
        registry.groundSurveys().restore(healthy);
        registry.groundSurveys().restore(broken);
        // A PENDING survey-origin instance whose mission is missing; the load would quarantine it.
        registry.restoreInstance(pending(7, OWNER, InstanceOrigin.survey(id(555))));
        GroundSurveys jobs = registry.groundSurveys();

        assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceSurvey(healthy.jobId(), healthy.source(), 24_000, 1).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.rebindSurvey(healthy.jobId(), OWNER, device(250), 2).code());
        assertEquals(1, jobs.job(healthy.jobId()).orElseThrow().surveyProgress());

        CompoundTag before = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        var advance = jobs.advanceSurvey(broken.jobId(), broken.source(), 24_000, 3);
        assertEquals(GroundSurveys.Code.INVARIANT_FAILED, advance.code());
        assertEquals("GROUND_PHASE_MISMATCH", advance.reason());
        // Cancel would drop the wrongly retained instance and hide the pair; it refuses on the original state.
        var cancel = jobs.cancel(broken.jobId(), OWNER, false, 3);
        assertEquals(GroundSurveys.Code.INVARIANT_FAILED, cancel.code());
        assertEquals("GROUND_PHASE_MISMATCH", cancel.reason());
        assertEquals(before, SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
    }

    @Test void touchedDevicesProcessorsInstancesAndStrayBacklinksStillRefuseWithoutChange() {
        for (String peer : List.of("device", "processor", "instance", "stray_origin")) {
            var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
            registry.restoreAccount(new ResearchAccount(1, OWNER, 77, 100, 23));
            GroundSurveyJob touched = peer.equals("processor") ? running(0, OWNER, device(300)) : active(0, OWNER);
            registry.restoreInstance(instance(touched, peer.equals("processor") ? InstanceState.AVAILABLE
                    : InstanceState.PENDING));
            registry.groundSurveys().restore(touched);
            switch (peer) {
                case "device" -> registry.groundSurveys().restore(active(1, OWNER).rebound(touched.source()));
                case "processor" -> registry.groundSurveys().restore(running(1, OWNER, device(300)));
                case "instance" -> registry.groundSurveys().restore(new GroundSurveyJob(1, id(101), OWNER,
                        touched.instanceId(), device(201), touched.snapshot(), 20, 20, 0, 0, 1,
                        GroundSurveyJob.Phase.ACTIVE, java.util.OptionalLong.empty(), java.util.OptionalLong.empty(),
                        touched.analysis(), java.util.Optional.empty()));
                case "stray_origin" -> registry.restoreInstance(pending(9, OWNER, InstanceOrigin.ground(touched.jobId())));
                default -> throw new AssertionError(peer);
            }
            CompoundTag before = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
            var result = peer.equals("processor")
                    ? registry.groundSurveys().advanceAnalysis(touched.jobId(), device(300), 5)
                    : registry.groundSurveys().advanceSurvey(touched.jobId(), touched.source(), 24_000, 5);
            assertEquals(GroundSurveys.Code.INVARIANT_FAILED, result.code(), peer);
            assertEquals(switch (peer) {
                case "device" -> "DUPLICATE_SOURCE";
                case "processor" -> "DUPLICATE_PROCESSOR";
                case "instance" -> "DUPLICATE_INSTANCE";
                default -> "GROUND_SNAPSHOT_MISMATCH";
            }, result.reason(), peer);
            assertEquals(before, SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()), peer);
            assertEquals(0, registry.logicalGameTime(), peer);
        }
    }

    @Test void retainedGroundStatesAcceptReadyProcessedAndTheirQuarantinedPreviousPhase() {
        for (GroundSurveyJob job : List.of(ready(0, OWNER), processed(0, OWNER), ready(0, OWNER).quarantine("Q"),
                processed(0, OWNER).quarantine("Q"))) {
            for (InstanceState state : List.of(InstanceState.AVAILABLE, InstanceState.EXPIRED, InstanceState.DEPLETED)) {
                CompoundTag root = root(List.of(job), List.of(instance(job, state)));
                var loaded = SatelliteMissionSavedData.load(root);
                assertTrue(loaded.operational());
                assertEquals(state, loaded.instances().get(0).state());
                assertEquals(job, loaded.groundSurveys().get(0));
                assertEquals(root, loaded.save(new CompoundTag()));
            }
        }
        for (GroundSurveyJob job : List.of(ready(0, OWNER), processed(0, OWNER))) {
            assertEquals(job, SatelliteMissionSavedData.load(root(List.of(job), List.of())).groundSurveys().get(0));
        }
    }

    @Test void groundAllocationStillChecksMiningAndMissingOriginHoldsItsMission() {
        CompoundTag migrated = SavedDataSchemaMigrator.migrate(ManagedSavedDataType.SATELLITE_MISSIONS, schema3()).payload();
        CompoundTag instance = migrated.getList("instances", Tag.TAG_COMPOUND).getCompound(3);
        instance.putString("origin_kind", "ground_survey");
        GroundSurveyJob job = ready(3, OWNER);
        instance.putUUID("origin_id", job.jobId());
        migrated.getList("ground_surveys", Tag.TAG_COMPOUND).add(GroundSurveyCodec.encode(job));
        var loaded = SatelliteMissionSavedData.load(migrated);
        assertTrue(loaded.operational());
        assertEquals(InstanceState.ALLOCATED, loaded.instance(id(1_003)).orElseThrow().state());
        assertEquals(MissionStatus.ACTIVE, loaded.mission(id(900)).orElseThrow().status());
        migrated.getList("ground_surveys", Tag.TAG_COMPOUND).clear();
        loaded = SatelliteMissionSavedData.load(migrated);
        assertEquals(InstanceState.QUARANTINED, loaded.instance(id(1_003)).orElseThrow().state());
        assertEquals(MissionStatus.QUARANTINED, loaded.mission(id(900)).orElseThrow().status());
        assertEquals("INSTANCE_MISMATCH", loaded.mission(id(900)).orElseThrow().quarantine().orElseThrow().reason());
        assertEquals(77, loaded.account(OWNER).balance());
    }

    @Test void missionOriginsAndBothSurveyDirectionsRetainExistingChecks() {
        CompoundTag migrated = SavedDataSchemaMigrator.migrate(ManagedSavedDataType.SATELLITE_MISSIONS, schema3()).payload();
        for (String change : List.of("ground_on_payload", "survey_origin_job_id", "owner", "system", "missing_instance")) {
            CompoundTag root = migrated.copy();
            CompoundTag pending = root.getList("instances", Tag.TAG_COMPOUND).getCompound(0);
            switch (change) {
                case "ground_on_payload" -> pending.putString("origin_kind", "ground_survey");
                case "survey_origin_job_id" -> pending.putUUID("origin_id", id(100));
                case "owner" -> pending.putUUID("owner_id", id(999));
                case "system" -> pending.putString("system", "x:other");
                case "missing_instance" -> root.getList("instances", Tag.TAG_COMPOUND).remove(0);
                default -> throw new AssertionError(change);
            }
            var loaded = SatelliteMissionSavedData.load(root);
            assertTrue(loaded.operational());
            assertEquals(MissionStatus.QUARANTINED, loaded.mission(id(500)).orElseThrow().status(), change);
            if (!change.equals("missing_instance")) {
                assertEquals(InstanceState.QUARANTINED, loaded.instance(id(1_000)).orElseThrow().state(), change);
            }
            assertEquals(InstanceState.AVAILABLE, loaded.instance(id(1_002)).orElseThrow().state());
            assertEquals(InstanceState.ALLOCATED, loaded.instance(id(1_003)).orElseThrow().state());
        }
    }
}

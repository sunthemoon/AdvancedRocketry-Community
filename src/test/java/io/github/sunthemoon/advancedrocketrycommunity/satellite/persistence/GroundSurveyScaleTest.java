package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.SpikeCost;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W21b review F1, W168: ground changes cost O(touched records) when reservations prove the candidate fits;
 * otherwise they use exact resident measurements, with whole-root fallback for failed sizing. One advance stays below
 * 1 ms, and START on a root near the 16 MiB bound stays within the 500 ms single-spike budget (docs/17), measured
 * as its server-thread cost ({@link SpikeCost}).
 */
final class GroundSurveyScaleTest {
    private static final long ADVANCE_BUDGET_NANOS = 1_000_000L;
    private static final long SPIKE_BUDGET_NANOS = 500_000_000L;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void reservationBoundedLifecycleIncludingStartAndAccountCreationSkipsTheWholeRootPreflight() {
        CountingSizer sizer = new CountingSizer();
        var registry = SatelliteMissionRegistry.create(0, sizer);
        var jobs = registry.groundSurveys();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(0, 20, 2), 0).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(1, 20, 2), 0).code());
        assertEquals(0, sizer.rootChecks, "both STARTs fit their added reservations");
        registry.markPersisted();
        GroundSurveyJob.Device source = device(200);
        for (int tick = 1; tick <= 20; tick++) {
            if (tick == 10) {
                assertEquals(GroundSurveys.Code.SUCCESS, jobs.rebindSurvey(id(100), OWNER, device(250), tick).code());
                source = device(250);
            }
            assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceSurvey(id(100), source, 24_000, tick).code());
        }
        assertEquals(InstanceState.AVAILABLE, registry.instance(id(1_000)).orElseThrow().state());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.cancel(id(101), OWNER, false, 21).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.claim(id(100), OWNER, device(300), 21).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.rebindAnalysis(id(100), OWNER, device(301), 22).code());
        registry.markPersisted();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceAnalysis(id(100), device(301), 23).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.advanceAnalysis(id(100), device(301), 24).code());
        assertEquals(GroundSurveyJob.Phase.PROCESSED, jobs.job(id(100)).orElseThrow().phase());
        assertEquals(120, registry.account(OWNER).balance());
        assertEquals(0, sizer.rootChecks, "advances, rebinds, claim, credit and cancel");

        CountingSizer releases = new CountingSizer();
        var held = SatelliteMissionRegistry.restore(0, 0, 2, releases);
        held.restoreAccount(ResearchAccount.empty(OWNER));
        GroundSurveyJob quarantined = ready(5, OWNER).quarantine("Q");
        held.restoreInstance(instance(quarantined, InstanceState.AVAILABLE).quarantine());
        held.groundSurveys().restore(quarantined);
        assertEquals(GroundSurveys.Code.SUCCESS, held.groundSurveys().release(quarantined.jobId()).code());
        assertEquals(SatelliteOperationCode.SUCCESS,
                held.resources().releaseInstance(quarantined.instanceId(), 24_000, 50).code());
        assertEquals(InstanceState.AVAILABLE, held.instance(quarantined.instanceId()).orElseThrow().state());
        assertEquals(0, releases.rootChecks, "job release and instance release");

        CountingSizer accounts = new CountingSizer();
        var unaccounted = SatelliteMissionRegistry.restore(0, 0, 2, accounts);
        GroundSurveyJob running = running(6, id(77), device(300));
        running = running.analyzed(running.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 1,
                running.analysis().claim()), 20);
        unaccounted.restoreInstance(instance(running, InstanceState.AVAILABLE));
        unaccounted.groundSurveys().restore(running);
        assertEquals(GroundSurveys.Code.SUCCESS, unaccounted.groundSurveys().advanceAnalysis(running.jobId(),
                device(300), 30).code());
        assertEquals(120, unaccounted.account(id(77)).balance());
        assertEquals(0, accounts.rootChecks, "the new account's reservation also fits");
    }

    @Test void reservationsAboveTheRootBoundSendOrdinaryChangesThroughTheWholeRootPreflight() {
        CountingSizer sizer = new CountingSizer();
        sizer.satelliteReservation = SatelliteLimits.MAX_REGISTRY_NBT_BYTES;
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        registry.restoreSatellite(SatelliteState.launch(new UUID(4L, 0L), ModIdentity.id("data_satellite"), OWNER, 0L));
        registry.restoreAccount(ResearchAccount.empty(OWNER));
        GroundSurveyJob active = active(0, OWNER);
        registry.restoreInstance(instance(active, InstanceState.PENDING));
        registry.groundSurveys().restore(active);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().advanceSurvey(active.jobId(), active.source(),
                24_000, 1).code());
        assertEquals(1, sizer.rootChecks);
        sizer.refuse = true;
        CompoundTag before = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, registry.groundSurveys().advanceSurvey(active.jobId(),
                active.source(), 24_000, 2).code());
        assertEquals(before, SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
    }

    /** The reservation bound's two constants: the root framing, and the largest account encoding. */
    @Test void reservationsAccountsAndFramingBoundTheEncodedRoot() {
        var empty = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        assertTrue(SatelliteNbtSize.uncompressedBytes(SatelliteRegistryPayload.encodeCurrent(empty, new CompoundTag()))
                <= RecordSizer.ROOT_FRAMING_BYTES);
        // Every account field has a fixed width, so one account measures them all.
        assertTrue(SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeAccount(new ResearchAccount(1,
                new UUID(-1L, -1L), 77, 100, 23))) <= RecordSizer.ACCOUNT_BYTES);
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        registry.restoreSatellite(SatelliteState.launch(new UUID(4L, 0L), ModIdentity.id("data_satellite"), OWNER, 0L));
        for (int index = 0; index < 8; index++) {
            registry.restoreMission(claimed(index));
            registry.restoreAccount(new ResearchAccount(1, id(40 + index), 77, 100, 23));
            GroundSurveyJob job = index % 2 == 0 ? processed(index, OWNER) : active(index, OWNER).quarantine("Q");
            registry.restoreInstance(instance(job, index % 2 == 0 ? InstanceState.EXPIRED : InstanceState.PENDING));
            registry.groundSurveys().restore(job);
        }
        long bound = RecordSizer.ROOT_FRAMING_BYTES + (long) registry.accounts().size() * RecordSizer.ACCOUNT_BYTES;
        for (String section : List.of("SATELLITES", "MISSIONS", "INSTANCES", "GROUND_SURVEYS")) {
            bound += registry.reservedBytes(section);
        }
        int root = SatelliteNbtSize.uncompressedBytes(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
        assertTrue(root <= bound, root + " > " + bound);
    }

    /** The review's probe registry: 2,000 AVAILABLE survey-origin instances and 700 READY jobs, plus one START. */
    @Test void oneAdvanceOnTheReviewRegistryStaysFarBelowOneMillisecond() {
        CountingSizer sizer = new CountingSizer();
        var registry = SatelliteMissionRegistry.create(0, sizer);
        for (int i = 0; i < 2_000; i++) {
            registry.restoreInstance(pending(10_000 + i, id(20_000 + i), InstanceOrigin.survey(id(900)))
                    .transition(InstanceState.AVAILABLE, OptionalLong.of(1_728_000), Optional.empty()));
        }
        for (int i = 0; i < 700; i++) { registry.groundSurveys().restore(ready(i, id(30_000 + i))); }
        var jobs = registry.groundSurveys();
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(5_000, 1_000, 1_000), 0).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(5_001, 20, 1_000), 0).code());
        assertEquals(2_002, registry.instances().size());
        assertEquals(702, jobs.all().size());
        int rootBytes = SatelliteNbtSize.uncompressedBytes(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
        registry.markPersisted();
        UUID survey = id(5_100);
        UUID analysis = id(5_101);
        for (int tick = 1; tick <= 20; tick++) { jobs.advanceSurvey(analysis, device(5_201), 24_000, tick); }
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.claim(analysis, OWNER, device(300), 20).code());
        registry.markPersisted();
        int checks = sizer.rootChecks;

        long surveyMedian = median(index -> assertEquals(GroundSurveys.Code.SUCCESS,
                jobs.advanceSurvey(survey, device(5_200), 24_000, 21 + index).code()));
        long analysisMedian = median(index -> assertEquals(GroundSurveys.Code.SUCCESS,
                jobs.advanceAnalysis(analysis, device(300), 400 + index).code()));
        System.out.printf("ARCE_W21BF_ADVANCE instances=%d jobs=%d root_bytes=%d survey_median_us=%.1f"
                + " analysis_median_us=%.1f%n", registry.instances().size(), jobs.all().size(), rootBytes,
                surveyMedian / 1e3, analysisMedian / 1e3);
        assertEquals(checks, sizer.rootChecks, "no advance encodes the root");
        assertTrue(surveyMedian < ADVANCE_BUDGET_NANOS, "Survey advance median " + surveyMedian / 1e3 + " us");
        assertTrue(analysisMedian < ADVANCE_BUDGET_NANOS, "Analysis advance median " + analysisMedian / 1e3 + " us");
    }

    @Test void startOnARootNearTheBoundStaysWithinTheSpikeBudget() {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        CompoundTag empty = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        int missionBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(claimed(0))) - 3;
        int target = SatelliteLimits.MAX_REGISTRY_NBT_BYTES - 32 * 1024;
        int count = (target - SatelliteNbtSize.uncompressedBytes(empty)) / missionBytes;
        for (int index = 0; index < count; index++) { registry.restoreMission(claimed(index)); }
        int rootBytes = SatelliteNbtSize.uncompressedBytes(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
        assertTrue(rootBytes > SatelliteLimits.MAX_REGISTRY_NBT_BYTES - 64 * 1024, "root " + rootBytes);

        // W55: two warm-up STARTs, then the median server-thread cost of seven (SpikeCost); each START adds about
        // 1 KiB, well inside the 32 KiB left below the bound.
        var samples = SpikeCost.measure(2, 7, run -> assertEquals(GroundSurveys.Code.SUCCESS,
                registry.groundSurveys().start(start(run, 20, 2), 0).code()));
        int afterBytes = SatelliteNbtSize.uncompressedBytes(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
        assertTrue(afterBytes <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
        System.out.printf("ARCE_W21BF_START missions=%d root_bytes=%d median_ms=%.1f max_ms=%.1f cost_median_ms=%.1f"
                + " cost_max_ms=%.1f%n", count, rootBytes, samples.medianWall() / 1e6, samples.maxWall() / 1e6,
                samples.medianCost() / 1e6, samples.maxCost() / 1e6);
        assertTrue(samples.medianCost() < SPIKE_BUDGET_NANOS,
                "START median cost " + samples.medianCost() / 1e6 + " ms at " + rootBytes + " bytes");
    }

    private static long median(java.util.function.IntConsumer advance) {
        for (int index = 0; index < 100; index++) { advance.accept(index); }
        long[] samples = new long[101];
        for (int index = 0; index < samples.length; index++) {
            long begin = System.nanoTime();
            advance.accept(100 + index);
            samples[index] = System.nanoTime() - begin;
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static GroundSurveys.Start start(long index, int surveyTicks, int analysisTicks) {
        AsteroidInstance instance = pending(index, OWNER, InstanceOrigin.ground(id(100 + index)));
        return new GroundSurveys.Start(id(100 + index), OWNER, device(200 + index), instance, surveyTicks, 20,
                analysisTicks, 20, 120);
    }

    /** A claimed gas mission whose 17 reward entries carry 126-character item IDs, about 3 KiB encoded. */
    private static MissionState claimed(int index) {
        List<RewardEntry> reward = new ArrayList<>();
        for (int entry = 0; entry < SatelliteLimits.MAX_REWARD_ENTRIES; entry++) {
            reward.add(new RewardEntry(ResourceLocation.tryParse(
                    String.format("x:%05d_%02d_", index, entry) + "i".repeat(115)), 1));
        }
        return new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, new UUID(31, index), new UUID(32, index),
                new UUID(33, index % 256), TYPE, MissionKind.GAS, SYSTEM, Optional.empty(), index, 0, 20, 1,
                MissionStatus.CLAIMED, OptionalLong.of(20), OptionalLong.of(20), "r".repeat(200), Optional.empty(),
                new MissionPayload.Resource(MissionKind.GAS, reward, new UUID(34, index), Optional.empty(), false,
                        Optional.empty(), false, OptionalLong.empty()));
    }

    /** The persisted sizer, counting whole-root preflights; it can inflate satellite reservations or refuse. */
    private static final class CountingSizer implements RecordSizer {
        int rootChecks;
        int satelliteReservation = -1;
        boolean refuse;

        public int satelliteBytes(SatelliteState state) {
            return satelliteReservation >= 0 ? satelliteReservation : CodecRecordSizer.INSTANCE.satelliteBytes(state);
        }
        public int missionBytes(MissionState mission) { return CodecRecordSizer.INSTANCE.missionBytes(mission); }
        public int instanceBytes(AsteroidInstance instance) { return CodecRecordSizer.INSTANCE.instanceBytes(instance); }
        public int groundSurveyBytes(GroundSurveyJob job) { return CodecRecordSizer.INSTANCE.groundSurveyBytes(job); }
        // W168-R5: claim a proof only where the persisted sizer does; an inflated reservation stays proven as before.
        public Reservation satelliteReservation(SatelliteState state) {
            return satelliteReservation >= 0 ? new Reservation(satelliteReservation, true)
                    : CodecRecordSizer.INSTANCE.satelliteReservation(state);
        }
        public Reservation missionReservation(MissionState mission) { return CodecRecordSizer.INSTANCE.missionReservation(mission); }
        public Reservation instanceReservation(AsteroidInstance instance) {
            return CodecRecordSizer.INSTANCE.instanceReservation(instance);
        }
        public Reservation groundSurveyReservation(GroundSurveyJob job) {
            return CodecRecordSizer.INSTANCE.groundSurveyReservation(job);
        }
        public boolean groundMutationFits(SatelliteMissionRegistry registry, GroundSurveyJob job,
                AsteroidInstance instance, boolean removeInstance, ResearchAccount account, long now, long observedTime) {
            rootChecks++;
            return !refuse && CodecRecordSizer.INSTANCE.groundMutationFits(registry, job, instance, removeInstance,
                    account, now, observedTime);
        }
    }
}

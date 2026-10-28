package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

    @ParameterizedTest
    @ValueSource(strings = {"START", "ACCOUNT"})
    void realCodecStillRefusesAdditionsThatExceedTheEncodedRootBound(String operation) {
        var sizer = new GroundPreflightBenchmark.CountingSizer();
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        GroundSurveyJob job = lastAnalysis();
        if (operation.equals("ACCOUNT")) {
            registry.restoreInstance(instance(job, InstanceState.AVAILABLE));
            registry.groundSurveys().restore(job);
        } else { registry.restoreAccount(ResearchAccount.empty(OWNER)); }
        int rootBytes = SatelliteNbtSize.uncompressedBytes(encoded(registry));
        int missionBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(GroundPreflightBenchmark.claimed(0))) - 3;
        for (int index = 0; rootBytes + missionBytes <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES; index++) {
            registry.restoreMission(GroundPreflightBenchmark.claimed(index));
            rootBytes += missionBytes;
        }
        int accountBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeAccount(ResearchAccount.empty(id(10_000)))) - 3;
        for (int index = 0; rootBytes + accountBytes <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES; index++) {
            registry.restoreAccount(ResearchAccount.empty(id(10_000 + index)));
            rootBytes += accountBytes;
        }
        CompoundTag before = encoded(registry);
        assertEquals(rootBytes, SatelliteNbtSize.uncompressedBytes(before));
        assertTrue(SatelliteLimits.MAX_REGISTRY_NBT_BYTES - rootBytes < accountBytes);
        assertTrue(GroundPreflightBenchmark.reservationBound(registry) > SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
        long reserved = GroundPreflightBenchmark.reservationBound(registry);
        long sequence = registry.epochs().stateChanges();
        GroundSurveys.Result result = operation.equals("START")
                ? registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0)
                : registry.groundSurveys().advanceAnalysis(job.jobId(), device(300), 30);
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, result.code());
        assertFalse(result.changed());
        assertEquals(1, sizer.rootChecks);
        assertEquals(before, encoded(registry));
        assertEquals(reserved, GroundPreflightBenchmark.reservationBound(registry));
        assertEquals(sequence, registry.epochs().stateChanges());
    }

    /**
     * W168-R1, the review's probe: restored DATA missions held from CLAIM_PENDING_DISCOVERY, then satellites and
     * accounts, fill a loadable root to just below the bound. START must refuse exactly as the whole-root preflight
     * does and leave the root, accounts, reservations, clock and mutation sequence unchanged.
     */
    @Test
    void startOnARootOfHeldDiscoveryMissionsRefusesAsTheWholeRootPreflightDoes() {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        registry.restoreAccount(ResearchAccount.empty(OWNER));
        int rootBytes = SatelliteNbtSize.uncompressedBytes(encoded(registry));
        int missionBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(held(0))) - 3;
        for (int index = 0; index < SatelliteLimits.MAX_MISSIONS; index++) {
            registry.restoreMission(held(index));
            rootBytes += missionBytes;
        }
        int satelliteBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeSatellite(heldSatellite(0))) - 3;
        for (int index = 0; rootBytes + satelliteBytes <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES
                && index < SatelliteLimits.MAX_SATELLITES; index++) {
            registry.restoreSatellite(heldSatellite(index));
            rootBytes += satelliteBytes;
        }
        int accountBytes = SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeAccount(ResearchAccount.empty(id(10_000)))) - 3;
        for (int index = 0; rootBytes + accountBytes <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES; index++) {
            registry.restoreAccount(ResearchAccount.empty(id(10_000 + index)));
            rootBytes += accountBytes;
        }
        CompoundTag root = encoded(registry);
        assertEquals(rootBytes, SatelliteNbtSize.uncompressedBytes(root));
        registry = SatelliteRegistryPayload.decodeCurrent(root);
        assertEquals(root, encoded(registry), "Loadable without repairs");

        var request = GroundPreflightBenchmark.start();
        GroundSurveyJob candidate = active(0, OWNER);
        long added = CodecRecordSizer.INSTANCE.groundSurveyBytes(candidate)
                + CodecRecordSizer.INSTANCE.instanceBytes(request.instance());
        assertTrue(GroundPreflightBenchmark.reservationBound(registry) + added > SatelliteLimits.MAX_REGISTRY_NBT_BYTES,
                "Reservations that cover the held missions cannot prove this START fits");
        assertFalse(CodecRecordSizer.INSTANCE.groundMutationFits(registry, candidate, request.instance(), false,
                registry.account(OWNER), 0, 0), "The whole-root preflight refuses the same START");
        CompoundTag before = encoded(registry);
        var accounts = registry.accounts();
        var reserved = sections(registry);
        long logical = registry.logicalGameTime();
        long observed = registry.lastObservedGameTime();
        long sequence = registry.epochs().stateChanges();

        GroundSurveys.Result result = registry.groundSurveys().start(request, 0);
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, result.code());
        assertFalse(result.changed());
        assertEquals(before, encoded(registry));
        assertEquals(accounts, registry.accounts());
        assertEquals(reserved, sections(registry));
        assertEquals(logical, registry.logicalGameTime());
        assertEquals(observed, registry.lastObservedGameTime());
        assertEquals(sequence, registry.epochs().stateChanges());
        assertTrue(registry.groundSurveys().all().isEmpty());
        assertTrue(registry.instance(request.instance().instanceId()).isEmpty());
    }

    /** The review's held record: 128-character IDs, a 600-byte reward version and the longest quarantine reason. */
    static MissionState held(int index) {
        return new MissionState(2, new UUID(50, index), new UUID(51, index), OWNER, longId(1), MissionKind.DATA,
                longId(2), Optional.empty(), 0, 0, 20, 1, MissionStatus.QUARANTINED, OptionalLong.of(20),
                OptionalLong.of(20), "ࠀ".repeat(200),
                Optional.of(new MissionQuarantine("Q".repeat(48), MissionStatus.CLAIM_PENDING_DISCOVERY, true)),
                new MissionPayload.Data(1, 1, true));
    }

    /** An operational data satellite bound to {@link #held(int)} with eight 128-character components. */
    static SatelliteState heldSatellite(int index) {
        var components = new ArrayList<ResourceLocation>();
        for (int component = 0; component < 8; component++) { components.add(longId(100 + component)); }
        return new SatelliteState(2, new UUID(51, index), longId(1), OWNER, 0, SatelliteStatus.OPERATIONAL,
                Optional.of(new UUID(50, index)), SatelliteKind.DATA, Optional.empty(),
                new SatelliteBlueprint(components, false, new SatelliteStats(4, 10_720, 1_000, 1, 10)),
                new SatelliteKindState.Plain(SatelliteKind.DATA));
    }

    private static ResourceLocation longId(int index) {
        return ResourceLocation.tryParse("x:" + String.format(Locale.ROOT, "%06d", index) + "a".repeat(120));
    }

    private static Map<String, Long> sections(SatelliteMissionRegistry registry) {
        var reserved = new LinkedHashMap<String, Long>();
        for (String section : List.of("SATELLITES", "MISSIONS", "INSTANCES", "ACCOUNTS", "GROUND_SURVEYS")) {
            reserved.put(section, registry.reservedBytes(section));
        }
        return reserved;
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

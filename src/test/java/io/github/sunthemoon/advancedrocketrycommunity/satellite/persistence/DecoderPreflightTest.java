package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** W168-R3: raw saved names may grow on decode; a capped sizing failure is not an encoding proof. */
final class DecoderPreflightTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    /** Review fixture, with a configurable raw byte count for the normalization boundary. */
    static CompoundTag rawMission(int rawBytes, boolean normalize) {
        CompoundTag raw = SatelliteNbtCodec.encodeMission(GroundPreflightBenchmark.claimed(0));
        raw.putString("definition_id", normalize ? "d".repeat(128) : "f:" + "d".repeat(126));
        raw.putString("target_body_id", normalize ? "t".repeat(128) : "f:" + "t".repeat(126));
        if (normalize) {
            ListTag rewards = raw.getList("reward", Tag.TAG_COMPOUND);
            for (int i = 0; i < rewards.size(); i++) {
                rewards.getCompound(i).putString("item", (char) ('a' + i) + "x".repeat(127));
            }
        }
        raw.putString("status", "quarantined");
        CompoundTag hold = new CompoundTag();
        hold.putString("reason", "Q".repeat(48));
        hold.putString("previous_status", "claim_pending_discovery");
        hold.putBoolean("receipt_seen", true);
        raw.put("quarantine", hold);
        raw.putString("terminal_level", "f:" + "l".repeat(126));
        raw.putLong("terminal_pos", 0L);
        raw.putUUID("paid_terminal", new UUID(1, 1));
        raw.putBoolean("acknowledged", true);
        raw.putLong("ack_epoch", 1L);
        resizeVersion(raw, rawBytes);
        return raw;
    }

    static void resizeVersion(CompoundTag raw, int bytes) {
        raw.putString("reward_version", "x");
        int versionBytes = bytes - (SatelliteNbtSize.uncompressedBytes(raw) - 1);
        assertTrue(versionBytes >= 1 && versionBytes <= 600);
        raw.putString("reward_version", "\u0800".repeat(versionBytes / 3) + "x".repeat(versionBytes % 3));
        assertTrue(raw.getString("reward_version").length() <= 200);
        assertEquals(bytes, SatelliteNbtSize.uncompressedBytes(raw));
    }

    private static CompoundTag root(boolean account, boolean job, boolean analysis, int missionBytes) {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        if (account) { registry.restoreAccount(ResearchAccount.empty(OWNER)); }
        if (job) {
            GroundSurveyJob record = analysis ? running(0, OWNER, device(300)) : active(0, OWNER);
            if (analysis) {
                record = record.analyzed(record.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 1,
                        record.analysis().claim()), 20);
            }
            registry.restoreInstance(instance(record, analysis ? InstanceState.AVAILABLE : InstanceState.PENDING));
            registry.groundSurveys().restore(record);
        }
        CompoundTag raw = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        raw.getList("missions", Tag.TAG_COMPOUND).add(rawMission(missionBytes, true));
        return raw;
    }

    private static SatelliteMissionRegistry loaded(boolean account, boolean job, boolean analysis) {
        CompoundTag raw = root(account, job, analysis, 4090);
        assertThrows(SatelliteCanonicalBoundsException.class, () -> SatelliteRegistryPayload.decodeCurrent(raw));
        var loaded = detached(raw);
        assertFalse(loaded.restoreReport().changed());
        MissionState mission = loaded.missions().get(0);
        assertEquals(138, mission.definitionId().toString().length());
        assertEquals(4096, CodecRecordSizer.INSTANCE.missionBytes(mission));
        assertThrows(IllegalArgumentException.class, () -> SatelliteNbtCodec.encodeMission(mission));
        return loaded;
    }

    /** Domain fallback remains covered with synthetic residents that D15 now refuses at the production load boundary. */
    private static SatelliteMissionRegistry detached(CompoundTag raw) {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        for (var tag : raw.getList("missions", Tag.TAG_COMPOUND)) {
            registry.restoreMission(SatelliteNbtCodec.decodeMission((CompoundTag) tag));
        }
        for (var tag : raw.getList("research_accounts", Tag.TAG_COMPOUND)) {
            registry.restoreAccount(SatelliteNbtCodec.decodeAccount((CompoundTag) tag));
        }
        for (var tag : raw.getList("instances", Tag.TAG_COMPOUND)) {
            registry.restoreInstance(SatelliteNbtCodec.decodeInstance((CompoundTag) tag));
        }
        for (var tag : raw.getList("ground_surveys", Tag.TAG_COMPOUND)) {
            registry.groundSurveys().restore(GroundSurveyCodec.decode((CompoundTag) tag));
        }
        registry.finishRestore();
        return registry;
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void startOnDecoderAcceptedRootMatchesWholeRootCheck(boolean account) {
        var registry = loaded(account, false, false);
        var request = GroundPreflightBenchmark.start();
        assertFalse(CodecRecordSizer.INSTANCE.groundMutationFits(registry, active(0, OWNER), request.instance(),
                false, registry.account(OWNER), 0, 0));
        refusesUnchanged(registry, () -> registry.groundSurveys().start(request, 0));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void finalAnalysisWithOrWithoutAccountMatchesWholeRootCheck(boolean account) {
        var loaded = loaded(true, true, true);
        // Exercise account creation through the domain restore entry before missing-account repair as in W168.
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        loaded.missions().forEach(registry::restoreMission);
        loaded.instances().forEach(registry::restoreInstance);
        loaded.groundSurveys().all().forEach(registry.groundSurveys()::restore);
        if (account) { loaded.accounts().forEach(registry::restoreAccount); }
        GroundSurveyJob job = registry.groundSurveys().job(id(100)).orElseThrow();
        var next = job.analyzed(job.analysis().with(GroundSurveyJob.AnalysisState.CREDITED, 2,
                job.analysis().claim()), 30);
        assertFalse(CodecRecordSizer.INSTANCE.groundMutationFits(registry, next, null, false,
                ResearchAccount.empty(OWNER).creditAndSpend(120, 0), 30, 30));
        refusesUnchanged(registry, () -> registry.groundSurveys().advanceAnalysis(job.jobId(), device(300), 30));
    }

    @Test void ordinaryChangeOnDecoderAcceptedRootMatchesWholeRootCheck() {
        var registry = loaded(true, true, false);
        GroundSurveyJob job = registry.groundSurveys().job(id(100)).orElseThrow();
        assertFalse(CodecRecordSizer.INSTANCE.groundMutationFits(registry, job.advanced(1),
                registry.instance(job.instanceId()).orElseThrow(), false, null, 1, 1));
        refusesUnchanged(registry, () -> registry.groundSurveys().advanceSurvey(job.jobId(), device(200), 24_000, 1));
    }

    @Test void instanceReleaseAlsoUsesWholeRootCheck() {
        var registry = loaded(true, true, true);
        GroundSurveyJob job = registry.groundSurveys().job(id(100)).orElseThrow();
        var held = instance(job, InstanceState.AVAILABLE).quarantine();
        var restored = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        registry.missions().forEach(restored::restoreMission);
        registry.accounts().forEach(restored::restoreAccount);
        restored.restoreInstance(held);
        restored.groundSurveys().restore(job);
        assertFalse(CodecRecordSizer.INSTANCE.groundMutationFits(restored, job,
                instance(job, InstanceState.AVAILABLE), false, null, 30, 30));
        List<Object> before = snapshot(restored);
        var result = restored.resources().releaseInstance(held.instanceId(), 24_000, 30);
        assertEquals(SatelliteOperationCode.STORAGE_BUDGET, result.code());
        assertFalse(result.changed());
        assertEquals(before, snapshot(restored));
    }

    @Test void savedDataAdapterBlocksUnencodableRootBeforeAnyStart() {
        CompoundTag raw = root(true, false, false, 4090);
        var data = SatelliteMissionSavedData.load(raw);
        assertFalse(data.operational());
        assertEquals(raw, data.save(new CompoundTag()));
        var capture = data.captureForTests();
        assertThrows(IllegalStateException.class,
                () -> data.groundSurveys(jobs -> jobs.start(GroundPreflightBenchmark.start(), 0)));
        assertEquals(capture, data.captureForTests());
        assertEquals(raw, data.save(new CompoundTag()));
    }

    @ParameterizedTest @ValueSource(ints = {3906, 3907})
    void normalizedCanonicalEncodingAtAndOneByteOverTheLimit(int rawBytes) {
        CompoundTag raw = root(true, false, false, rawBytes);
        if (rawBytes > 3906) {
            assertThrows(SatelliteCanonicalBoundsException.class, () -> SatelliteRegistryPayload.decodeCurrent(raw));
        }
        var registry = rawBytes == 3906 ? SatelliteRegistryPayload.decodeCurrent(raw) : detached(raw);
        assertFalse(registry.restoreReport().changed());
        MissionState mission = registry.missions().get(0);
        boolean fits = rawBytes == 3906;
        if (fits) { assertEquals(4096, SatelliteNbtSize.uncompressedBytes(SatelliteNbtCodec.encodeMission(mission))); }
        else { assertThrows(IllegalArgumentException.class, () -> SatelliteNbtCodec.encodeMission(mission)); }
        var request = GroundPreflightBenchmark.start();
        assertEquals(fits, CodecRecordSizer.INSTANCE.groundMutationFits(registry, active(0, OWNER),
                request.instance(), false, registry.account(OWNER), 0, 0));
        if (fits) { assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(request, 0).code()); }
        else { refusesUnchanged(registry, () -> registry.groundSurveys().start(request, 0)); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void provenReservationExactlyAtTheCapKeepsTheFastPath(boolean normalize) {
        MissionState mission = SatelliteNbtCodec.decodeMission(rawMission(normalize ? 3906 : 4096, normalize));
        var reservation = CodecRecordSizer.INSTANCE.missionReservation(mission);
        assertEquals(4096, reservation.bytes());
        assertTrue(reservation.proven());
        var sizer = new ProofCountingSizer();
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        registry.restoreMission(mission);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0).code());
        assertEquals(0, sizer.rootChecks);
    }

    @ParameterizedTest @ValueSource(ints = {4090, 4096})
    void validRecordWithFailedLifecycleSizingUsesFallbackWithoutChangingTheAnswer(int oldBytes) {
        CompoundTag raw = rawMission(4096, false);
        raw.getCompound("quarantine").putString("previous_status", "ready");
        raw.remove("resolved_at");
        resizeVersion(raw, oldBytes);
        MissionState mission = SatelliteNbtCodec.decodeMission(raw);
        assertEquals(raw, SatelliteNbtCodec.encodeMission(mission));
        assertEquals(new RecordSizer.Reservation(4096, false), CodecRecordSizer.INSTANCE.missionReservation(mission));
        var sizer = new ProofCountingSizer();
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        registry.restoreMission(mission);
        var root = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        assertFalse(SatelliteRegistryPayload.decodeCurrent(root).restoreReport().changed());
        var request = GroundPreflightBenchmark.start();
        assertTrue(CodecRecordSizer.INSTANCE.groundMutationFits(registry, active(0, OWNER), request.instance(),
                false, registry.account(OWNER), 0, 0));
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(request, 0).code());
        assertEquals(1, sizer.rootChecks);
        registry.markPersisted();
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().advanceSurvey(id(100), device(200), 24_000, 1).code());
        assertEquals(2, sizer.rootChecks);
    }

    @Test void purgingTheLastUnprovenReservationRestoresTheFastPath() {
        CompoundTag raw = rawMission(4096, false);
        raw.putString("status", "claimed");
        raw.remove("quarantine");
        raw.putBoolean("acknowledged", false);
        raw.remove("ack_epoch");
        // Current CLAIMED encoding fits; restoring the longest quarantine/acknowledgement would need 4097 bytes.
        resizeVersion(raw, SatelliteNbtSize.uncompressedBytes(raw) + 1);
        MissionState mission = SatelliteNbtCodec.decodeMission(raw);
        assertFalse(CodecRecordSizer.INSTANCE.missionReservation(mission).proven());
        var sizer = new ProofCountingSizer();
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        registry.restoreMission(mission);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0).code());
        assertEquals(1, sizer.rootChecks);
        registry.markPersisted();
        assertEquals(SatelliteOperationCode.SUCCESS, registry.resources().purge(mission.missionId()).code());
        assertEquals(0, registry.reservedBytes("MISSIONS"));
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().advanceSurvey(id(100), device(200), 24_000, 1).code());
        assertEquals(1, sizer.rootChecks, "The removal releases the proof flag as well as the bytes");
    }

    @ParameterizedTest @ValueSource(strings = {"JOB", "INSTANCE"})
    void unprovenCandidateUsesFallbackBeforeAnyReservationIsPublished(String candidate) {
        var sizer = new ProofCountingSizer() {
            @Override public Reservation groundSurveyReservation(GroundSurveyJob value) {
                return new Reservation(super.groundSurveyReservation(value).bytes(), !candidate.equals("JOB"));
            }
            @Override public Reservation instanceReservation(AsteroidInstance value) {
                return new Reservation(super.instanceReservation(value).bytes(), !candidate.equals("INSTANCE"));
            }
            @Override public boolean groundMutationFits(SatelliteMissionRegistry registry, GroundSurveyJob job,
                    AsteroidInstance instance, boolean remove, ResearchAccount account, long now, long observed) {
                rootChecks++;
                return false;
            }
        };
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, sizer);
        refusesUnchanged(registry, () -> registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0));
        assertEquals(2, sizer.rootChecks);
    }

    private static class ProofCountingSizer extends GroundPreflightBenchmark.CountingSizer {
        @Override public Reservation satelliteReservation(SatelliteState value) {
            return CodecRecordSizer.INSTANCE.satelliteReservation(value);
        }
        @Override public Reservation missionReservation(MissionState value) {
            return CodecRecordSizer.INSTANCE.missionReservation(value);
        }
        @Override public Reservation instanceReservation(AsteroidInstance value) {
            return CodecRecordSizer.INSTANCE.instanceReservation(value);
        }
        @Override public Reservation groundSurveyReservation(GroundSurveyJob value) {
            return CodecRecordSizer.INSTANCE.groundSurveyReservation(value);
        }
    }

    private static void refusesUnchanged(SatelliteMissionRegistry registry, Supplier<GroundSurveys.Result> action) {
        List<Object> before = snapshot(registry);
        GroundSurveys.Result result = action.get();
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, result.code());
        assertFalse(result.changed());
        assertEquals(before, snapshot(registry));
        // Repeat against the same derived indexes, not a freshly decoded copy.
        assertEquals(result, action.get());
        assertEquals(before, snapshot(registry));
    }

    private static List<Object> snapshot(SatelliteMissionRegistry registry) {
        return List.of(registry.satellites(), registry.missions(), registry.instances(), registry.accounts(),
                registry.groundSurveys().all(), registry.logicalGameTime(), registry.lastObservedGameTime(),
                registry.saveEpoch(), registry.stampEpoch(), registry.epochs().stateChanges(),
                registry.groundSurveys().version(), registry.scheduledCount(), registry.unfinishedMissionCount(),
                registry.finishedMissionCount(), List.of("SATELLITES", "MISSIONS", "INSTANCES", "ACCOUNTS", "GROUND_SURVEYS")
                        .stream().map(registry::reservedBytes).toList());
    }
}

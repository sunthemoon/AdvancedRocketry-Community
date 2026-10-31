package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Checks compositional raw bytes AND the actual Forge native reader's heap accounting. */
final class ResidentEncodingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyResidentTypeAndRepairMatchNativeRootMeasurements() throws IOException {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        exact(registry);
        var craft = SatelliteState.launch(id(9000), TYPE, OWNER, 0).startMission(id(9001));
        registry.restoreSatellite(craft);
        exact(registry);
        registry.restoreMission(GroundPreflightBenchmark.claimed(0));
        exact(registry);
        var ground = active(0, OWNER);
        registry.restoreInstance(instance(ground, InstanceState.PENDING));
        exact(registry);
        registry.groundSurveys().restore(ground);
        exact(registry);
        assertTrue(registry.accounts().isEmpty());
        assertTrue(registry.finishRestore().changed()); // Missing owner account and missing satellite mission.
        exact(registry);
        assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding(craft));
        assertEquals(SatelliteOperationCode.SUCCESS, registry.recoverSatellite(craft.satelliteId()).code());
        exact(registry);
        assertEquals(SatelliteOperationCode.SUCCESS, registry.decommission(craft.satelliteId(), OWNER, false, false).code());
        exact(registry);
        var loaded = SatelliteRegistryPayload.decodeCurrent(GroundExactPreflightTest.encoded(registry));
        exact(loaded);
        assertEquals(registry.residentEncoding(), loaded.residentEncoding());
        var migrated = SatelliteRegistryPayload.decodeCurrent(SatelliteRegistryPayload.upgrade(schema3(), 3));
        exact(migrated);
        var empty = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        assertEquals(RecordSizer.EncodingSize.ZERO, empty.residentEncoding());
    }

    @Test void ordinaryMissionSatelliteAndAccountMutationsStayExact() throws IOException {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        var definition = new SatelliteDefinition(1, TYPE, 20, 120, 1, List.of(SYSTEM));
        assertEquals(SatelliteOperationCode.SUCCESS,
                registry.launch(id(8000), id(8001), OWNER, definition, SYSTEM, 0, false).code());
        exact(registry);
        registry.completeDue(20);
        exact(registry);
        assertEquals(SatelliteOperationCode.SUCCESS, registry.claim(id(8001), OWNER, 20).code());
        exact(registry);
        assertEquals(SatelliteOperationCode.SUCCESS,
                registry.startMission(id(8000), id(8002), OWNER, definition, SYSTEM, 21, false).code());
        exact(registry);
        registry.completeDue(41);
        exact(registry);
        assertEquals(SatelliteOperationCode.SUCCESS, registry.claim(id(8002), OWNER, 41).code());
        exact(registry);
        var solar = SatelliteState.launchIdle(id(8010), TYPE, OWNER, 0, SYSTEM,
                new SatelliteBlueprint(List.of(TYPE), false, SatelliteStats.LEGACY_DATA),
                new SatelliteKindState.Solar(100, Optional.empty()));
        registry.restoreSatellite(solar);
        assertEquals(SatelliteOperationCode.SUCCESS, registry.updateKindState(solar.satelliteId(),
                new SatelliteKindState.Solar(100, Optional.of(id(8011)))).code());
        exact(registry);
        var survey = SatelliteState.launchIdle(id(8020), TYPE, OWNER, 0, SYSTEM,
                new SatelliteBlueprint(List.of(TYPE), false, new SatelliteStats(4, 10_720, 1000, 1, 10)),
                new SatelliteKindState.Survey(10_000, 0, 1000, 32, 8));
        registry.restoreSatellite(survey);
        assertEquals(SatelliteOperationCode.SUCCESS, registry.payScan(survey.satelliteId(), OWNER, false, 42).code());
        exact(registry);
    }

    @Test void groundTransitionsAndRetentionReleaseOldIdentities() throws IOException {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0).code());
        exact(registry);
        registry.markPersisted();
        for (int tick = 1; tick <= 20; tick++) {
            var old = registry.groundSurveys().job(id(100)).orElseThrow();
            assertEquals(GroundSurveys.Code.SUCCESS,
                    registry.groundSurveys().advanceSurvey(old.jobId(), device(200), 24_000, tick).code());
            assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding(old));
            exact(registry);
        }
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().claim(id(100), OWNER, device(300), 21).code());
        exact(registry);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().rebindAnalysis(id(100), OWNER, device(301), 22).code());
        exact(registry);
        registry.markPersisted();
        for (int tick = 23; tick <= 24; tick++) {
            assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().advanceAnalysis(id(100), device(301), tick).code());
            exact(registry);
        }
        registry.completeDue(24_021);
        exact(registry);
        registry.completeDue(2_000_000);
        exact(registry);
        assertTrue(registry.instances().isEmpty());
        assertTrue(registry.groundSurveys().all().isEmpty());
    }

    @Test void cancellingAndPurgingRemoveMeasuredRecords() throws IOException {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        var mission = GroundPreflightBenchmark.claimed(0);
        registry.restoreMission(mission);
        exact(registry);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0).code());
        var instance = registry.instance(id(1000)).orElseThrow();
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().cancel(id(100), OWNER, false, 0).code());
        assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding(instance));
        exact(registry);
        registry.markPersisted();
        assertEquals(SatelliteOperationCode.SUCCESS, registry.resources().purge(mission.missionId()).code());
        assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding(mission));
        exact(registry);
    }

    @Test void failedEagerSizingDoesNotRefuseLoadOrHideAResidentFromPreflight() throws IOException {
        var sizer = new MeasuringSizer();
        sizer.fail = true;
        var registry = SatelliteMissionRegistry.create(0, sizer);
        var mission = GroundPreflightBenchmark.claimed(0);
        assertDoesNotThrow(() -> registry.restoreMission(mission));
        assertFalse(registry.residentEncoding().known());
        sizer.fail = false;
        assertTrue(GroundExactPreflightTest.equivalent(registry, active(0, OWNER),
                GroundPreflightBenchmark.start().instance(), false, registry.account(OWNER), 0));
        assertFalse(registry.residentEncoding().known(), "A successful detached fallback does not publish cache state");
        assertEquals(SatelliteOperationCode.SUCCESS, registry.resources().purge(mission.missionId()).code());
        assertTrue(registry.residentEncoding().known());
        exact(registry);
        registry.restoreMission(mission);
        exact(registry);
        var before = registry.residentEncoding();
        assertThrows(IllegalArgumentException.class, () -> registry.restoreMission(mission));
        assertEquals(before, registry.residentEncoding());
    }

    @Test void unencodableDecodedResidentStillRefusesAndIsNotHiddenByCaching() {
        CompoundTag root = GroundSurveyFixtures.root(List.of(), List.of());
        root.getList("missions", Tag.TAG_COMPOUND).add(DecoderPreflightTest.rawMission(4090, true));
        assertThrows(SatelliteCanonicalBoundsException.class, () -> SatelliteRegistryPayload.decodeCurrent(root));
        // Retain the caching/fallback regression below the production load boundary that D15 now closes.
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        registry.restoreMission(SatelliteNbtCodec.decodeMission(root.getList("missions", Tag.TAG_COMPOUND).getCompound(0)));
        assertFalse(registry.residentEncoding().known());
        assertFalse(GroundExactPreflightTest.equivalent(registry, active(0, OWNER),
                GroundPreflightBenchmark.start().instance(), false, registry.account(OWNER), 0));
        assertEquals(GroundSurveys.Code.STORAGE_BUDGET, registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0).code());
    }

    @Test void failedReplacementInvalidatesThePreviousMeasurementAndRemovalClearsIt() throws IOException {
        var sizer = new MeasuringSizer();
        var registry = SatelliteMissionRegistry.create(0, sizer);
        var solar = SatelliteState.launchIdle(id(9000), TYPE, OWNER, 0, SYSTEM,
                new SatelliteBlueprint(List.of(TYPE), false, SatelliteStats.LEGACY_DATA),
                new SatelliteKindState.Solar(100, Optional.empty()));
        registry.restoreSatellite(solar);
        exact(registry);
        sizer.fail = true;
        assertEquals(SatelliteOperationCode.SUCCESS, registry.updateKindState(solar.satelliteId(),
                new SatelliteKindState.Solar(100, Optional.of(id(9001)))).code());
        assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding(solar));
        assertFalse(registry.residentEncoding().known());
        assertTrue(GroundExactPreflightTest.equivalent(registry, active(0, OWNER),
                GroundPreflightBenchmark.start().instance(), false, registry.account(OWNER), 0));
        assertEquals(SatelliteOperationCode.SUCCESS, registry.decommission(solar.satelliteId(), OWNER, false, true).code());
        assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding());
        sizer.fail = false;
        registry.restoreSatellite(solar);
        exact(registry);
    }

    @ParameterizedTest @ValueSource(ints = {4090, 4096})
    void failedLifecycleSizingCanStillHaveAnExactCurrentEncoding(int bytes) throws IOException {
        CompoundTag raw = DecoderPreflightTest.rawMission(4096, false);
        raw.getCompound("quarantine").putString("previous_status", "ready");
        raw.remove("resolved_at");
        DecoderPreflightTest.resizeVersion(raw, bytes);
        var mission = SatelliteNbtCodec.decodeMission(raw);
        assertFalse(CodecRecordSizer.INSTANCE.missionReservation(mission).proven());
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        registry.restoreMission(mission);
        exact(registry);
        assertTrue(GroundExactPreflightTest.equivalent(registry, active(0, OWNER),
                GroundPreflightBenchmark.start().instance(), false, registry.account(OWNER), 0));
    }

    @Test void nearBoundStartOnlyMeasuresNewResidents() {
        var sizer = new MeasuringSizer();
        var registry = SatelliteMissionRegistry.create(0, sizer);
        GroundExactPreflightTest.fillTo(registry, SatelliteLimits.MAX_REGISTRY_NBT_BYTES - 32 * 1024);
        int missionCalls = sizer.missions;
        assertTrue(missionCalls > 5000);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0).code());
        assertEquals(missionCalls, sizer.missions, "No resident mission is encoded for START");
    }

    @Test void epochAndInvariantRefusalsRetainMeasurements() {
        var full = SatelliteMissionRegistry.restore(0, 0, Long.MAX_VALUE, CodecRecordSizer.INSTANCE);
        assertThrows(ArithmeticException.class, () -> CodecRecordSizer.INSTANCE.groundMutationFits(full,
                active(0, OWNER), GroundPreflightBenchmark.start().instance(), false, full.account(OWNER), 0, 0));
        assertEquals(GroundSurveys.Code.CAPACITY_REACHED, full.groundSurveys().start(GroundPreflightBenchmark.start(), 0).code());
        assertEquals(RecordSizer.EncodingSize.ZERO, full.residentEncoding());
        var broken = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        var job = active(0, OWNER);
        broken.groundSurveys().restore(job);
        var before = broken.residentEncoding();
        assertEquals(GroundSurveys.Code.INVARIANT_FAILED,
                broken.groundSurveys().advanceSurvey(job.jobId(), job.source(), 24_000, 1).code());
        assertEquals(before, broken.residentEncoding());
    }

    private static void exact(SatelliteMissionRegistry registry) throws IOException {
        var empty = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        CompoundTag framing = GroundExactPreflightTest.encoded(empty);
        CompoundTag root = GroundExactPreflightTest.encoded(registry);
        var size = registry.residentEncoding();
        assertTrue(size.known());
        assertEquals(SatelliteNbtSize.uncompressedBytes(root), SatelliteNbtSize.uncompressedBytes(framing) + size.bytes());
        assertEquals(heap(root), heap(framing) + size.heapBytes());
        assertTrue(ExactGroundPreflight.fits(size));
    }

    private static long heap(CompoundTag root) throws IOException {
        CompoundTag outer = new CompoundTag();
        outer.put("data", root);
        var bytes = new ByteArrayOutputStream();
        NbtIo.write(outer, new DataOutputStream(bytes));
        var counter = new NbtAccounter(Long.MAX_VALUE);
        assertEquals(outer, NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), counter));
        return counter.getUsage();
    }

    private static class MeasuringSizer extends GroundPreflightBenchmark.CountingSizer {
        boolean fail;
        int missions;
        @Override public EncodingSize encodingSize(Object record) {
            if (fail) { throw new IllegalStateException("Injected eager measurement failure"); }
            if (record instanceof MissionState) { missions++; }
            return CodecRecordSizer.INSTANCE.encodingSize(record);
        }
    }
}

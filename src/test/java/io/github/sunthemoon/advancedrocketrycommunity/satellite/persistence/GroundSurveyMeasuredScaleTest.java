package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.IntConsumer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The ordinary-advance scale check with W216's eager native measurement included in every publication. */
final class GroundSurveyMeasuredScaleTest {
    private static final long ADVANCE_BUDGET_NANOS = 1_000_000L;
    private static final int WARMUPS = 100;
    private static final int SAMPLES = 101;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void ordinarySurveyAndAnalysisAdvancesIncludeRealResidentMeasurement() {
        var sizer = new MeasuringSizer();
        var registry = SatelliteMissionRegistry.create(0, sizer);
        // Match GroundSurveyScaleTest's review registry, clocks and non-final advances.
        for (int i = 0; i < 2_000; i++) {
            registry.restoreInstance(pending(10_000 + i, id(20_000 + i), InstanceOrigin.survey(id(900)))
                    .transition(InstanceState.AVAILABLE, OptionalLong.of(1_728_000), Optional.empty()));
        }
        var jobs = registry.groundSurveys();
        for (int i = 0; i < 700; i++) { jobs.restore(ready(i, id(30_000 + i))); }
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(5_000, 1_000), 0).code());
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.start(start(5_001, 20), 0).code());
        assertEquals(2_002, registry.instances().size());
        assertEquals(702, jobs.all().size());
        int rootBytes = SatelliteNbtSize.uncompressedBytes(
                SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
        registry.markPersisted();
        var survey = id(5_100);
        var analysis = id(5_101);
        for (int tick = 1; tick <= 20; tick++) {
            assertEquals(GroundSurveys.Code.SUCCESS,
                    jobs.advanceSurvey(analysis, device(5_201), 24_000, tick).code());
        }
        assertEquals(GroundSurveys.Code.SUCCESS, jobs.claim(analysis, OWNER, device(300), 20).code());
        registry.markPersisted();

        assertKnownResidents(registry);
        GroundSurveyJob oldSurvey = jobs.job(survey).orElseThrow();
        assertEquals(CodecRecordSizer.INSTANCE.encodingSize(oldSurvey), registry.residentEncoding(oldSurvey));
        int measurements = sizer.measurements;
        int checks = sizer.rootChecks;
        long surveyMedian = median(index -> assertEquals(GroundSurveys.Code.SUCCESS,
                jobs.advanceSurvey(survey, device(5_200), 24_000, 21 + index).code()));
        assertEquals(WARMUPS + SAMPLES, sizer.measurements - measurements,
                "each ordinary survey publication measures exactly its changed job");
        assertKnownResidents(registry);
        GroundSurveyJob advancedSurvey = jobs.job(survey).orElseThrow();
        assertEquals(WARMUPS + SAMPLES, advancedSurvey.surveyProgress());
        assertEquals(GroundSurveyJob.Phase.ACTIVE, advancedSurvey.phase());
        assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding(oldSurvey));
        assertEquals(CodecRecordSizer.INSTANCE.encodingSize(advancedSurvey), registry.residentEncoding(advancedSurvey));

        GroundSurveyJob oldAnalysis = jobs.job(analysis).orElseThrow();
        assertEquals(CodecRecordSizer.INSTANCE.encodingSize(oldAnalysis), registry.residentEncoding(oldAnalysis));
        measurements = sizer.measurements;
        long analysisMedian = median(index -> assertEquals(GroundSurveys.Code.SUCCESS,
                jobs.advanceAnalysis(analysis, device(300), 400 + index).code()));
        assertEquals(WARMUPS + SAMPLES, sizer.measurements - measurements,
                "each ordinary analysis publication measures exactly its changed job");
        assertKnownResidents(registry);
        GroundSurveyJob advancedAnalysis = jobs.job(analysis).orElseThrow();
        assertEquals(WARMUPS + SAMPLES, advancedAnalysis.analysis().progress());
        assertEquals(GroundSurveyJob.AnalysisState.RUNNING, advancedAnalysis.analysis().state());
        assertEquals(RecordSizer.EncodingSize.ZERO, registry.residentEncoding(oldAnalysis));
        assertEquals(CodecRecordSizer.INSTANCE.encodingSize(advancedAnalysis), registry.residentEncoding(advancedAnalysis));
        assertEquals(checks, sizer.rootChecks, "ordinary advances still use the reservation proof");

        System.out.printf("ARCE_W218_ADVANCE instances=%d jobs=%d root_bytes=%d warmups=%d samples=%d"
                + " survey_median_us=%.1f analysis_median_us=%.1f%n", registry.instances().size(), jobs.all().size(),
                rootBytes, WARMUPS, SAMPLES, surveyMedian / 1e3, analysisMedian / 1e3);
        assertTrue(surveyMedian < ADVANCE_BUDGET_NANOS, "Survey advance median " + surveyMedian / 1e3 + " us");
        assertTrue(analysisMedian < ADVANCE_BUDGET_NANOS, "Analysis advance median " + analysisMedian / 1e3 + " us");
    }

    /** Outside the timed interval: UNKNOWN and missing (ZERO) entries must both fail, including unchanged residents. */
    private static void assertKnownResidents(SatelliteMissionRegistry registry) {
        assertTrue(registry.residentEncoding().known(), "resident measurements must be known before/after advances");
        var records = new java.util.ArrayList<Object>();
        records.addAll(registry.instances());
        records.addAll(registry.groundSurveys().all());
        records.addAll(registry.accounts());
        var sum = RecordSizer.EncodingSize.ZERO;
        for (Object record : records) {
            var size = registry.residentEncoding(record);
            assertTrue(size.known(), "resident measurement must be known");
            assertTrue(size.bytes() > 0 && size.heapBytes() > 0, "resident must have a nonempty measurement");
            sum = sum.plus(size);
        }
        assertEquals(sum, registry.residentEncoding());
    }

    private static long median(IntConsumer advance) {
        for (int index = 0; index < WARMUPS; index++) { advance.accept(index); }
        long[] samples = new long[SAMPLES];
        for (int index = 0; index < samples.length; index++) {
            long begin = System.nanoTime();
            advance.accept(WARMUPS + index);
            samples[index] = System.nanoTime() - begin;
        }
        Arrays.sort(samples);
        return samples[samples.length / 2];
    }

    private static GroundSurveys.Start start(long index, int surveyTicks) {
        AsteroidInstance instance = pending(index, OWNER, InstanceOrigin.ground(id(100 + index)));
        return new GroundSurveys.Start(id(100 + index), OWNER, device(200 + index), instance, surveyTicks, 20,
                1_000, 20, 120);
    }

    private static final class MeasuringSizer extends GroundPreflightBenchmark.CountingSizer {
        int measurements;

        @Override public EncodingSize encodingSize(Object record) {
            measurements++;
            return CodecRecordSizer.INSTANCE.encodingSize(record);
        }
    }
}

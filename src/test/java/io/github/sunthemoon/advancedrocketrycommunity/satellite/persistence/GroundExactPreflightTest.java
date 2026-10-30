package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.management.ManagementFactory;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

final class GroundExactPreflightTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void firstNearBoundStartDoesNotAllocateAWholeRoot(boolean reload) {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        fillTo(registry, SatelliteLimits.MAX_REGISTRY_NBT_BYTES - 32 * 1024);
        if (reload) { registry = SatelliteRegistryPayload.decodeCurrent(encoded(registry)); }
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        var result = registry.groundSurveys().start(GroundPreflightBenchmark.start(), 0);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertEquals(GroundSurveys.Code.SUCCESS, result.code());
        assertTrue(allocated < 1024 * 1024, "START allocated " + allocated + " bytes; no resident root encode allowed");
    }

    @ParameterizedTest
    @CsvSource({"-1,false", "0,false", "1,false", "-1,true", "0,true", "1,true"})
    void exactByteEdgesMatchWholeRootAndKeepRefusalsAtomic(int excess, boolean reload) {
        var registry = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE);
        GroundSurveyJob job = active(0, OWNER);
        var request = GroundPreflightBenchmark.start();
        ResearchAccount account = ResearchAccount.empty(OWNER);
        int added = bytes(WholeRootGroundPreflight.edited(registry, job, request.instance(), false, account, 0, 0))
                - bytes(encoded(registry));
        fillTo(registry, SatelliteLimits.MAX_REGISTRY_NBT_BYTES + excess - added);
        if (reload) { registry = SatelliteRegistryPayload.decodeCurrent(encoded(registry)); }
        assertEquals(SatelliteLimits.MAX_REGISTRY_NBT_BYTES + excess,
                bytes(WholeRootGroundPreflight.edited(registry, job, request.instance(), false, account, 0, 0)));
        assertEquals(excess <= 0, equivalent(registry, job, request.instance(), false, account, 0));
        var before = encoded(registry);
        var epochs = registry.epochs().stateChanges();
        var result = registry.groundSurveys().start(request, 0);
        assertEquals(excess <= 0 ? GroundSurveys.Code.SUCCESS : GroundSurveys.Code.STORAGE_BUDGET, result.code());
        if (excess > 0) {
            assertEquals(before, encoded(registry));
            assertEquals(epochs, registry.epochs().stateChanges());
            assertEquals(GroundSurveys.Code.STORAGE_BUDGET, registry.groundSurveys().start(request, 0).code());
            assertEquals(before, encoded(registry));
        }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void lifecycleAndAccountCreationMatchOracleAfterEveryChange(boolean reload) {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        fillTo(registry, SatelliteLimits.MAX_REGISTRY_NBT_BYTES - 32 * 1024);
        if (reload) { registry = SatelliteRegistryPayload.decodeCurrent(encoded(registry)); }
        var request = GroundPreflightBenchmark.start();
        assertTrue(equivalent(registry, active(0, OWNER), request.instance(), false, registry.account(OWNER), 0));
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(request, 0).code());
        registry.markPersisted();
        for (int tick = 1; tick <= 20; tick++) {
            var job = registry.groundSurveys().job(id(100)).orElseThrow().advanced(tick);
            var instance = registry.instance(job.instanceId()).orElseThrow();
            if (tick == 20) {
                instance = instance.transition(InstanceState.AVAILABLE,
                        java.util.OptionalLong.of(tick + 24_000), java.util.Optional.empty());
            }
            assertTrue(equivalent(registry, job, instance, false, null, tick));
            assertEquals(GroundSurveys.Code.SUCCESS,
                    registry.groundSurveys().advanceSurvey(id(100), device(200), 24_000, tick).code());
        }
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().claim(id(100), OWNER, device(300), 21).code());
        registry.markPersisted();
        for (int tick = 1; tick <= 2; tick++) {
            var job = registry.groundSurveys().job(id(100)).orElseThrow();
            var next = job.analyzed(job.analysis().with(tick == 2 ? GroundSurveyJob.AnalysisState.CREDITED
                    : GroundSurveyJob.AnalysisState.RUNNING, tick, job.analysis().claim()), 21 + tick);
            assertTrue(equivalent(registry, next, null, false,
                    tick == 2 ? registry.account(OWNER).creditAndSpend(120, 0) : null, 21 + tick));
            assertEquals(GroundSurveys.Code.SUCCESS,
                    registry.groundSurveys().advanceAnalysis(job.jobId(), device(300), 21 + tick).code());
        }
        var second = active(1, OWNER);
        var other = new GroundSurveys.Start(second.jobId(), OWNER, second.source(),
                instance(second, InstanceState.PENDING), 20, 20, 2, 20, 120);
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().start(other, 24).code());
        var resident = registry.groundSurveys().job(second.jobId()).orElseThrow();
        assertTrue(equivalent(registry, resident.cancelled(25), registry.instance(second.instanceId()).orElseThrow(),
                true, null, 25));
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().cancel(second.jobId(), OWNER, false, 25).code());
        assertTrue(equivalent(registry, active(2, OWNER), instance(active(2, OWNER), InstanceState.PENDING),
                false, registry.account(OWNER), 26));
    }

    @Test void migratedRootAndMissingAccountCompletionMatchOracle() {
        var migrated = SatelliteRegistryPayload.decodeCurrent(SatelliteRegistryPayload.upgrade(schema3(), 3));
        assertTrue(equivalent(migrated, active(90, OWNER), instance(active(90, OWNER), InstanceState.PENDING),
                false, migrated.account(OWNER), 0));
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        var running = running(0, OWNER, device(300));
        running = running.analyzed(running.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 1,
                running.analysis().claim()), 20);
        registry.groundSurveys().restore(running);
        registry.restoreInstance(instance(running, InstanceState.AVAILABLE));
        fillTo(registry, SatelliteLimits.MAX_REGISTRY_NBT_BYTES - 32 * 1024);
        var next = running.analyzed(running.analysis().with(GroundSurveyJob.AnalysisState.CREDITED, 2,
                running.analysis().claim()), 30);
        assertTrue(equivalent(registry, next, null, false, registry.account(OWNER).creditAndSpend(120, 0), 30));
        assertEquals(GroundSurveys.Code.SUCCESS, registry.groundSurveys().advanceAnalysis(id(100), device(300), 30).code());
        assertEquals(120, registry.account(OWNER).balance());
        assertTrue(equivalent(registry, active(1, OWNER), instance(active(1, OWNER), InstanceState.PENDING),
                false, registry.account(OWNER), 31));
    }

    static boolean equivalent(SatelliteMissionRegistry registry, GroundSurveyJob job, AsteroidInstance instance,
            boolean remove, ResearchAccount account, long time) {
        boolean expected = WholeRootGroundPreflight.fits(registry, job, instance, remove, account, time, time);
        assertEquals(expected, CodecRecordSizer.INSTANCE.groundMutationFits(registry, job, instance, remove,
                account, time, time));
        return expected;
    }

    /** Exact filler with canonical claimed missions, accounts and one variable-width reward version. */
    static void fillTo(SatelliteMissionRegistry registry, int target) {
        int remaining = target - bytes(encoded(registry));
        int missionBytes = bytes(SatelliteNbtCodec.encodeMission(GroundPreflightBenchmark.claimed(0))) - 3;
        int count = remaining / missionBytes;
        assertTrue(count > 0);
        for (int i = 0; i < count - 1; i++) { registry.restoreMission(GroundPreflightBenchmark.claimed(i)); }
        remaining -= count * missionBytes;
        int accountBytes = bytes(SatelliteNbtCodec.encodeAccount(ResearchAccount.empty(id(50_000)))) - 3;
        for (int i = 0; remaining >= accountBytes; i++) {
            registry.restoreAccount(ResearchAccount.empty(id(50_000 + i)));
            remaining -= accountBytes;
        }
        CompoundTag last = SatelliteNbtCodec.encodeMission(GroundPreflightBenchmark.claimed(count - 1));
        DecoderPreflightTest.resizeVersion(last, bytes(last) + remaining);
        registry.restoreMission(SatelliteNbtCodec.decodeMission(last));
        assertEquals(target, bytes(encoded(registry)));
    }

    static CompoundTag encoded(SatelliteMissionRegistry registry) {
        return SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
    }

    private static int bytes(CompoundTag tag) { return SatelliteNbtSize.uncompressedBytes(tag); }
}

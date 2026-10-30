package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class GroundSectionPreflightTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @ParameterizedTest
    @CsvSource({"GROUND_SURVEYS,-1", "GROUND_SURVEYS,0", "GROUND_SURVEYS,1", "INSTANCES,-1", "INSTANCES,0", "INSTANCES,1"})
    void sectionEdgesMatchTheWholeRootImplementation(String section, int excess) {
        var exact = filled(section, excess, false);
        var oracle = filled(section, excess, true);
        assertEquals(exact.reservedBytes(section), oracle.reservedBytes(section));
        var before = GroundExactPreflightTest.encoded(exact);
        assertTrue(GroundExactPreflightTest.equivalent(exact, active(0, OWNER),
                GroundPreflightBenchmark.start().instance(), false, exact.account(OWNER), 0));
        var expected = oracle.groundSurveys().start(GroundPreflightBenchmark.start(), 0);
        var actual = exact.groundSurveys().start(GroundPreflightBenchmark.start(), 0);
        assertEquals(expected, actual);
        assertEquals(excess <= 0 ? GroundSurveys.Code.SUCCESS : GroundSurveys.Code.STORAGE_BUDGET, actual.code());
        assertEquals(GroundExactPreflightTest.encoded(oracle), GroundExactPreflightTest.encoded(exact));
        if (excess > 0) { assertEquals(before, GroundExactPreflightTest.encoded(exact)); }
    }

    private static SatelliteMissionRegistry filled(String section, int excess, boolean oracle) {
        var sizer = new SectionSizer(section, oracle);
        var registry = SatelliteMissionRegistry.create(0, sizer);
        int added = section.equals("GROUND_SURVEYS") ? sizer.groundSurveyBytes(active(0, OWNER))
                : sizer.instanceBytes(GroundPreflightBenchmark.start().instance());
        int limit = section.equals("GROUND_SURVEYS") ? 1024 * 1024 : 2 * 1024 * 1024;
        int remaining = limit + excess - added;
        for (int i = 0; remaining > 0; i++) {
            sizer.reservation = Math.min(4096, remaining);
            if (section.equals("GROUND_SURVEYS")) {
                var job = active(20_000 + i, id(30_000 + i));
                assertTrue(sizer.reservation >= CodecRecordSizer.INSTANCE.groundSurveyBytes(job));
                registry.restoreInstance(instance(job, InstanceState.PENDING));
                registry.groundSurveys().restore(job);
            } else {
                var instance = pending(20_000 + i, id(30_000 + i), InstanceOrigin.survey(id(40_000 + i)));
                assertTrue(sizer.reservation >= CodecRecordSizer.INSTANCE.instanceBytes(instance));
                registry.restoreInstance(instance);
            }
            remaining -= sizer.reservation;
        }
        sizer.reservation = -1;
        return registry;
    }

    private static final class SectionSizer extends GroundPreflightBenchmark.CountingSizer {
        private final String section;
        private final boolean oracle;
        private int reservation = -1;
        SectionSizer(String section, boolean oracle) { this.section = section; this.oracle = oracle; }
        @Override public EncodingSize encodingSize(Object record) { return CodecRecordSizer.INSTANCE.encodingSize(record); }
        @Override public Reservation groundSurveyReservation(GroundSurveyJob job) {
            return section.equals("GROUND_SURVEYS") && reservation >= 0 ? new Reservation(reservation, true)
                    : super.groundSurveyReservation(job);
        }
        @Override public Reservation instanceReservation(AsteroidInstance instance) {
            return section.equals("INSTANCES") && reservation >= 0 ? new Reservation(reservation, true)
                    : super.instanceReservation(instance);
        }
        @Override public boolean groundMutationFits(SatelliteMissionRegistry registry, GroundSurveyJob job,
                AsteroidInstance instance, boolean remove, ResearchAccount account, long now, long observed) {
            return oracle ? WholeRootGroundPreflight.fits(registry, job, instance, remove, account, now, observed)
                    : super.groundMutationFits(registry, job, instance, remove, account, now, observed);
        }
    }
}

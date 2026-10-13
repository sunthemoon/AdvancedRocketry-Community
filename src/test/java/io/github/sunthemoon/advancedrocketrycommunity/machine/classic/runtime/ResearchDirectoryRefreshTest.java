package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.CodecRecordSizer;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W21c review L6: the per-tick directory cost at the 2,048-job limit is a version check, not a rebuild. */
class ResearchDirectoryRefreshTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ResourceLocation EARTH = ResourceLocation.tryParse("minecraft:overworld");

    @Test void anUnchangedGroundSectionCostsNoRebuildAtTheJobLimit() {
        var surveys = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE).groundSurveys();
        var jobs = new ArrayList<GroundSurveyJob>();
        for (int i = 0; i < SatelliteLimits.MAX_GROUND_SURVEYS; i++) { jobs.add(job(i)); }
        jobs.subList(0, jobs.size() - 1).forEach(surveys::restore);
        var directory = new ResearchDeviceDirectory(); int[] reads = {0};
        Supplier<List<GroundSurveyJob>> all = () -> { reads[0]++; return surveys.all(); };
        assertTrue(directory.refresh(surveys, surveys.version(), all));
        surveys.restore(jobs.get(jobs.size() - 1));
        assertTrue(directory.refresh(surveys, surveys.version(), all), "A ground change must rebuild");
        assertEquals(2, reads[0]);
        // One server tick runs one refresh per enqueue and one in the scheduler; 16,384 loaded controllers is the
        // directory's own bound. None of them reads the 2,048 jobs while the section is unchanged.
        for (int tick = 0; tick < 4; tick++) {
            for (int call = 0; call <= ResearchDeviceDirectory.MAX_LOADED; call++) {
                assertFalse(directory.refresh(surveys, surveys.version(), all));
            }
        }
        assertEquals(2, reads[0], "An unchanged section was read again");
        for (var job : jobs) {
            assertEquals(job.phase() == Phase.ACTIVE, directory.working(job.source()), "Source work index");
            job.analysis().claim().ifPresent(claim -> assertEquals(job.analysis().state() == AnalysisState.RUNNING,
                    directory.working(claim.processor()), "Processor work index"));
        }
        assertFalse(directory.working(new Device(UUID.randomUUID(), EARTH, 0)));
        var reloaded = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE).groundSurveys();
        assertTrue(directory.refresh(reloaded, surveys.version(), reloaded::all), "Another section object must rebuild");
        assertFalse(directory.working(jobs.get(0).source()));
        directory.clear();
        assertTrue(directory.refresh(surveys, surveys.version(), all), "A cleared directory must rebuild");
    }

    /** Owners of 16, cycling ACTIVE, READY waiting, READY with a RUNNING claim and PROCESSED with a CREDITED claim. */
    private static GroundSurveyJob job(int index) {
        var owner = new UUID(1, index / 16); var source = new Device(UUID.randomUUID(), EARTH, index);
        var snapshot = new Snapshot(EARTH, EARTH, "0123456789abcdef", "0123456789abcdef", 1, "0123456789abcdef");
        var job = new GroundSurveyJob(1, UUID.randomUUID(), owner, UUID.randomUUID(), source, snapshot, 20, 20, 0, 0, 1,
                Phase.ACTIVE, OptionalLong.empty(), OptionalLong.empty(),
                new Analysis(AnalysisState.WAITING, 200, 20, 120, 0, Optional.empty()), Optional.empty());
        if (index % 4 == 0) { return job; }
        for (int tick = 0; tick < 20; tick++) { job = job.advanced(tick + 1); }
        var claim = Optional.of(new Claim(2, new Device(UUID.randomUUID(), EARTH, -1 - index)));
        return switch (index % 4) {
            case 1 -> job;
            case 2 -> job.analyzed(new Analysis(AnalysisState.RUNNING, 200, 20, 120, 1, claim), 30);
            default -> job.analyzed(new Analysis(AnalysisState.CREDITED, 200, 20, 120, 200, claim), 30);
        };
    }
}

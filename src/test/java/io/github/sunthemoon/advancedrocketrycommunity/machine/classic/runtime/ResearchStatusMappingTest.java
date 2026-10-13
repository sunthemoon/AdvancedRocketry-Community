package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveys;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveys.Code;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.CodecRecordSizer;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W21c review L1: each registry refusal names its reason, and only R4's offline cases read as offline repair. */
class ResearchStatusMappingTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ResourceLocation EARTH = ResourceLocation.tryParse("minecraft:overworld");

    @Test void everyRegistryCodeNamesItsOwnReason() {
        for (Code code : Code.values()) {
            ResearchStatus status = ResearchService.status(code);
            if (code != Code.NOT_READY) { assertNotEquals(ResearchStatus.NOT_READY, status, code.name()); }
            assertNotEquals(ResearchStatus.DEVICE_COLLISION, status, code.name() + " is not a copied device");
        }
        var expected = Map.of(Code.INVARIANT_FAILED, ResearchStatus.OFFLINE_REPAIR_REQUIRED,
                Code.OFFLINE_REPAIR_REQUIRED, ResearchStatus.OFFLINE_REPAIR_REQUIRED, Code.QUARANTINED, ResearchStatus.QUARANTINED,
                Code.JOB_NOT_FOUND, ResearchStatus.JOB_NOT_FOUND, Code.CANCELLED, ResearchStatus.CANCELLED,
                Code.IDENTITY_CONFLICT, ResearchStatus.IDENTITY_CONFLICT, Code.SOURCE_MISMATCH, ResearchStatus.SOURCE_MISMATCH,
                Code.ACCOUNT_FULL, ResearchStatus.ACCOUNT_FULL, Code.ALREADY_CLAIMED, ResearchStatus.ALREADY_CLAIMED);
        expected.forEach((code, status) -> assertEquals(status, ResearchService.status(code), code.name()));
    }

    @Test void onlyOfflineOnlyQuarantinesReadAsOfflineRepair() {
        GroundSurveys surveys = SatelliteMissionRegistry.create(0, CodecRecordSizer.INSTANCE).groundSurveys();
        var processor = new Device(UUID.randomUUID(), EARTH, 9);
        var activePair = job().quarantine("GROUND_SNAPSHOT_MISMATCH");
        var ready = completed(job()).quarantine("GROUND_SNAPSHOT_MISMATCH");
        var running = completed(job()).analyzed(new Analysis(AnalysisState.RUNNING, 200, 20, 120, 3, Optional.of(new Claim(2, processor))), 30);
        var duplicate = completed(job()).analyzed(new Analysis(AnalysisState.RUNNING, 200, 20, 120, 5, Optional.of(new Claim(2, processor))), 30)
                .quarantine("DUPLICATE_PROCESSOR");
        for (var job : new GroundSurveyJob[]{activePair, ready, running, duplicate}) { surveys.restore(job); }
        assertEquals(ResearchStatus.OFFLINE_REPAIR_REQUIRED, ResearchService.quarantined(surveys.recoveryHint(activePair.jobId())));
        assertEquals(ResearchStatus.OFFLINE_REPAIR_REQUIRED, ResearchService.quarantined(surveys.recoveryHint(duplicate.jobId())));
        assertEquals(ResearchStatus.QUARANTINED, ResearchService.quarantined(surveys.recoveryHint(ready.jobId())));
        assertNotEquals(GroundSurveys.Code.OFFLINE_REPAIR_REQUIRED, surveys.release(ready.jobId()).code(), "The QUARANTINED label is not offline-only");
        assertEquals(GroundSurveys.Code.OFFLINE_REPAIR_REQUIRED, surveys.release(activePair.jobId()).code());
    }

    private static GroundSurveyJob job() {
        var snapshot = new Snapshot(EARTH, EARTH, "0123456789abcdef", "0123456789abcdef", 1, "0123456789abcdef");
        return new GroundSurveyJob(1, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new Device(UUID.randomUUID(), EARTH, 1),
                snapshot, 20, 20, 0, 0, 1, Phase.ACTIVE, OptionalLong.empty(), OptionalLong.empty(),
                new Analysis(AnalysisState.WAITING, 200, 20, 120, 0, Optional.empty()), Optional.empty());
    }
    private static GroundSurveyJob completed(GroundSurveyJob job) {
        for (int tick = 0; tick < 20; tick++) { job = job.advanced(tick + 1); }
        return job;
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class GroundSurveyCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Stream<GroundSurveyJob> variants() {
        GroundSurveyJob active = active(0, OWNER).advanced(1);
        return Stream.of(active, ready(0, OWNER), running(0, OWNER, device(300)), processed(0, OWNER),
                active.cancelled(5)).flatMap(job -> Stream.of(job, job.quarantine("Q".repeat(48))));
    }

    @ParameterizedTest @MethodSource("variants")
    void everyPhaseAndQuarantinePreservesProgressAndCredit(GroundSurveyJob job) {
        assertEquals(job, GroundSurveyCodec.decode(GroundSurveyCodec.encode(job)));
        GroundSurveyJob held = job.quarantine("FIRST");
        assertEquals(held, held.quarantine("SECOND"));
        assertEquals(job.effectivePhase(), held.effectivePhase());
        assertEquals(job.analysis(), held.analysis());
        assertEquals(job.surveyProgress(), held.surveyProgress());
        assertTrue(SatelliteNbtSize.uncompressedBytes(GroundSurveyCodec.encode(job))
                <= CodecRecordSizer.INSTANCE.groundSurveyBytes(job));
    }

    @Test void actualLargestLifecycleIncludesCreditedClaimQuarantineAndWideSource() {
        ResourceLocation longest = ResourceLocation.tryParse("x:" + "a".repeat(126));
        GroundSurveyJob base = processed(0, OWNER);
        var snapshot = new GroundSurveyJob.Snapshot(longest, longest, VERSION, VERSION, Long.MIN_VALUE, VERSION);
        var source = new GroundSurveyJob.Device(id(200), longest, Long.MAX_VALUE);
        var analysis = new GroundSurveyJob.Analysis(GroundSurveyJob.AnalysisState.CREDITED, 72_000, 10_000,
                10_000, 72_000, Optional.of(new GroundSurveyJob.Claim(Long.MAX_VALUE, source)));
        GroundSurveyJob largest = new GroundSurveyJob(1, base.jobId(), OWNER, base.instanceId(), source, snapshot,
                72_000, 10_000, 72_000, 0, Long.MAX_VALUE, GroundSurveyJob.Phase.PROCESSED,
                OptionalLong.of(Long.MAX_VALUE), OptionalLong.of(Long.MAX_VALUE), analysis, Optional.empty())
                .quarantine("\u0800".repeat(48));
        int bytes = SatelliteNbtSize.uncompressedBytes(GroundSurveyCodec.encode(largest));
        assertEquals(1_406, bytes);
        assertEquals(1_310, SatelliteNbtSize.uncompressedBytes(GroundSurveyCodec.encode(largest.released()
                .quarantine("Q".repeat(48)))));
        assertEquals(bytes, CodecRecordSizer.INSTANCE.groundSurveyBytes(largest));
        assertEquals(largest, GroundSurveyCodec.decode(GroundSurveyCodec.encode(largest)));
        assertTrue(bytes <= 4_096);
        System.out.println("ARCE_GROUND_LIFECYCLE_BYTES=" + bytes);
        assertThrows(IllegalArgumentException.class, () -> new GroundSurveyJob.Device(id(200),
                ResourceLocation.tryParse("x:" + "a".repeat(127)), 0));
        assertThrows(IllegalArgumentException.class, () -> new GroundSurveyJob.Quarantine(
                "Q".repeat(49), GroundSurveyJob.Phase.PROCESSED));
    }

    @Test void lifecycleReservationCoversModifiedUtf8WithoutNarrowingTheCharacterBound() {
        for (String reason : List.of("Q".repeat(48), "\u0000".repeat(48), "\u07ff".repeat(48),
                "\u0800".repeat(48), "\ud800".repeat(48))) {
            GroundSurveyJob held = processed(0, OWNER).quarantine(reason);
            assertEquals(held, GroundSurveyCodec.decode(GroundSurveyCodec.encode(held)));
            assertTrue(SatelliteNbtSize.uncompressedBytes(GroundSurveyCodec.encode(held))
                    <= CodecRecordSizer.INSTANCE.groundSurveyBytes(held));
        }
    }

    static Stream<String> corruptions() {
        return Stream.of("unknown_phase", "future_job", "extra", "extra_analysis", "partial_claim",
                "active_full", "active_completed", "active_running", "cancelled_full", "cancelled_missing_resolved",
                "processed_waiting", "ready_credited", "running_full", "waiting_progress", "quarantine_previous",
                "quarantine_missing", "negative_time", "oversized_reason", "ticks_low", "ticks_high",
                "energy_low", "energy_high", "credit_low", "credit_high", "duplicate_job",
                "future_instance", "unknown_origin", "instance_extra", "instance_legacy");
    }

    @ParameterizedTest @MethodSource("corruptions")
    void malformedCombinationsBlockAndKeepTheWholeRoot(String kind) {
        GroundSurveyJob active = active(0, OWNER);
        CompoundTag root = root(List.of(active), List.of(instance(active, InstanceState.PENDING)));
        CompoundTag tag = root.getList("ground_surveys", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag analysis = tag.getCompound("analysis");
        CompoundTag instance = root.getList("instances", Tag.TAG_COMPOUND).getCompound(0);
        switch (kind) {
            case "unknown_phase" -> tag.putString("phase", "unknown");
            case "future_job" -> tag.putInt("schema_version", 2);
            case "extra" -> tag.putInt("extra", 1);
            case "extra_analysis" -> analysis.putInt("extra", 1);
            case "partial_claim" -> analysis.putUUID("processor", id(300));
            case "active_full" -> tag.putInt("survey_progress", 20);
            case "active_completed" -> tag.putLong("completed_at", 10);
            case "active_running" -> tag.put("analysis", GroundSurveyCodec.encode(running(0, OWNER, device(300))).get("analysis"));
            case "cancelled_full" -> { tag.putString("phase", "cancelled"); tag.putInt("survey_progress", 20); tag.putLong("resolved_at", 1); }
            case "cancelled_missing_resolved" -> tag.putString("phase", "cancelled");
            case "processed_waiting" -> { tag.putString("phase", "processed"); tag.putLong("completed_at", 1); tag.putLong("resolved_at", 2); tag.putInt("survey_progress", 20); }
            case "ready_credited" -> { tag.merge(GroundSurveyCodec.encode(processed(0, OWNER))); tag.putString("phase", "ready"); tag.remove("resolved_at"); }
            case "running_full" -> { tag.merge(GroundSurveyCodec.encode(running(0, OWNER, device(300)))); tag.getCompound("analysis").putInt("progress", 2); }
            case "waiting_progress" -> analysis.putInt("progress", 1);
            case "quarantine_previous" -> { tag.merge(GroundSurveyCodec.encode(active.quarantine("Q"))); tag.getCompound("quarantine").putString("previous_phase", "quarantined"); }
            case "quarantine_missing" -> tag.putString("phase", "quarantined");
            case "negative_time" -> tag.putLong("created_at", -1);
            case "oversized_reason" -> { tag.merge(GroundSurveyCodec.encode(active.quarantine("Q"))); tag.getCompound("quarantine").putString("reason", "Q".repeat(49)); }
            case "ticks_low" -> tag.putInt("survey_ticks", 19);
            case "ticks_high" -> tag.putInt("survey_ticks", 72_001);
            case "energy_low" -> analysis.putInt("energy", 0);
            case "energy_high" -> analysis.putInt("energy", 10_001);
            case "credit_low" -> analysis.putInt("credit", 0);
            case "credit_high" -> analysis.putInt("credit", 10_001);
            case "duplicate_job" -> root.getList("ground_surveys", Tag.TAG_COMPOUND).add(tag.copy());
            case "future_instance" -> instance.putInt("schema_version", 3);
            case "unknown_origin" -> instance.putString("origin_kind", "unknown");
            case "instance_extra" -> instance.putInt("extra", 1);
            case "instance_legacy" -> instance.putInt("schema_version", 1);
            default -> throw new AssertionError(kind);
        }
        SatelliteMissionSavedData loaded = SatelliteMissionSavedData.load(root);
        assertFalse(loaded.operational(), kind);
        assertEquals(root, loaded.preservedBlockedData().orElseThrow());
        assertEquals(root, loaded.save(new CompoundTag()));
        assertEquals(List.of(), loaded.groundSurveys());
    }
}

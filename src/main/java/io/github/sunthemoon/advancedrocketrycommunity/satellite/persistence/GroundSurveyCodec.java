package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteNbtCodec.*;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/** Exact-key codec for ground job schema 1, including the all-or-none claim. */
public final class GroundSurveyCodec {
    private static final Set<String> JOB_KEYS = Set.of("schema_version", "job_id", "owner_id", "instance_id",
            "source_device", "source_level", "source_pos", "system", "asteroid_type", "table_version",
            "candidate_fingerprint", "yield_digest", "seed", "survey_ticks", "survey_energy", "survey_progress",
            "created_at", "start_epoch", "phase", "completed_at", "resolved_at", "analysis", "quarantine");
    private static final Set<String> ANALYSIS_KEYS = Set.of("state", "ticks", "energy", "credit", "progress",
            "claim_epoch", "processor", "processor_level", "processor_pos");
    private static final Set<String> QUARANTINE_KEYS = Set.of("reason", "previous_phase");

    private GroundSurveyCodec() { }

    public static CompoundTag encode(GroundSurveyJob job) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", job.schemaVersion());
        tag.putUUID("job_id", job.jobId());
        tag.putUUID("owner_id", job.ownerId());
        tag.putUUID("instance_id", job.instanceId());
        tag.putUUID("source_device", job.source().id());
        tag.putString("source_level", job.source().level().toString());
        tag.putLong("source_pos", job.source().pos());
        tag.putString("system", job.snapshot().system().toString());
        tag.putString("asteroid_type", job.snapshot().asteroidType().toString());
        tag.putString("table_version", job.snapshot().tableVersion());
        tag.putString("candidate_fingerprint", job.snapshot().candidateFingerprint());
        tag.putString("yield_digest", job.snapshot().yieldDigest());
        tag.putLong("seed", job.snapshot().seed());
        tag.putInt("survey_ticks", job.surveyTicks());
        tag.putInt("survey_energy", job.surveyEnergy());
        tag.putInt("survey_progress", job.surveyProgress());
        tag.putLong("created_at", job.createdAt());
        tag.putLong("start_epoch", job.startEpoch());
        tag.putString("phase", lower(job.phase()));
        job.completedAt().ifPresent(value -> tag.putLong("completed_at", value));
        job.resolvedAt().ifPresent(value -> tag.putLong("resolved_at", value));
        Analysis analysis = job.analysis();
        CompoundTag nested = new CompoundTag();
        nested.putString("state", lower(analysis.state()));
        nested.putInt("ticks", analysis.ticks());
        nested.putInt("energy", analysis.energy());
        nested.putInt("credit", analysis.credit());
        nested.putInt("progress", analysis.progress());
        analysis.claim().ifPresent(claim -> {
            nested.putLong("claim_epoch", claim.epoch());
            nested.putUUID("processor", claim.processor().id());
            nested.putString("processor_level", claim.processor().level().toString());
            nested.putLong("processor_pos", claim.processor().pos());
        });
        tag.put("analysis", nested);
        job.quarantine().ifPresent(held -> {
            CompoundTag raw = new CompoundTag();
            raw.putString("reason", held.reason());
            raw.putString("previous_phase", lower(held.previousPhase()));
            tag.put("quarantine", raw);
        });
        SatelliteCanonicalBoundsException.check(SatelliteCanonicalBoundsException.Kind.GROUND_SURVEY,
                job.jobId(), tag, SatelliteLimits.MAX_GROUND_SURVEY_RECORD_NBT_BYTES);
        return tag;
    }

    public static GroundSurveyJob decode(CompoundTag tag) {
        requireBound(tag, SatelliteLimits.MAX_GROUND_SURVEY_RECORD_NBT_BYTES);
        requireKeys(tag, JOB_KEYS);
        CompoundTag nested = requireCompound(tag, "analysis");
        requireKeys(nested, ANALYSIS_KEYS);
        boolean hasClaim = nested.contains("claim_epoch") || nested.contains("processor")
                || nested.contains("processor_level") || nested.contains("processor_pos");
        Optional<Claim> claim = hasClaim ? Optional.of(new Claim(requireLong(nested, "claim_epoch"),
                new Device(requireUuid(nested, "processor"), requireLocation(nested, "processor_level"),
                        requireLong(nested, "processor_pos")))) : Optional.empty();
        Analysis analysis = new Analysis(requireEnum(nested, "state", AnalysisState.class),
                requireInt(nested, "ticks"), requireInt(nested, "energy"), requireInt(nested, "credit"),
                requireInt(nested, "progress"), claim);
        Optional<Quarantine> quarantine = Optional.empty();
        if (tag.contains("quarantine")) {
            CompoundTag raw = requireCompound(tag, "quarantine");
            requireKeys(raw, QUARANTINE_KEYS);
            quarantine = Optional.of(new Quarantine(requireBoundedString(raw, "reason", 48),
                    requireEnum(raw, "previous_phase", Phase.class)));
        }
        return new GroundSurveyJob(requireSchema(tag, SatelliteLimits.GROUND_SURVEY_SCHEMA_VERSION, "ground job"),
                requireUuid(tag, "job_id"), requireUuid(tag, "owner_id"), requireUuid(tag, "instance_id"),
                new Device(requireUuid(tag, "source_device"), requireLocation(tag, "source_level"),
                        requireLong(tag, "source_pos")),
                new Snapshot(requireLocation(tag, "system"), requireLocation(tag, "asteroid_type"),
                        requireBoundedString(tag, "table_version", 16),
                        requireBoundedString(tag, "candidate_fingerprint", 16), requireLong(tag, "seed"),
                        requireBoundedString(tag, "yield_digest", 16)),
                requireInt(tag, "survey_ticks"), requireInt(tag, "survey_energy"), requireInt(tag, "survey_progress"),
                requireLong(tag, "created_at"), requireLong(tag, "start_epoch"), requireEnum(tag, "phase", Phase.class),
                optionalLong(tag, "completed_at"), optionalLong(tag, "resolved_at"), analysis, quarantine);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Immutable registry-only job. Its effective phase freezes the progress/field matrix. */
public record GroundSurveyJob(int schemaVersion, UUID jobId, UUID ownerId, UUID instanceId,
        Device source, Snapshot snapshot, int surveyTicks, int surveyEnergy, int surveyProgress,
        long createdAt, long startEpoch, Phase phase, OptionalLong completedAt, OptionalLong resolvedAt,
        Analysis analysis, Optional<Quarantine> quarantine) {
    public enum Phase { ACTIVE, READY, PROCESSED, CANCELLED, QUARANTINED }
    public enum AnalysisState { WAITING, RUNNING, CREDITED }

    public record Device(UUID id, ResourceLocation level, long pos) {
        public Device {
            Objects.requireNonNull(id, "id");
            identifier(level);
        }
    }

    public record Snapshot(ResourceLocation system, ResourceLocation asteroidType, String tableVersion,
            String candidateFingerprint, long seed, String yieldDigest) {
        public Snapshot {
            identifier(system);
            identifier(asteroidType);
            hex(tableVersion);
            hex(candidateFingerprint);
            hex(yieldDigest);
        }

        public static Snapshot from(AsteroidInstance instance) {
            return new Snapshot(instance.system(), instance.asteroidType(), instance.tableVersion(),
                    instance.candidateFingerprint(), instance.seed(), digest(instance.yield()));
        }

        public boolean matches(AsteroidInstance instance) {
            return equals(from(instance));
        }
    }

    public record Claim(long epoch, Device processor) {
        public Claim {
            if (epoch < 1) {
                throw new IllegalArgumentException("Claim epoch starts at 1");
            }
            Objects.requireNonNull(processor, "processor");
        }
    }

    public record Analysis(AnalysisState state, int ticks, int energy, int credit, int progress,
            Optional<Claim> claim) {
        public Analysis {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(claim, "claim");
            bounded(ticks, 1, 72_000, "analysis ticks");
            bounded(energy, 1, 10_000, "analysis energy");
            bounded(credit, 1, 10_000, "analysis credit");
            bounded(progress, 0, ticks, "analysis progress");
            boolean valid = switch (state) {
                case WAITING -> progress == 0 && claim.isEmpty();
                case RUNNING -> progress < ticks && claim.isPresent();
                case CREDITED -> progress == ticks && claim.isPresent();
            };
            if (!valid) {
                throw new IllegalArgumentException("Inconsistent analysis state, progress or claim");
            }
        }

        public Analysis with(AnalysisState next, int nextProgress, Optional<Claim> nextClaim) {
            return new Analysis(next, ticks, energy, credit, nextProgress, nextClaim);
        }
    }

    public record Quarantine(String reason, Phase previousPhase) {
        public Quarantine {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(previousPhase, "previousPhase");
            if (reason.isEmpty() || reason.length() > 48 || previousPhase == Phase.QUARANTINED) {
                throw new IllegalArgumentException("Invalid ground quarantine reason or previous phase");
            }
        }
    }

    public GroundSurveyJob {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(resolvedAt, "resolvedAt");
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(quarantine, "quarantine");
        if (schemaVersion != SatelliteLimits.GROUND_SURVEY_SCHEMA_VERSION || createdAt < 0 || startEpoch < 1) {
            throw new IllegalArgumentException("Invalid ground survey schema, time or epoch");
        }
        bounded(surveyTicks, 20, 72_000, "survey ticks");
        bounded(surveyEnergy, 1, 10_000, "survey energy");
        bounded(surveyProgress, 0, surveyTicks, "survey progress");
        if ((phase == Phase.QUARANTINED) != quarantine.isPresent()) {
            throw new IllegalArgumentException("Only quarantined jobs carry quarantine fields");
        }
        Phase effective = quarantine.map(Quarantine::previousPhase).orElse(phase);
        boolean valid = switch (effective) {
            case ACTIVE -> surveyProgress < surveyTicks && completedAt.isEmpty() && resolvedAt.isEmpty()
                    && analysis.state() == AnalysisState.WAITING;
            case READY -> surveyProgress == surveyTicks && completedAt.isPresent() && resolvedAt.isEmpty()
                    && analysis.state() != AnalysisState.CREDITED;
            case PROCESSED -> surveyProgress == surveyTicks && completedAt.isPresent() && resolvedAt.isPresent()
                    && analysis.state() == AnalysisState.CREDITED;
            case CANCELLED -> surveyProgress < surveyTicks && completedAt.isEmpty() && resolvedAt.isPresent()
                    && analysis.state() == AnalysisState.WAITING;
            case QUARANTINED -> false;
        };
        if (!valid || completedAt.isPresent() && completedAt.getAsLong() < createdAt
                || resolvedAt.isPresent() && resolvedAt.getAsLong() < completedAt.orElse(createdAt)) {
            throw new IllegalArgumentException("Inconsistent ground survey phase, progress or timestamps");
        }
    }

    public Phase effectivePhase() {
        return quarantine.map(Quarantine::previousPhase).orElse(phase);
    }

    /** Re-quarantine keeps the first reason, previous phase, claim and credited marker unchanged. */
    public GroundSurveyJob quarantine(String reason) {
        return phase == Phase.QUARANTINED ? this : copy(source, Phase.QUARANTINED, surveyProgress,
                completedAt, resolvedAt, analysis, Optional.of(new Quarantine(reason, phase)));
    }

    public GroundSurveyJob released() {
        return copy(source, effectivePhase(), surveyProgress, completedAt, resolvedAt, analysis, Optional.empty());
    }

    public GroundSurveyJob rebound(Device next) {
        return copy(next, phase, surveyProgress, completedAt, resolvedAt, analysis, quarantine);
    }

    public GroundSurveyJob advanced(long now) {
        int progress = Math.addExact(surveyProgress, 1);
        return copy(source, progress == surveyTicks ? Phase.READY : Phase.ACTIVE, progress,
                progress == surveyTicks ? OptionalLong.of(now) : OptionalLong.empty(), OptionalLong.empty(),
                analysis, Optional.empty());
    }

    public GroundSurveyJob cancelled(long now) {
        return copy(source, Phase.CANCELLED, surveyProgress, OptionalLong.empty(), OptionalLong.of(now),
                analysis, Optional.empty());
    }

    public GroundSurveyJob analyzed(Analysis next, long now) {
        boolean credited = next.state() == AnalysisState.CREDITED;
        return copy(source, credited ? Phase.PROCESSED : Phase.READY, surveyProgress, completedAt,
                credited ? OptionalLong.of(now) : OptionalLong.empty(), next, Optional.empty());
    }

    private GroundSurveyJob copy(Device device, Phase next, int progress, OptionalLong completed,
            OptionalLong resolved, Analysis nextAnalysis, Optional<Quarantine> held) {
        return new GroundSurveyJob(schemaVersion, jobId, ownerId, instanceId, device, snapshot, surveyTicks,
                surveyEnergy, progress, createdAt, startEpoch, next, completed, resolved, nextAnalysis, held);
    }

    public static String digest(List<RewardEntry> entries) {
        StringBuilder lines = new StringBuilder();
        entries.forEach(entry -> lines.append(entry.item()).append('\t').append(entry.count()).append('\n'));
        return ResourceAlgorithms.sha256Hex16(lines.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void hex(String value) {
        if (value == null || !MissionPayload.HEX16.matcher(value).matches()) {
            throw new IllegalArgumentException("Ground survey digest must be 16 hex characters");
        }
    }

    private static void identifier(ResourceLocation value) {
        if (value == null || value.toString().length() > 128) {
            throw new IllegalArgumentException("Ground survey identifier exceeds its bound");
        }
    }

    private static void bounded(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException("Ground " + name + " exceeds its bound");
        }
    }
}

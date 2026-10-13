package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bidirectional origin checks, calculated before the existing mission/allocation plan. */
final class GroundSurveyInvariants {
    record Plan(Map<UUID, String> jobs, Set<UUID> instances) { }

    private GroundSurveyInvariants() { }

    static Plan plan(Map<UUID, GroundSurveyJob> jobs, Map<UUID, AsteroidInstance> instances) {
        Map<UUID, String> heldJobs = new LinkedHashMap<>();
        Set<UUID> heldInstances = new LinkedHashSet<>();
        Map<UUID, Set<UUID>> byInstance = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> byDevice = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> byProcessor = new LinkedHashMap<>();
        for (GroundSurveyJob job : jobs.values()) {
            index(byInstance, job.instanceId(), job.jobId());
            if (job.effectivePhase() == GroundSurveyJob.Phase.ACTIVE) {
                index(byDevice, job.source().id(), job.jobId());
            }
            if (job.analysis().state() == GroundSurveyJob.AnalysisState.RUNNING) {
                index(byProcessor, job.analysis().claim().orElseThrow().processor().id(), job.jobId());
            }
        }
        duplicates(byInstance, jobs, instances, heldJobs, heldInstances, "DUPLICATE_INSTANCE", true);
        duplicates(byDevice, jobs, instances, heldJobs, heldInstances, "DUPLICATE_SOURCE", true);
        duplicates(byProcessor, jobs, instances, heldJobs, heldInstances, "DUPLICATE_PROCESSOR", false);
        for (GroundSurveyJob job : jobs.values()) {
            if (job.phase() == GroundSurveyJob.Phase.QUARANTINED) {
                continue;
            }
            AsteroidInstance instance = instances.get(job.instanceId());
            String problem = jobProblem(job, instance);
            if (problem != null) {
                heldJobs.putIfAbsent(job.jobId(), problem);
                if (instance != null && instance.state() != InstanceState.QUARANTINED) {
                    heldInstances.add(instance.instanceId());
                }
            }
        }
        for (AsteroidInstance instance : instances.values()) {
            if (instance.origin().kind() != InstanceOrigin.Kind.GROUND_SURVEY
                    || instance.state() == InstanceState.QUARANTINED) {
                continue;
            }
            GroundSurveyJob job = jobs.get(instance.origin().id());
            String problem = instanceProblem(instance, job);
            if (problem != null) {
                heldInstances.add(instance.instanceId());
                // A cancelled tombstone is not changed; only the wrongly retained instance is held.
                if (job != null && job.phase() != GroundSurveyJob.Phase.QUARANTINED
                        && job.phase() != GroundSurveyJob.Phase.CANCELLED) {
                    heldJobs.putIfAbsent(job.jobId(), problem);
                }
            }
        }
        return new Plan(heldJobs, heldInstances);
    }

    static String jobProblem(GroundSurveyJob job, AsteroidInstance instance) {
        if (job.phase() == GroundSurveyJob.Phase.CANCELLED) {
            return null;
        }
        if (instance == null) {
            return job.phase() == GroundSurveyJob.Phase.ACTIVE ? "MISSING_PENDING_INSTANCE" : null;
        }
        if (!matches(job, instance)) {
            return "GROUND_SNAPSHOT_MISMATCH";
        }
        if (job.phase() == GroundSurveyJob.Phase.ACTIVE) {
            return instance.state() == InstanceState.PENDING ? null : "GROUND_PHASE_MISMATCH";
        }
        return instance.state() == InstanceState.PENDING ? "GROUND_PHASE_MISMATCH" : null;
    }

    static String instanceProblem(AsteroidInstance instance, GroundSurveyJob job) {
        if (job == null) {
            return "MISSING_GROUND_JOB";
        }
        if (!matches(job, instance)) {
            return "GROUND_SNAPSHOT_MISMATCH";
        }
        if (instance.state() == InstanceState.PENDING) {
            return job.phase() == GroundSurveyJob.Phase.ACTIVE ? null : "GROUND_PHASE_MISMATCH";
        }
        GroundSurveyJob.Phase phase = job.effectivePhase();
        return phase == GroundSurveyJob.Phase.READY || phase == GroundSurveyJob.Phase.PROCESSED
                ? null : "GROUND_PHASE_MISMATCH";
    }

    static boolean matches(GroundSurveyJob job, AsteroidInstance instance) {
        return instance.origin().equals(InstanceOrigin.ground(job.jobId()))
                && instance.instanceId().equals(job.instanceId()) && instance.ownerId().equals(job.ownerId())
                && job.snapshot().matches(instance);
    }

    private static void index(Map<UUID, Set<UUID>> index, UUID key, UUID id) {
        index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(id);
    }

    private static void duplicates(Map<UUID, Set<UUID>> index, Map<UUID, GroundSurveyJob> jobs,
            Map<UUID, AsteroidInstance> instances, Map<UUID, String> heldJobs, Set<UUID> heldInstances,
            String reason, boolean holdInstances) {
        for (Set<UUID> ids : index.values()) {
            if (ids.size() < 2) {
                continue;
            }
            for (UUID id : ids) {
                GroundSurveyJob job = jobs.get(id);
                if (job.phase() != GroundSurveyJob.Phase.QUARANTINED) {
                    heldJobs.putIfAbsent(id, reason);
                }
                AsteroidInstance instance = instances.get(job.instanceId());
                if (holdInstances && instance != null && instance.state() != InstanceState.QUARANTINED) {
                    heldInstances.add(instance.instanceId());
                }
            }
        }
    }
}

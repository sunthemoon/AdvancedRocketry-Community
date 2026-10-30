package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Analysis;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.AnalysisState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Claim;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Device;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Phase;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Registry-only checked transactions. Runtime/FE/loaded-world validation belongs to W21c. */
public final class GroundSurveys {
    public enum Code {
        SUCCESS, IDEMPOTENT, JOB_NOT_FOUND, UNAUTHORIZED, IDENTITY_CONFLICT, DEVICE_BUSY, PROCESSOR_BUSY,
        OWNER_LIMIT, CAPACITY_REACHED, STORAGE_BUDGET, WAITING_SAVE, NOT_READY, ALREADY_CLAIMED, CANCELLED,
        QUARANTINED, SOURCE_MISMATCH, ACCOUNT_FULL, OFFLINE_REPAIR_REQUIRED, INVARIANT_FAILED
    }
    public record Result(Code code, boolean changed, Optional<GroundSurveyJob> job, String reason) { }
    public record Start(UUID jobId, UUID ownerId, Device source, AsteroidInstance instance,
            int surveyTicks, int surveyEnergy, int analysisTicks, int analysisEnergy, int credit) { }

    private final SatelliteMissionRegistry registry;
    private final Map<UUID, GroundSurveyJob> jobs = new LinkedHashMap<>();
    private final GroundSurveyLedger ledger = new GroundSurveyLedger();
    private long version;

    GroundSurveys(SatelliteMissionRegistry registry) { this.registry = registry; }
    /** Read-only start timestamp preview for the loaded-world ground research adapter. */
    public long previewTime(long observedTime) {
        synchronized (registry) { return registry.clock.preview(observedTime); }
    }
    /** Operator/menu inspection must not attempt a release just to learn its recovery boundary. */
    public String recoveryHint(UUID id) {
        synchronized (registry) {
            var job = jobs.get(id); return job == null ? "Job not found" : offlineRepair(job);
        }
    }

    /** Counts every change to the job records, so a derived view rebuilds only when this section changed. */
    public long version() {
        synchronized (registry) { return version; }
    }

    public List<GroundSurveyJob> all() {
        synchronized (registry) {
            return jobs.keySet().stream().sorted(GroundSurveyLedger.IDS).map(jobs::get).toList();
        }
    }

    public Optional<GroundSurveyJob> job(UUID id) {
        synchronized (registry) { return Optional.ofNullable(jobs.get(id)); }
    }

    public List<GroundSurveyJob> owned(UUID owner) {
        synchronized (registry) {
            return ledger.ownedIds(owner).stream().sorted(GroundSurveyLedger.IDS).map(jobs::get).toList();
        }
    }

    public void restore(GroundSurveyJob job) {
        synchronized (registry) {
            if (jobs.size() >= SatelliteLimits.MAX_GROUND_SURVEYS || jobs.containsKey(job.jobId())) {
                throw new IllegalArgumentException("Ground job count or duplicate ID");
            }
            put(job);
            registry.budget.reserve(StorageBudget.Section.GROUND_SURVEYS, job.jobId(),
                    registry.sizer.groundSurveyReservation(job));
        }
    }

    Map<UUID, GroundSurveyJob> records() { return java.util.Collections.unmodifiableMap(jobs); }

    void put(GroundSurveyJob job) {
        ledger.changed(registry.encodings.changed(jobs.put(job.jobId(), job), job), job, !hasInstanceReference(job));
        version++;
    }

    void instanceRemoved(AsteroidInstance instance) {
        java.util.Set<UUID> affected = new java.util.HashSet<>(ledger.forInstance(instance.instanceId()));
        if (instance.origin().kind() == InstanceOrigin.Kind.GROUND_SURVEY) { affected.add(instance.origin().id()); }
        for (UUID jobId : affected) {
            GroundSurveyJob job = jobs.get(jobId);
            if (job != null) { ledger.changed(job, job, !hasInstanceReference(job)); }
        }
    }

    private boolean hasInstanceReference(GroundSurveyJob job) {
        return registry.instanceRecord(job.instanceId()) != null
                || !registry.ledger().groundOrigins(job.jobId()).isEmpty();
    }

    public Result start(Start start, long observedTime) {
        synchronized (registry) {
            GroundSurveyJob existing = jobs.get(start.jobId());
            if (existing != null) {
                return result(Code.IDENTITY_CONFLICT, false, existing);
            }
            if (ledger.deviceBusy(start.source().id(), null)) { return result(Code.DEVICE_BUSY, false, null); }
            if (registry.ledger().live(start.ownerId()) >= registry.limits().instancesPerOwner()
                    || ledger.owned(start.ownerId()) >= SatelliteLimits.MAX_GROUND_SURVEYS_PER_OWNER) {
                return result(Code.OWNER_LIMIT, false, null);
            }
            if (jobs.size() >= SatelliteLimits.MAX_GROUND_SURVEYS
                    || registry.instanceCount() >= registry.limits().instancesGlobal()
                    || !registry.accounts.containsKey(start.ownerId())
                    && registry.accounts.size() >= SatelliteLimits.MAX_RESEARCH_ACCOUNTS) {
                return result(Code.CAPACITY_REACHED, false, null);
            }
            AsteroidInstance instance = Objects.requireNonNull(start.instance(), "instance");
            long now = registry.clock.preview(observedTime);
            if (instance.state() != InstanceState.PENDING || !instance.ownerId().equals(start.ownerId())
                    || !instance.origin().equals(InstanceOrigin.ground(start.jobId())) || instance.createdAt() != now
                    || registry.instanceRecord(instance.instanceId()) != null) {
                return result(Code.IDENTITY_CONFLICT, false, null);
            }
            GroundSurveyJob job = new GroundSurveyJob(SatelliteLimits.GROUND_SURVEY_SCHEMA_VERSION, start.jobId(),
                    start.ownerId(), instance.instanceId(), start.source(), GroundSurveyJob.Snapshot.from(instance),
                    start.surveyTicks(), start.surveyEnergy(), 0, now, registry.stampEpoch(), Phase.ACTIVE,
                    OptionalLong.empty(), OptionalLong.empty(), new Analysis(AnalysisState.WAITING,
                    start.analysisTicks(), start.analysisEnergy(), start.credit(), 0, Optional.empty()), Optional.empty());
            ResearchAccount account = registry.account(start.ownerId());
            var jobReservation = registry.sizer.groundSurveyReservation(job);
            var instanceReservation = registry.sizer.instanceReservation(instance);
            int jobBytes = jobReservation.bytes();
            int instanceBytes = instanceReservation.bytes();
            if (jobBytes < 0 || jobBytes > SatelliteLimits.MAX_GROUND_SURVEY_RECORD_NBT_BYTES
                    || instanceBytes < 0 || instanceBytes > SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES
                    || !registry.budget.fits(StorageBudget.Section.GROUND_SURVEYS, jobBytes)
                    || !registry.budget.fits(StorageBudget.Section.INSTANCES, instanceBytes)
                    || !registry.accounts.containsKey(start.ownerId())
                    && !registry.budget.fits(StorageBudget.Section.ACCOUNTS, RecordSizer.ACCOUNT_BYTES)) {
                return result(Code.STORAGE_BUDGET, false, null);
            }
            Result check = preflight(job, instance, false, account, now, observedTime);
            if (check != null) { return check; }
            registry.budget.reserve(StorageBudget.Section.GROUND_SURVEYS, job.jobId(), jobReservation);
            registry.budget.reserve(StorageBudget.Section.INSTANCES, instance.instanceId(), instanceReservation);
            publish(job, instance, false, account, observedTime);
            return result(Code.SUCCESS, true, job);
        }
    }

    /** The last advance publishes READY and AVAILABLE together, never ACTIVE at full progress. */
    public Result advanceSurvey(UUID id, Device source, long ttl, long observedTime) {
        synchronized (registry) {
            GroundSurveyJob job = jobs.get(id);
            Result refused = active(job, source);
            if (refused != null) { return refused; }
            if (registry.saveEpoch() <= job.startEpoch()) { return result(Code.WAITING_SAVE, false, job); }
            if (ttl < 24_000 || ttl > 1_728_000) { throw new IllegalArgumentException("Instance TTL bound"); }
            long now = registry.clock.preview(observedTime);
            GroundSurveyJob next = job.advanced(now);
            AsteroidInstance instance = registry.instanceRecord(job.instanceId());
            if (instance == null) { return result(Code.INVARIANT_FAILED, false, job); }
            try {
                if (next.phase() == Phase.READY) {
                    instance = instance.transition(InstanceState.AVAILABLE, OptionalLong.of(Math.addExact(now, ttl)),
                            Optional.empty());
                }
            } catch (ArithmeticException exception) { return result(Code.CAPACITY_REACHED, false, job); }
            Result check = preflight(next, instance, false, null, now, observedTime);
            if (check != null) { return check; }
            publish(next, instance, false, null, observedTime);
            return result(Code.SUCCESS, true, next);
        }
    }

    public Result cancel(UUID id, UUID requester, boolean operator, long observedTime) {
        synchronized (registry) {
            GroundSurveyJob job = jobs.get(id);
            Result refused = authorized(job, requester, operator);
            if (refused != null) { return refused; }
            if (job.phase() == Phase.CANCELLED) { return result(Code.CANCELLED, false, job); }
            if (job.phase() != Phase.ACTIVE) { return result(job.phase() == Phase.QUARANTINED
                    ? Code.QUARANTINED : Code.NOT_READY, false, job); }
            long now = registry.clock.preview(observedTime);
            AsteroidInstance instance = registry.instanceRecord(job.instanceId());
            GroundSurveyJob next = job.cancelled(now);
            Result check = preflight(next, instance, true, null, now, observedTime);
            if (check != null) { return check; }
            publish(next, instance, true, null, observedTime);
            return result(Code.SUCCESS, true, next);
        }
    }

    public Result rebindSurvey(UUID id, UUID requester, Device target, long observedTime) {
        synchronized (registry) {
            GroundSurveyJob job = jobs.get(id);
            Result refused = authorized(job, requester, false);
            if (refused != null) { return refused; }
            if (job.phase() != Phase.ACTIVE) { return result(Code.NOT_READY, false, job); }
            if (job.source().equals(target)) { return result(Code.IDEMPOTENT, false, job); }
            if (ledger.deviceBusy(target.id(), id)) { return result(Code.DEVICE_BUSY, false, job); }
            return mutate(job.rebound(target), observedTime);
        }
    }

    public Result claim(UUID id, UUID requester, Device processor, long observedTime) {
        synchronized (registry) {
            GroundSurveyJob job = jobs.get(id);
            Result refused = authorized(job, requester, false);
            if (refused != null) { return refused; }
            if (job.phase() == Phase.PROCESSED || job.analysis().state() != AnalysisState.WAITING) {
                return result(Code.ALREADY_CLAIMED, false, job);
            }
            if (job.phase() != Phase.READY) { return result(Code.NOT_READY, false, job); }
            if (ledger.processorBusy(processor.id(), id)) { return result(Code.PROCESSOR_BUSY, false, job); }
            Analysis next = job.analysis().with(AnalysisState.RUNNING, 0,
                    Optional.of(new Claim(registry.stampEpoch(), processor)));
            return mutate(job.analyzed(next, registry.clock.preview(observedTime)), observedTime);
        }
    }

    public Result rebindAnalysis(UUID id, UUID requester, Device target, long observedTime) {
        synchronized (registry) {
            GroundSurveyJob job = jobs.get(id);
            Result refused = authorized(job, requester, false);
            if (refused != null) { return refused; }
            if (job.phase() != Phase.READY || job.analysis().state() != AnalysisState.RUNNING) {
                return result(Code.NOT_READY, false, job);
            }
            if (job.analysis().claim().orElseThrow().processor().equals(target)) {
                return result(Code.IDEMPOTENT, false, job);
            }
            if (ledger.processorBusy(target.id(), id)) { return result(Code.PROCESSOR_BUSY, false, job); }
            Analysis next = job.analysis().with(AnalysisState.RUNNING, job.analysis().progress(),
                    Optional.of(new Claim(registry.stampEpoch(), target)));
            return mutate(job.analyzed(next, registry.clock.preview(observedTime)), observedTime);
        }
    }

    /** The last paid analysis advance and the whole account credit are a single mutation. */
    public Result advanceAnalysis(UUID id, Device processor, long observedTime) {
        synchronized (registry) {
            GroundSurveyJob job = jobs.get(id);
            if (job == null) { return result(Code.JOB_NOT_FOUND, false, null); }
            if (job.phase() == Phase.QUARANTINED) { return result(Code.QUARANTINED, false, job); }
            if (job.phase() == Phase.PROCESSED) { return result(Code.ALREADY_CLAIMED, false, job); }
            if (job.phase() != Phase.READY || job.analysis().state() != AnalysisState.RUNNING) {
                return result(Code.NOT_READY, false, job);
            }
            Claim claim = job.analysis().claim().orElseThrow();
            if (!claim.processor().equals(processor)) { return result(Code.SOURCE_MISMATCH, false, job); }
            if (registry.saveEpoch() <= claim.epoch()) { return result(Code.WAITING_SAVE, false, job); }
            long now = registry.clock.preview(observedTime);
            int progress = job.analysis().progress() + 1;
            boolean last = progress == job.analysis().ticks();
            ResearchAccount account = null;
            if (last) {
                try { account = registry.account(job.ownerId()).creditAndSpend(job.analysis().credit(), 0); }
                catch (IllegalStateException | ArithmeticException exception) {
                    return result(Code.ACCOUNT_FULL, false, job);
                }
            }
            GroundSurveyJob next = job.analyzed(job.analysis().with(last ? AnalysisState.CREDITED
                    : AnalysisState.RUNNING, progress, job.analysis().claim()), now);
            Result check = preflight(next, null, false, account, now, observedTime);
            if (check != null) { return check; }
            publish(next, null, false, account, observedTime);
            return result(Code.SUCCESS, true, next);
        }
    }

    public Result release(UUID id) {
        synchronized (registry) {
            GroundSurveyJob job = jobs.get(id);
            if (job == null) { return result(Code.JOB_NOT_FOUND, false, null); }
            if (job.phase() != Phase.QUARANTINED) { return result(Code.IDEMPOTENT, false, job); }
            String offline = offlineRepair(job);
            if (offline != null) { return new Result(Code.OFFLINE_REPAIR_REQUIRED, false, Optional.of(job), offline); }
            return mutate(job.released(), registry.lastObservedGameTime());
        }
    }

    /** Shared with instance release; no online path promotes a held PENDING pair into availability. */
    String instanceReleaseProblem(AsteroidInstance instance) {
        if (instance.origin().kind() != InstanceOrigin.Kind.GROUND_SURVEY) { return null; }
        GroundSurveyJob job = jobs.get(instance.origin().id());
        if (job == null) { return "Offline repair required: restore the ground job and matching instance backlink"; }
        String offline = offlineRepair(job);
        if (offline != null) { return offline; }
        if (job.phase() != Phase.READY && job.phase() != Phase.PROCESSED
                || !GroundSurveyInvariants.matches(job, instance)) {
            return "Offline repair required: the matching ground job must be READY or PROCESSED";
        }
        return null;
    }

    public String instanceReleaseProblem(UUID instanceId) {
        synchronized (registry) {
            AsteroidInstance instance = registry.instanceRecord(instanceId);
            return instance == null ? null : instanceReleaseProblem(instance);
        }
    }

    private String offlineRepair(GroundSurveyJob job) {
        if (job.phase() == Phase.QUARANTINED && job.effectivePhase() == Phase.ACTIVE) {
            return "Offline repair required: restore ACTIVE job and PENDING instance with matching snapshot/backlinks";
        }
        if (job.analysis().state() == AnalysisState.RUNNING
                && ledger.processorBusy(job.analysis().claim().orElseThrow().processor().id(), job.jobId())) {
            return "Offline repair required: repair duplicate RUNNING processor identities/claims together";
        }
        return null;
    }

    /** Existing scheduler pass; no live job is removed to admit another. */
    int prune(long now) {
        if (!registry.epochs().admitsChange()) { return 0; }
        int removed = 0;
        int inspected = 0;
        while (removed < 16 && inspected < 32 && !ledger.queue.isEmpty()) {
            GroundSurveyLedger.Entry head = ledger.queue.first();
            inspected++;
            if (head.dueAt() > now) { break; }
            GroundSurveyJob job = jobs.get(head.id());
            if (job == null || job.phase() != Phase.CANCELLED && job.phase() != Phase.PROCESSED
                    || hasInstanceReference(job)) {
                ledger.unqueue(head.id());
                continue;
            }
            registry.encodings.changed(jobs.remove(job.jobId()), null);
            version++;
            ledger.changed(job, null, true);
            registry.budget.release(StorageBudget.Section.GROUND_SURVEYS, job.jobId());
            removed++;
        }
        return removed;
    }

    private Result mutate(GroundSurveyJob next, long observedTime) {
        long now = registry.clock.preview(observedTime);
        Result check = preflight(next, null, false, null, now, observedTime);
        if (check != null) { return check; }
        publish(next, null, false, null, observedTime);
        return result(Code.SUCCESS, true, next);
    }

    private Result preflight(GroundSurveyJob next, AsteroidInstance instance, boolean remove,
            ResearchAccount account, long now, long observedTime) {
        boolean newAccount = account != null && !registry.accounts.containsKey(account.ownerId());
        if (newAccount) {
            if (registry.accounts.size() >= SatelliteLimits.MAX_RESEARCH_ACCOUNTS) {
                return result(Code.CAPACITY_REACHED, false, jobs.get(next.jobId()));
            }
            if (!registry.budget.fits(StorageBudget.Section.ACCOUNTS, RecordSizer.ACCOUNT_BYTES)) {
                return result(Code.STORAGE_BUDGET, false, jobs.get(next.jobId()));
            }
        }
        var jobReservation = registry.sizer.groundSurveyReservation(next);
        var instanceReservation = instance == null || remove ? new RecordSizer.Reservation(0, true)
                : registry.sizer.instanceReservation(instance);
        int jobBytes = jobReservation.bytes();
        int instanceBytes = instanceReservation.bytes();
        if (jobBytes < 0 || jobBytes > SatelliteLimits.MAX_GROUND_SURVEY_RECORD_NBT_BYTES
                || instanceBytes < 0 || instanceBytes > SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES) {
            return result(Code.STORAGE_BUDGET, false, jobs.get(next.jobId()));
        }
        // Check both the original and candidate; a mutation cannot hide an existing broken pair.
        GroundSurveyJob current = jobs.get(next.jobId());
        AsteroidInstance after = instance == null ? registry.instanceRecord(next.instanceId()) : remove ? null : instance;
        String problem = current == null ? null : touchedProblem(current, registry.instanceRecord(current.instanceId()));
        if (problem == null) { problem = touchedProblem(next, after); }
        if (problem != null) { return new Result(Code.INVARIANT_FAILED, false, Optional.ofNullable(current), problem); }
        try {
            Math.addExact(registry.epochs().ceiling(), 1L);
            // Existing records stay inside their lifecycle reservations. START has not reserved its job or
            // instance yet; account creation likewise needs its own additional reservation in the proof.
            long addedBytes = (current == null ? (long) jobBytes + instanceBytes : 0L)
                    + (newAccount ? RecordSizer.ACCOUNT_BYTES : 0L);
            if (!(jobReservation.proven() && instanceReservation.proven() && reservationsBoundRoot(addedBytes))
                    && !registry.sizer.groundMutationFits(registry, next, instance, remove, account, now, observedTime)) {
                return result(Code.STORAGE_BUDGET, false, current);
            }
        } catch (ArithmeticException exception) { return result(Code.CAPACITY_REACHED, false, current); }
        return null;
    }

    /** A change that adds no record fits by reservation or exact canonical preflight. */
    boolean fitsWithoutNewRecord(GroundSurveyJob job, AsteroidInstance instance, long now, long observedTime) {
        return reservationsBoundRoot(0L)
                || registry.sizer.groundMutationFits(registry, job, instance, false, null, now, observedTime);
    }

    /**
     * Proven reservations cover the largest encoding their records can reach; an account encodes in at most
     * {@link RecordSizer#ACCOUNT_BYTES}. Their sum plus framing bounds the encoded root, but a restored root
     * above its section budgets can exceed the root byte limit. Failed sizing also invalidates the proof,
     * even if its capped accounting bytes fit; both cases take the canonical preflight.
     * Candidate additions are counted before publication reserves them; existing reservations are not subtracted.
     */
    private boolean reservationsBoundRoot(long addedBytes) {
        if (!registry.budget.allProven()) { return false; }
        long bound = RecordSizer.ROOT_FRAMING_BYTES + (long) registry.accounts.size() * RecordSizer.ACCOUNT_BYTES
                + addedBytes;
        for (StorageBudget.Section section : List.of(StorageBudget.Section.SATELLITES, StorageBudget.Section.MISSIONS,
                StorageBudget.Section.INSTANCES, StorageBudget.Section.GROUND_SURVEYS)) {
            bound += registry.budget.reserved(section);
        }
        return bound <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES;
    }

    /**
     * The load plan's ground rules for one job state and its instance, with device, processor and instance
     * peers taken from the derived indexes. A broken record elsewhere never refuses this job; the cause is
     * returned so the refusal names it.
     */
    private String touchedProblem(GroundSurveyJob job, AsteroidInstance instance) {
        if (job.phase() != Phase.QUARANTINED) {
            String problem = GroundSurveyInvariants.jobProblem(job, instance);
            if (problem != null) { return problem; }
        }
        if (instance != null && instance.origin().kind() == InstanceOrigin.Kind.GROUND_SURVEY
                && instance.state() != InstanceState.QUARANTINED) {
            GroundSurveyJob origin = instance.origin().id().equals(job.jobId()) ? job : jobs.get(instance.origin().id());
            String problem = GroundSurveyInvariants.instanceProblem(instance, origin);
            if (problem != null) { return problem; }
        }
        for (UUID id : registry.ledger().groundOrigins(job.jobId())) {
            if (!id.equals(job.instanceId()) && registry.instanceRecord(id).state() != InstanceState.QUARANTINED) {
                return "GROUND_SNAPSHOT_MISMATCH";
            }
        }
        if (ledger.instanceShared(job.instanceId(), job.jobId())) { return "DUPLICATE_INSTANCE"; }
        if (job.effectivePhase() == Phase.ACTIVE && ledger.deviceBusy(job.source().id(), job.jobId())) {
            return "DUPLICATE_SOURCE";
        }
        if (job.analysis().state() == AnalysisState.RUNNING
                && ledger.processorBusy(job.analysis().claim().orElseThrow().processor().id(), job.jobId())) {
            return "DUPLICATE_PROCESSOR";
        }
        return null;
    }

    private void publish(GroundSurveyJob job, AsteroidInstance instance, boolean remove,
            ResearchAccount account, long observedTime) {
        if (instance != null) {
            if (remove) { registry.dropInstance(instance.instanceId()); }
            else { registry.storeInstance(instance); }
        }
        if (account != null) {
            registry.encodings.changed(registry.accounts.put(account.ownerId(), account), account);
            registry.budget.reserveAccount(account.ownerId());
        }
        put(job);
        registry.advanceClock(observedTime);
        registry.markChanged();
    }

    private static Result authorized(GroundSurveyJob job, UUID requester, boolean operator) {
        return job == null ? result(Code.JOB_NOT_FOUND, false, null) : !operator && !job.ownerId().equals(requester)
                ? result(Code.UNAUTHORIZED, false, job) : null;
    }

    private static Result active(GroundSurveyJob job, Device source) {
        if (job == null) { return result(Code.JOB_NOT_FOUND, false, null); }
        if (job.phase() != Phase.ACTIVE) { return result(job.phase() == Phase.QUARANTINED ? Code.QUARANTINED
                : job.phase() == Phase.CANCELLED ? Code.CANCELLED : Code.NOT_READY, false, job); }
        return job.source().equals(source) ? null : result(Code.SOURCE_MISMATCH, false, job);
    }

    private static Result result(Code code, boolean changed, GroundSurveyJob job) {
        return new Result(code, changed, Optional.ofNullable(job), code.name());
    }
}

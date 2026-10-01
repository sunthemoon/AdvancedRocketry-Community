package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** In-memory authority for bounded satellite, mission, clock, and research state. */
public final class SatelliteMissionRegistry {
    private static final Comparator<UUID> UUID_ORDER = Comparator
            .comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final Map<UUID, SatelliteState> satellites = new LinkedHashMap<>();
    private final Map<UUID, MissionState> missions = new LinkedHashMap<>();
    private final Map<UUID, ResearchAccount> accounts = new LinkedHashMap<>();
    private final Map<UUID, AsteroidInstance> instances = new LinkedHashMap<>();
    /** ADR-050 §5: maintained incrementally; derived on restore, never persisted. */
    private final Map<UUID, Integer> satellitesByOwner = new java.util.HashMap<>();
    /** ADR-049 §9 receiver → linked solar satellites; derived on restore, never persisted. */
    private final ReceiverLinkIndex receiverLinks = new ReceiverLinkIndex();
    private final MissionDeadlineScheduler scheduler = new MissionDeadlineScheduler();
    private final MonotonicMissionClock clock;
    /** ADR-050 sections 5–7: counters, pruning queues, byte budgets and admission limits. */
    private final MissionRetention retention = new MissionRetention();
    private final StorageBudget budget = new StorageBudget();
    /** ADR-051 sections 2 and 7: live instances, the expiry queue and the terminal → mission index. */
    private final InstanceLedger ledger = new InstanceLedger();
    private final TerminalIndex terminals = new TerminalIndex();
    private final ResourceMissions resources = new ResourceMissions(this);
    private final RecordSizer sizer;
    private RegistryLimits limits = RegistryLimits.DEFAULTS;
    private RestoreReport restoreReport = new RestoreReport(0, 0, 0, 0);
    /**
     * ADR-050 §2 save epoch. A write carries {@link #epochToWrite()}; the epoch advances only when that write
     * returned without error and carried a change, so an unchanged reload writes the same bytes. A mission started
     * at epoch s is therefore present in every file whose epoch is greater than s.
     */
    private long saveEpoch;
    private boolean changedSinceEpoch;

    private SatelliteMissionRegistry(MonotonicMissionClock clock, long saveEpoch, RecordSizer sizer) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.sizer = Objects.requireNonNull(sizer, "sizer");
        if (saveEpoch < 1L) {
            throw new IllegalArgumentException("Save epoch starts at 1");
        }
        this.saveEpoch = saveEpoch;
    }

    public static SatelliteMissionRegistry create(long observedGameTime) {
        return create(observedGameTime, RecordSizer.BOUNDS);
    }

    public static SatelliteMissionRegistry create(long observedGameTime, RecordSizer sizer) {
        return new SatelliteMissionRegistry(MonotonicMissionClock.create(observedGameTime), 1L, sizer);
    }

    public static SatelliteMissionRegistry restore(long logicalGameTime, long lastObservedGameTime) {
        return restore(logicalGameTime, lastObservedGameTime, 1L);
    }

    public static SatelliteMissionRegistry restore(long logicalGameTime, long lastObservedGameTime, long saveEpoch) {
        return restore(logicalGameTime, lastObservedGameTime, saveEpoch, RecordSizer.BOUNDS);
    }

    public static SatelliteMissionRegistry restore(long logicalGameTime, long lastObservedGameTime, long saveEpoch,
                                                   RecordSizer sizer) {
        return new SatelliteMissionRegistry(
                MonotonicMissionClock.restore(logicalGameTime, lastObservedGameTime),
                saveEpoch,
                sizer
        );
    }

    /** Applies the server config limits (ADR-050 section 6); they govern admission only. */
    public synchronized void applyLimits(RegistryLimits next) {
        limits = Objects.requireNonNull(next, "next");
    }

    public synchronized RegistryLimits limits() {
        return limits;
    }

    /** What the last {@link #finishRestore()} changed. */
    public synchronized RestoreReport restoreReport() {
        return restoreReport;
    }

    public synchronized void restoreSatellite(SatelliteState state) {
        Objects.requireNonNull(state, "state");
        if (satellites.size() >= SatelliteLimits.MAX_SATELLITES) {
            throw new IllegalArgumentException("Satellite registry exceeds its fixed bound");
        }
        if (satellites.putIfAbsent(state.satelliteId(), state) != null) {
            throw new IllegalArgumentException("Duplicate satellite id " + state.satelliteId());
        }
        satellitesByOwner.merge(state.ownerId(), 1, Integer::sum);
        receiverLinks.update(null, state);
        budget.reserve(StorageBudget.Section.SATELLITES, state.satelliteId(), sizer.satelliteBytes(state));
    }

    public synchronized void restoreMission(MissionState state) {
        Objects.requireNonNull(state, "state");
        if (missions.size() >= SatelliteLimits.MAX_MISSIONS) {
            throw new IllegalArgumentException("Mission registry exceeds its fixed bound");
        }
        if (missions.containsKey(state.missionId())) {
            throw new IllegalArgumentException("Duplicate mission id " + state.missionId());
        }
        putMission(state);
        budget.reserve(StorageBudget.Section.MISSIONS, state.missionId(), sizer.missionBytes(state));
        resources.restored(state);
    }

    public synchronized void restoreAccount(ResearchAccount account) {
        Objects.requireNonNull(account, "account");
        if (accounts.size() >= SatelliteLimits.MAX_RESEARCH_ACCOUNTS) {
            throw new IllegalArgumentException("Research account registry exceeds its fixed bound");
        }
        if (accounts.putIfAbsent(account.ownerId(), account) != null) {
            throw new IllegalArgumentException("Duplicate research account " + account.ownerId());
        }
        budget.reserve(StorageBudget.Section.ACCOUNTS, account.ownerId(), RecordSizer.ACCOUNT_BYTES);
    }

    public synchronized void restoreInstance(AsteroidInstance instance) {
        Objects.requireNonNull(instance, "instance");
        if (instances.size() >= SatelliteLimits.MAX_INSTANCES) {
            throw new IllegalArgumentException("Asteroid instance registry exceeds its fixed bound");
        }
        if (instances.putIfAbsent(instance.instanceId(), instance) != null) {
            throw new IllegalArgumentException("Duplicate asteroid instance " + instance.instanceId());
        }
        budget.reserve(StorageBudget.Section.INSTANCES, instance.instanceId(), sizer.instanceBytes(instance));
        ledger.changed(null, instance);
    }

    /**
     * ADR-050 section 9: one bounded pass over the restored records. Broken references never block the registry:
     * an unfinished mission without its satellite binding is QUARANTINED (the satellite is not changed), a
     * satellite whose current mission is not unfinished becomes RECOVERY_REQUIRED, and an instance whose mission
     * reference is broken is QUARANTINED. Nothing is deleted, completed or paid. Over-admission-limit roots
     * still load, but a missing-account repair must fit the fixed load count and storage budget.
     */
    public synchronized RestoreReport finishRestore() {
        int accountsAdded = AccountRecovery.restore(satellites.values(), accounts, budget);
        RegistryInvariants.Plan plan = RegistryInvariants.plan(satellites, missions, instances);
        plan.missionQuarantines().forEach((id, reason) -> putMission(missions.get(id).quarantine(reason, false)));
        plan.recoveries().forEach(id -> satellites.put(id, satellites.get(id).requireRecovery()));
        plan.instanceQuarantines().forEach(id -> storeInstance(instances.get(id).quarantine()));
        scheduler.rebuild(missions.values());
        RestoreReport report = new RestoreReport(plan.missionQuarantines().size(), plan.recoveries().size(),
                plan.instanceQuarantines().size(), accountsAdded);
        if (report.changed()) {
            changedSinceEpoch = true;
        }
        restoreReport = report;
        return report;
    }

    public synchronized SatelliteOperationResult launch(UUID satelliteId, UUID missionId, UUID ownerId,
            SatelliteDefinition definition, ResourceLocation targetBodyId, long observedGameTime,
            boolean discoveryRequired) {
        requireAll(satelliteId, missionId, ownerId, definition, targetBodyId);
        long logicalTime = clock.advance(observedGameTime);

        SatelliteState existing = satellites.get(satelliteId);
        if (existing != null) {
            MissionState existingMission = missions.get(missionId);
            // ADR-049 section 6 (R2-L6): a replayed package is consumed whatever the satellite has done since.
            if (existing.ownerId().equals(ownerId)
                    && existing.definitionId().equals(definition.id())
                    && existing.kind() == SatelliteKind.DATA) {
                return result(SatelliteOperationCode.IDEMPOTENT, false, existing, existingMission);
            }
            return result(SatelliteOperationCode.IDENTITY_CONFLICT, false, existing, existingMission);
        }
        if (!definition.allows(targetBodyId)) {
            return result(SatelliteOperationCode.TARGET_NOT_ALLOWED, false, null, null);
        }
        if (missions.containsKey(missionId)) {
            return result(SatelliteOperationCode.IDENTITY_CONFLICT, false, null, missions.get(missionId));
        }
        SatelliteState satellite = SatelliteState.launch(satelliteId, definition.id(), ownerId, logicalTime);
        MissionState mission;
        try {
            mission = MissionState.start(missionId, satelliteId, ownerId, SatelliteDefinitionSnapshot.from(definition),
                    targetBodyId, logicalTime, discoveryRequired, saveEpoch);
        } catch (ArithmeticException exception) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, null, null);
        }
        satellite = satellite.startMission(missionId);
        SatelliteOperationCode refused = admission(ownerId, satellite, mission);
        if (refused != null) {
            return result(refused, false, null, null);
        }
        satellites.put(satelliteId, satellite);
        satellitesByOwner.merge(ownerId, 1, Integer::sum);
        budget.reserve(StorageBudget.Section.SATELLITES, satelliteId, sizer.satelliteBytes(satellite));
        putMission(mission);
        budget.reserve(StorageBudget.Section.MISSIONS, missionId, sizer.missionBytes(mission));
        admitAccount(ownerId);
        scheduler.schedule(mission);
        return result(SatelliteOperationCode.SUCCESS, true, satellite, mission);
    }

    /**
     * Registers a non-data satellite idle in its orbit body (ADR-049 section 6). The caller validated the
     * definition, blueprint, research and orbit body. Replays of the same identity are idempotent.
     */
    public synchronized SatelliteOperationResult launchIdle(java.util.function.LongFunction<SatelliteState> factory,
                                                           long observedGameTime) {
        Objects.requireNonNull(factory, "factory");
        long logicalTime = clock.advance(observedGameTime);
        SatelliteState candidate = Objects.requireNonNull(factory.apply(logicalTime), "candidate");
        if (candidate.kind() == SatelliteKind.DATA || candidate.currentMissionId().isPresent()
                || candidate.status() != SatelliteStatus.OPERATIONAL) {
            throw new IllegalArgumentException("An idle launch needs an operational non-data satellite");
        }
        SatelliteState existing = satellites.get(candidate.satelliteId());
        if (existing != null) {
            boolean same = existing.ownerId().equals(candidate.ownerId())
                    && existing.definitionId().equals(candidate.definitionId())
                    && existing.kind() == candidate.kind();
            return result(same ? SatelliteOperationCode.IDEMPOTENT : SatelliteOperationCode.IDENTITY_CONFLICT,
                    false, existing, null);
        }
        SatelliteOperationCode refused = admission(candidate.ownerId(), candidate, null);
        if (refused != null) {
            return result(refused, false, null, null);
        }
        satellites.put(candidate.satelliteId(), candidate);
        satellitesByOwner.merge(candidate.ownerId(), 1, Integer::sum);
        receiverLinks.update(null, candidate);
        budget.reserve(StorageBudget.Section.SATELLITES, candidate.satelliteId(), sizer.satelliteBytes(candidate));
        admitAccount(candidate.ownerId());
        return result(SatelliteOperationCode.SUCCESS, true, candidate, null);
    }

    /**
     * Removes an idle satellite (ADR-049 section 7): no unfinished mission and no live receiver link.
     * Finished missions keep referencing it (ADR-050 section 9). Nothing is refunded.
     */
    public synchronized SatelliteOperationResult decommission(UUID satelliteId, UUID requesterId, boolean operator,
                                                             boolean receiverMissing) {
        requireAll(satelliteId, requesterId);
        SatelliteState satellite = satellites.get(satelliteId);
        if (satellite == null) {
            return result(SatelliteOperationCode.SATELLITE_NOT_FOUND, false, null, null);
        }
        if (!operator && !satellite.ownerId().equals(requesterId)) {
            return result(SatelliteOperationCode.UNAUTHORIZED, false, satellite, null);
        }
        if (satellite.currentMissionId().isPresent()) {
            return result(SatelliteOperationCode.MISSION_BUSY, false, satellite,
                    missions.get(satellite.currentMissionId().orElseThrow()));
        }
        if (satellite.kindState() instanceof SatelliteKindState.Solar solar
                && solar.receiver().isPresent() && !receiverMissing) {
            return result(SatelliteOperationCode.MISSION_BUSY, false, satellite, null);
        }
        satellites.remove(satelliteId);
        satellitesByOwner.computeIfPresent(satellite.ownerId(), (owner, count) -> count <= 1 ? null : count - 1);
        receiverLinks.update(satellite, null);
        budget.release(StorageBudget.Section.SATELLITES, satelliteId);
        return result(SatelliteOperationCode.SUCCESS, true, null, null);
    }

    /** Replaces an existing satellite's kind state (lazy charge, receiver link); kind and identity stay. */
    public synchronized SatelliteOperationResult updateKindState(UUID satelliteId, SatelliteKindState next) {
        SatelliteState satellite = satellites.get(Objects.requireNonNull(satelliteId, "satelliteId"));
        if (satellite == null) {
            return result(SatelliteOperationCode.SATELLITE_NOT_FOUND, false, null, null);
        }
        if (satellite.kindState().equals(next)) {
            return result(SatelliteOperationCode.IDEMPOTENT, false, satellite, null);
        }
        SatelliteState updated = satellite.withKindState(next);
        satellites.put(satelliteId, updated);
        receiverLinks.update(satellite, updated);
        return result(SatelliteOperationCode.SUCCESS, true, updated, null);
    }

    /**
     * ADR-049 section 8: pays one area scan from the survey satellite's lazy battery. Only a paid scan changes
     * the record; a refused one leaves it untouched.
     */
    public synchronized SatelliteOperationResult payScan(UUID satelliteId, UUID requesterId, boolean operator,
                                                        long observedGameTime) {
        requireAll(satelliteId, requesterId);
        long logicalTime = clock.advance(observedGameTime);
        SatelliteState satellite = satellites.get(satelliteId);
        if (satellite == null) {
            return result(SatelliteOperationCode.SATELLITE_NOT_FOUND, false, null, null);
        }
        if (!operator && !satellite.ownerId().equals(requesterId)) {
            return result(SatelliteOperationCode.UNAUTHORIZED, false, satellite, null);
        }
        if (!(satellite.kindState() instanceof SatelliteKindState.Survey survey)) {
            return result(SatelliteOperationCode.DEFINITION_NOT_FOUND, false, satellite, null);
        }
        Optional<SatelliteKindState.Survey> after = survey.pay(logicalTime, satellite.blueprint().stats().power(),
                satellite.blueprint().stats().battery());
        if (after.isEmpty()) {
            return result(SatelliteOperationCode.NO_POWER, false, satellite, null);
        }
        SatelliteState paid = satellite.withKindState(after.get());
        satellites.put(satelliteId, paid);
        return result(SatelliteOperationCode.SUCCESS, true, paid, null);
    }

    /** ADR-050 sections 4 and 9: see {@link OperatorRecovery#releaseQuarantine}. */
    public synchronized SatelliteOperationResult releaseQuarantine(UUID missionId) {
        return OperatorRecovery.releaseQuarantine(this, Objects.requireNonNull(missionId, "missionId"));
    }

    /** ADR-050 section 8: see {@link OperatorRecovery#recoverSatellite}. */
    public synchronized SatelliteOperationResult recoverSatellite(UUID satelliteId) {
        return OperatorRecovery.recoverSatellite(this, Objects.requireNonNull(satelliteId, "satelliteId"));
    }

    public synchronized Optional<AsteroidInstance> instance(UUID instanceId) {
        return Optional.ofNullable(instances.get(instanceId));
    }

    /** Solar satellites whose link names this receiver, in ID order. */
    public synchronized List<UUID> linkedTo(UUID receiverId) {
        return receiverLinks.linkedTo(receiverId);
    }

    public synchronized int ownerSatellites(UUID ownerId) {
        return satellitesByOwner.getOrDefault(ownerId, 0);
    }

    public synchronized SatelliteOperationResult startMission(UUID satelliteId, UUID missionId, UUID ownerId,
            SatelliteDefinition definition, ResourceLocation targetBodyId, long observedGameTime,
            boolean discoveryRequired) {
        requireAll(satelliteId, missionId, ownerId, definition, targetBodyId);
        long logicalTime = clock.advance(observedGameTime);
        SatelliteState satellite = satellites.get(satelliteId);
        if (satellite == null) {
            return result(SatelliteOperationCode.SATELLITE_NOT_FOUND, false, null, null);
        }
        if (!satellite.ownerId().equals(ownerId)) {
            return result(SatelliteOperationCode.UNAUTHORIZED, false, satellite, null);
        }
        if (satellite.status() != SatelliteStatus.OPERATIONAL) {
            return result(SatelliteOperationCode.RECOVERY_REQUIRED, false, satellite, null);
        }
        if (!satellite.definitionId().equals(definition.id())) {
            return result(SatelliteOperationCode.DEFINITION_NOT_FOUND, false, satellite, null);
        }
        if (!definition.allows(targetBodyId)) {
            return result(SatelliteOperationCode.TARGET_NOT_ALLOWED, false, satellite, null);
        }
        if (satellite.currentMissionId().isPresent()) {
            MissionState current = missions.get(satellite.currentMissionId().orElseThrow());
            if (current != null && current.missionId().equals(missionId)) {
                return result(SatelliteOperationCode.IDEMPOTENT, false, satellite, current);
            }
            return result(SatelliteOperationCode.MISSION_BUSY, false, satellite, current);
        }
        if (missions.containsKey(missionId)) {
            return result(SatelliteOperationCode.IDENTITY_CONFLICT, false, satellite, missions.get(missionId));
        }
        MissionState mission;
        try {
            mission = MissionState.start(missionId, satelliteId, ownerId, SatelliteDefinitionSnapshot.from(definition),
                    targetBodyId, logicalTime, discoveryRequired, saveEpoch);
        } catch (ArithmeticException exception) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, satellite, null);
        }
        SatelliteOperationCode refused = admission(ownerId, null, mission);
        if (refused != null) {
            return result(refused, false, satellite, null);
        }
        SatelliteState updated = satellite.startMission(missionId);
        satellites.put(satelliteId, updated);
        putMission(mission);
        budget.reserve(StorageBudget.Section.MISSIONS, missionId, sizer.missionBytes(mission));
        scheduler.schedule(mission);
        return result(SatelliteOperationCode.SUCCESS, true, updated, mission);
    }

    public synchronized SchedulerPass completeDue(long observedGameTime) {
        long before = clock.logicalGameTime();
        long logicalTime = clock.advance(observedGameTime);
        MissionDeadlineScheduler.DrainResult drained = scheduler.drainDue(logicalTime,
                SatelliteLimits.MAX_COMPLETIONS_PER_PASS, id -> Optional.ofNullable(missions.get(id)),
                mission -> putMission(mission.complete(logicalTime)));
        int pruned = retention.prune(logicalTime, limits.finishedPerOwner(), saveEpoch, missions::get,
                this::removeMission);
        int expired = ledger.pass(logicalTime, instances::get, this::storeInstance,
                instance -> dropInstance(instance.instanceId()));
        if (drained.completed() > 0 || pruned > 0 || expired > 0) {
            changedSinceEpoch = true;
        }
        return new SchedulerPass(logicalTime, logicalTime != before, drained.completed(), drained.inspectedEntries(),
                drained.staleEntries(), drained.remainingScheduled(), pruned, expired);
    }

    public synchronized SatelliteOperationResult claim(UUID missionId, UUID ownerId, long observedGameTime) {
        requireAll(missionId, ownerId);
        long logicalTime = clock.advance(observedGameTime);
        MissionState mission = missions.get(missionId);
        if (mission == null) {
            return result(SatelliteOperationCode.MISSION_NOT_FOUND, false, null, null);
        }
        SatelliteState satellite = satellites.get(mission.satelliteId());
        if (!mission.ownerId().equals(ownerId)) {
            return result(SatelliteOperationCode.UNAUTHORIZED, false, satellite, mission);
        }
        if (mission.kind() != MissionKind.DATA) {
            // Survey, asteroid and gas missions are claimed through resources() (ADR-051).
            return result(SatelliteOperationCode.DEFINITION_NOT_FOUND, false, satellite, mission);
        }
        boolean completedNow = false;
        if (mission.status() == MissionStatus.ACTIVE
                && logicalTime >= mission.completesAtLogicalTime()) {
            mission = mission.complete(logicalTime);
            putMission(mission);
            scheduler.remove(missionId);
            completedNow = true;
        }
        if (mission.status() == MissionStatus.QUARANTINED) {
            return result(SatelliteOperationCode.RECOVERY_REQUIRED, false, satellite, mission);
        }
        if (mission.status() == MissionStatus.ACTIVE) {
            return result(SatelliteOperationCode.NOT_READY, false, satellite, mission);
        }
        if (mission.status() == MissionStatus.CANCELLED) {
            return result(SatelliteOperationCode.CANCELLED, false, satellite, mission);
        }
        if (mission.status() == MissionStatus.CLAIM_PENDING_DISCOVERY) {
            return result(SatelliteOperationCode.PENDING_DISCOVERY, false, satellite, mission);
        }
        if (mission.status() == MissionStatus.CLAIMED) {
            return result(SatelliteOperationCode.ALREADY_CLAIMED, false, satellite, mission);
        }

        ResearchAccount account = accounts.getOrDefault(ownerId, ResearchAccount.empty(ownerId));
        ResearchAccount updated;
        try {
            updated = account.creditAndSpend(
                    mission.researchYield(),
                    mission.discoveryRequired() ? mission.discoveryCost() : 0
            );
        } catch (IllegalStateException exception) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, completedNow, satellite, mission);
        }
        MissionState claimed = mission.beginClaim(logicalTime);
        accounts.put(ownerId, updated);
        putMission(claimed);
        if (claimed.status() == MissionStatus.CLAIMED) {
            satellite = finishSatelliteMission(satellite, missionId);
        }
        return new SatelliteOperationResult(claimed.status() == MissionStatus.CLAIM_PENDING_DISCOVERY
                ? SatelliteOperationCode.PENDING_DISCOVERY : SatelliteOperationCode.SUCCESS, true,
                Optional.ofNullable(satellite), Optional.of(claimed), updated.balance());
    }

    public synchronized SatelliteOperationResult finishDiscovery(UUID missionId) {
        Objects.requireNonNull(missionId, "missionId");
        MissionState mission = missions.get(missionId);
        if (mission == null) {
            return result(SatelliteOperationCode.MISSION_NOT_FOUND, false, null, null);
        }
        SatelliteState satellite = satellites.get(mission.satelliteId());
        if (mission.status() == MissionStatus.CLAIMED) {
            return result(SatelliteOperationCode.ALREADY_CLAIMED, false, satellite, mission);
        }
        if (mission.status() != MissionStatus.CLAIM_PENDING_DISCOVERY) {
            return result(SatelliteOperationCode.NOT_READY, false, satellite, mission);
        }
        MissionState claimed = mission.finishDiscovery();
        putMission(claimed);
        satellite = finishSatelliteMission(satellite, missionId);
        return result(SatelliteOperationCode.SUCCESS, true, satellite, claimed);
    }

    /** Cancels by ID; survey, asteroid and gas missions follow ADR-051 (no bound terminal on this path). */
    public synchronized SatelliteOperationResult cancel(UUID missionId, UUID requesterId, boolean operator,
                                                       long observedGameTime) {
        requireAll(missionId, requesterId);
        MissionState mission = missions.get(missionId);
        if (mission != null && mission.kind() != MissionKind.DATA) {
            return resources.cancel(missionId, requesterId, operator, Optional.empty(), observedGameTime);
        }
        long logicalTime = clock.advance(observedGameTime);
        if (mission == null) {
            return result(SatelliteOperationCode.MISSION_NOT_FOUND, false, null, null);
        }
        SatelliteState satellite = satellites.get(mission.satelliteId());
        if (!operator && !mission.ownerId().equals(requesterId)) {
            return result(SatelliteOperationCode.UNAUTHORIZED, false, satellite, mission);
        }
        if (mission.status() == MissionStatus.CANCELLED) {
            return result(SatelliteOperationCode.CANCELLED, false, satellite, mission);
        }
        if (mission.status() == MissionStatus.CLAIMED || mission.phase() == MissionStatus.CLAIM_PENDING_DISCOVERY) {
            return result(SatelliteOperationCode.ALREADY_CLAIMED, false, satellite, mission);
        }
        if (mission.status() == MissionStatus.QUARANTINED && !operator) {
            return result(SatelliteOperationCode.RECOVERY_REQUIRED, false, satellite, mission);
        }
        MissionState cancelled = mission.cancel(logicalTime);
        putMission(cancelled);
        scheduler.remove(missionId);
        // ADR-050 section 8: only a satellite that names this mission is released.
        if (satellite != null && satellite.currentMissionId().filter(missionId::equals).isPresent()) {
            satellite = finishSatelliteMission(satellite, missionId);
        }
        return result(SatelliteOperationCode.SUCCESS, true, satellite, cancelled);
    }

    public synchronized Optional<SatelliteState> satellite(UUID satelliteId) {
        return Optional.ofNullable(satellites.get(satelliteId));
    }

    public synchronized Optional<MissionState> mission(UUID missionId) {
        return Optional.ofNullable(missions.get(missionId));
    }

    public synchronized ResearchAccount account(UUID ownerId) {
        return accounts.getOrDefault(ownerId, ResearchAccount.empty(ownerId));
    }

    public synchronized List<SatelliteState> satellites() {
        return sorted(satellites);
    }

    public synchronized List<MissionState> missions() {
        return sorted(missions);
    }

    public synchronized List<ResearchAccount> accounts() {
        return sorted(accounts);
    }

    public synchronized List<MissionState> pendingDiscoveries() {
        return missions().stream()
                .filter(mission -> mission.status() == MissionStatus.CLAIM_PENDING_DISCOVERY)
                .toList();
    }

    public synchronized List<AsteroidInstance> instances() {
        return sorted(instances);
    }

    private static <T> List<T> sorted(Map<UUID, T> records) {
        List<UUID> ids = new ArrayList<>(records.keySet());
        ids.sort(UUID_ORDER);
        List<T> values = new ArrayList<>(ids.size());
        ids.forEach(id -> values.add(records.get(id)));
        return Collections.unmodifiableList(values);
    }

    public synchronized long saveEpoch() {
        return saveEpoch;
    }

    /** Records that the registry state changed since the last persisted epoch. */
    public synchronized void markChanged() {
        changedSinceEpoch = true;
    }

    /** The epoch value the next write carries. */
    public synchronized long epochToWrite() {
        return changedSinceEpoch ? Math.addExact(saveEpoch, 1L) : saveEpoch;
    }

    /** Called once a write carrying {@link #epochToWrite()} has returned without error. */
    public synchronized void markPersisted() {
        if (changedSinceEpoch) {
            saveEpoch = Math.addExact(saveEpoch, 1L);
            changedSinceEpoch = false;
            resources.epochAdvanced();
        }
    }

    public synchronized long logicalGameTime() {
        return clock.logicalGameTime();
    }

    public synchronized long lastObservedGameTime() {
        return clock.lastObservedGameTime();
    }

    public synchronized long unfinishedMissionCount() {
        return retention.unfinished();
    }

    public synchronized int finishedMissionCount() {
        return retention.finished();
    }

    /** Reserved lifecycle bytes of one section: satellites, missions, instances or accounts. */
    public synchronized long reservedBytes(String section) {
        return budget.reserved(StorageBudget.Section.valueOf(section));
    }

    public synchronized int scheduledCount() {
        return scheduler.scheduledCount();
    }

    /**
     * ADR-050 sections 6–7 admission of new records: counts first, then the lifecycle byte reservations.
     * {@code satellite} and {@code mission} are the records about to be admitted, either may be null.
     */
    SatelliteOperationCode admission(UUID ownerId, SatelliteState satellite, MissionState mission) {
        boolean newAccount = !accounts.containsKey(ownerId);
        if (newAccount && accounts.size() >= SatelliteLimits.MAX_RESEARCH_ACCOUNTS) {
            return SatelliteOperationCode.CAPACITY_REACHED;
        }
        if (satellite != null) {
            if (satellites.size() >= limits.satellitesGlobal()) {
                return SatelliteOperationCode.CAPACITY_REACHED;
            }
            if (ownerSatellites(ownerId) >= limits.satellitesPerOwner()) {
                return SatelliteOperationCode.OWNER_LIMIT;
            }
        }
        if (mission != null) {
            if (missions.size() >= Math.min(limits.missionsTotal(), SatelliteLimits.MAX_MISSIONS)
                    || retention.unfinished() >= limits.unfinishedGlobal()) {
                return SatelliteOperationCode.CAPACITY_REACHED;
            }
            if (retention.unfinished(ownerId) >= limits.unfinishedPerOwner()) {
                return SatelliteOperationCode.OWNER_LIMIT;
            }
        }
        if (satellite != null && !budget.fits(StorageBudget.Section.SATELLITES, sizer.satelliteBytes(satellite))
                || mission != null && !budget.fits(StorageBudget.Section.MISSIONS, sizer.missionBytes(mission))
                || newAccount && !budget.fits(StorageBudget.Section.ACCOUNTS, RecordSizer.ACCOUNT_BYTES)) {
            return SatelliteOperationCode.STORAGE_BUDGET;
        }
        return null;
    }

    private void admitAccount(UUID ownerId) {
        if (!accounts.containsKey(ownerId)) {
            accounts.put(ownerId, ResearchAccount.empty(ownerId));
            budget.reserve(StorageBudget.Section.ACCOUNTS, ownerId, RecordSizer.ACCOUNT_BYTES);
        }
    }

    /** Every mission write goes through here, so counters, queues and indexes never drift. */
    void putMission(MissionState next) {
        MissionState previous = missions.put(next.missionId(), next);
        retention.changed(previous, next, saveEpoch);
        terminals.changed(previous, next);
    }

    void removeMission(MissionState mission) {
        missions.remove(mission.missionId());
        retention.changed(mission, null, saveEpoch);
        terminals.changed(mission, null);
        budget.release(StorageBudget.Section.MISSIONS, mission.missionId());
    }

    // --- Package primitives for ResourceMissions (ADR-051); callers hold this registry's lock. ---

    long advanceClock(long observedGameTime) {
        return clock.advance(observedGameTime);
    }

    MissionState missionRecord(UUID missionId) {
        return missions.get(missionId);
    }

    SatelliteState satelliteRecord(UUID satelliteId) {
        return satellites.get(satelliteId);
    }

    AsteroidInstance instanceRecord(UUID instanceId) {
        return instances.get(instanceId);
    }

    void storeSatellite(SatelliteState satellite) {
        receiverLinks.update(satellites.put(satellite.satelliteId(), satellite), satellite);
    }

    void storeInstance(AsteroidInstance instance) {
        ledger.changed(instances.put(instance.instanceId(), instance), instance);
    }

    void dropInstance(UUID instanceId) {
        AsteroidInstance previous = instances.remove(instanceId);
        if (previous != null) {
            ledger.changed(previous, null);
            budget.release(StorageBudget.Section.INSTANCES, instanceId);
        }
    }

    /** Admits new records: unfinished mission (scheduled) and its instances, with their byte reservations. */
    void admit(MissionState mission, List<AsteroidInstance> created) {
        created.forEach(instance -> {
            storeInstance(instance);
            budget.reserve(StorageBudget.Section.INSTANCES, instance.instanceId(), sizer.instanceBytes(instance));
        });
        putMission(mission);
        budget.reserve(StorageBudget.Section.MISSIONS, mission.missionId(), sizer.missionBytes(mission));
        scheduler.schedule(mission);
    }

    boolean instancesFit(List<AsteroidInstance> created) {
        return budget.fits(StorageBudget.Section.INSTANCES,
                created.stream().mapToInt(sizer::instanceBytes).sum());
    }

    void unschedule(UUID missionId) {
        scheduler.remove(missionId);
    }

    void schedule(MissionState mission) {
        scheduler.schedule(mission);
    }

    Map<UUID, AsteroidInstance> instanceMap() {
        return Collections.unmodifiableMap(instances);
    }

    int instanceCount() {
        return instances.size();
    }

    InstanceLedger ledger() {
        return ledger;
    }

    List<UUID> boundTo(UUID terminal) {
        return terminals.boundTo(terminal);
    }

    List<UUID> awaiting(UUID terminal) {
        return terminals.awaiting(terminal);
    }

    void reoffer(List<MissionState> durable) {
        retention.epochAdvanced(durable, saveEpoch);
    }

    /** Releases the satellite only if it names this mission (ADR-050 section 8). */
    SatelliteState release(MissionState mission) {
        SatelliteState satellite = satellites.get(mission.satelliteId());
        return satellite != null && satellite.currentMissionId().filter(mission.missionId()::equals).isPresent()
                ? finishSatelliteMission(satellite, mission.missionId()) : satellite;
    }

    /** ADR-051 survey, asteroid and gas missions, instances and delivery; all calls hold this registry's lock. */
    public ResourceMissions resources() {
        return resources;
    }

    private static void requireAll(Object... values) {
        for (Object value : values) {
            Objects.requireNonNull(value);
        }
    }

    private SatelliteState finishSatelliteMission(SatelliteState satellite, UUID missionId) {
        if (satellite == null) {
            throw new IllegalStateException("Mission satellite is missing");
        }
        SatelliteState updated = satellite.finishMission(missionId);
        satellites.put(updated.satelliteId(), updated);
        return updated;
    }

    SatelliteOperationResult result(SatelliteOperationCode code, boolean changed, SatelliteState satellite,
                                    MissionState mission) {
        UUID owner = mission != null ? mission.ownerId() : satellite == null ? null : satellite.ownerId();
        int balance = owner == null ? 0 : account(owner).balance();
        return new SatelliteOperationResult(code, changed, Optional.ofNullable(satellite), Optional.ofNullable(mission),
                balance);
    }
}

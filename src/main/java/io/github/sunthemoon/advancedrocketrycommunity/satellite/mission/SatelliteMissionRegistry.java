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
    private final Map<UUID, java.util.Set<UUID>> receiverLinks = new java.util.HashMap<>();
    private final MissionDeadlineScheduler scheduler = new MissionDeadlineScheduler();
    private final MonotonicMissionClock clock;
    /**
     * ADR-050 §2 save epoch. A write carries {@link #epochToWrite()}; the epoch advances only when that write
     * returned without error and carried a change, so an unchanged reload writes the same bytes. A mission started
     * at epoch s is therefore present in every file whose epoch is greater than s.
     */
    private long saveEpoch;
    private boolean changedSinceEpoch;

    private SatelliteMissionRegistry(MonotonicMissionClock clock, long saveEpoch) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (saveEpoch < 1L) {
            throw new IllegalArgumentException("Save epoch starts at 1");
        }
        this.saveEpoch = saveEpoch;
    }

    public static SatelliteMissionRegistry create(long observedGameTime) {
        return new SatelliteMissionRegistry(MonotonicMissionClock.create(observedGameTime), 1L);
    }

    public static SatelliteMissionRegistry restore(long logicalGameTime, long lastObservedGameTime) {
        return restore(logicalGameTime, lastObservedGameTime, 1L);
    }

    public static SatelliteMissionRegistry restore(long logicalGameTime, long lastObservedGameTime, long saveEpoch) {
        return new SatelliteMissionRegistry(
                MonotonicMissionClock.restore(logicalGameTime, lastObservedGameTime),
                saveEpoch
        );
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
        indexLink(null, state);
    }

    public synchronized void restoreMission(MissionState state) {
        Objects.requireNonNull(state, "state");
        if (missions.size() >= SatelliteLimits.MAX_MISSIONS) {
            throw new IllegalArgumentException("Mission registry exceeds its fixed bound");
        }
        if (missions.putIfAbsent(state.missionId(), state) != null) {
            throw new IllegalArgumentException("Duplicate mission id " + state.missionId());
        }
    }

    public synchronized void restoreAccount(ResearchAccount account) {
        Objects.requireNonNull(account, "account");
        if (accounts.size() >= SatelliteLimits.MAX_RESEARCH_ACCOUNTS) {
            throw new IllegalArgumentException("Research account registry exceeds its fixed bound");
        }
        if (accounts.putIfAbsent(account.ownerId(), account) != null) {
            throw new IllegalArgumentException("Duplicate research account " + account.ownerId());
        }
    }

    public synchronized void restoreInstance(AsteroidInstance instance) {
        Objects.requireNonNull(instance, "instance");
        if (instances.size() >= SatelliteLimits.MAX_INSTANCES) {
            throw new IllegalArgumentException("Asteroid instance registry exceeds its fixed bound");
        }
        if (instances.putIfAbsent(instance.instanceId(), instance) != null) {
            throw new IllegalArgumentException("Duplicate asteroid instance " + instance.instanceId());
        }
    }

    public synchronized void finishRestore() {
        long unfinished = missions.values().stream().filter(state -> state.status().unfinished()).count();
        if (unfinished > SatelliteLimits.MAX_ACTIVE_MISSIONS) {
            throw new IllegalArgumentException("Unfinished mission count exceeds its fixed bound");
        }
        for (SatelliteState satellite : satellites.values()) {
            if (!accounts.containsKey(satellite.ownerId())) {
                throw new IllegalArgumentException("Satellite owner has no research account");
            }
            if (satellite.currentMissionId().isEmpty()) {
                continue;
            }
            MissionState mission = missions.get(satellite.currentMissionId().orElseThrow());
            if (mission == null
                    || !mission.status().unfinished()
                    || !mission.satelliteId().equals(satellite.satelliteId())
                    || !mission.ownerId().equals(satellite.ownerId())
                    || !mission.definitionId().equals(satellite.definitionId())) {
                throw new IllegalArgumentException("Satellite has an invalid current mission reference");
            }
        }
        for (MissionState mission : missions.values()) {
            SatelliteState satellite = satellites.get(mission.satelliteId());
            if (satellite == null) {
                // ADR-050 §9: finished records may outlive a decommissioned satellite.
                if (mission.status().unfinished()) {
                    throw new IllegalArgumentException("Unfinished mission has no satellite");
                }
                continue;
            }
            if (!satellite.ownerId().equals(mission.ownerId())
                    || !satellite.definitionId().equals(mission.definitionId())) {
                throw new IllegalArgumentException("Mission has an invalid satellite reference");
            }
            if (mission.status().unfinished()
                    && !satellite.currentMissionId().filter(mission.missionId()::equals).isPresent()) {
                throw new IllegalArgumentException("Unfinished mission is not owned by its satellite");
            }
        }
        scheduler.rebuild(missions.values());
    }

    public synchronized SatelliteOperationResult launch(
            UUID satelliteId,
            UUID missionId,
            UUID ownerId,
            SatelliteDefinition definition,
            ResourceLocation targetBodyId,
            long observedGameTime,
            boolean discoveryRequired
    ) {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(missionId, "missionId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(targetBodyId, "targetBodyId");
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
        if (!hasCapacityFor(ownerId)) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, null, null);
        }
        if (ownerSatellites(ownerId) >= SatelliteLimits.MAX_SATELLITES_PER_OWNER) {
            return result(SatelliteOperationCode.OWNER_LIMIT, false, null, null);
        }

        SatelliteState satellite = SatelliteState.launch(
                satelliteId, definition.id(), ownerId, logicalTime
        );
        MissionState mission;
        try {
            mission = MissionState.start(
                    missionId,
                    satelliteId,
                    ownerId,
                    SatelliteDefinitionSnapshot.from(definition),
                    targetBodyId,
                    logicalTime,
                    discoveryRequired,
                    saveEpoch
            );
        } catch (ArithmeticException exception) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, null, null);
        }
        satellite = satellite.startMission(missionId);
        satellites.put(satelliteId, satellite);
        satellitesByOwner.merge(ownerId, 1, Integer::sum);
        missions.put(missionId, mission);
        accounts.computeIfAbsent(ownerId, ResearchAccount::empty);
        scheduler.schedule(mission);
        return result(SatelliteOperationCode.SUCCESS, true, satellite, mission);
    }

    /**
     * Registers a non-data satellite idle in its orbit body (ADR-049 section 6). The caller validated the
     * definition, blueprint, research and orbit body. Replays of the same identity are idempotent.
     */
    public synchronized SatelliteOperationResult launchIdle(
            java.util.function.LongFunction<SatelliteState> factory,
            long observedGameTime
    ) {
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
        if (satellites.size() >= SatelliteLimits.MAX_SATELLITES
                || !accounts.containsKey(candidate.ownerId()) && accounts.size() >= SatelliteLimits.MAX_RESEARCH_ACCOUNTS) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, null, null);
        }
        if (ownerSatellites(candidate.ownerId()) >= SatelliteLimits.MAX_SATELLITES_PER_OWNER) {
            return result(SatelliteOperationCode.OWNER_LIMIT, false, null, null);
        }
        satellites.put(candidate.satelliteId(), candidate);
        satellitesByOwner.merge(candidate.ownerId(), 1, Integer::sum);
        indexLink(null, candidate);
        accounts.computeIfAbsent(candidate.ownerId(), ResearchAccount::empty);
        return result(SatelliteOperationCode.SUCCESS, true, candidate, null);
    }

    /**
     * Removes an idle satellite (ADR-049 section 7): no unfinished mission and no live receiver link.
     * Finished missions keep referencing it (ADR-050 section 9). Nothing is refunded.
     */
    public synchronized SatelliteOperationResult decommission(
            UUID satelliteId,
            UUID requesterId,
            boolean operator,
            boolean receiverMissing
    ) {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(requesterId, "requesterId");
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
        indexLink(satellite, null);
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
        indexLink(satellite, updated);
        return result(SatelliteOperationCode.SUCCESS, true, updated, null);
    }

    /**
     * ADR-049 section 8: pays one area scan from the survey satellite's lazy battery. Only a paid scan changes
     * the record; a refused one leaves it untouched.
     */
    public synchronized SatelliteOperationResult payScan(
            UUID satelliteId,
            UUID requesterId,
            boolean operator,
            long observedGameTime
    ) {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(requesterId, "requesterId");
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
        long charge = survey.chargeAt(logicalTime, satellite.blueprint().stats().power(),
                satellite.blueprint().stats().battery());
        if (charge < survey.scanEnergy()) {
            return result(SatelliteOperationCode.NO_POWER, false, satellite, null);
        }
        SatelliteState paid = satellite.withKindState(new SatelliteKindState.Survey(charge - survey.scanEnergy(),
                logicalTime, survey.scanEnergy(), survey.scanRadius(), survey.scanCell()));
        satellites.put(satelliteId, paid);
        return result(SatelliteOperationCode.SUCCESS, true, paid, null);
    }

    /** Solar satellites whose link names this receiver, in ID order. */
    public synchronized List<UUID> linkedTo(UUID receiverId) {
        return receiverLinks.getOrDefault(Objects.requireNonNull(receiverId, "receiverId"), java.util.Set.of())
                .stream().sorted(UUID_ORDER).toList();
    }

    private void indexLink(SatelliteState previous, SatelliteState next) {
        receiver(previous).ifPresent(receiver -> receiverLinks.computeIfPresent(receiver, (key, linked) -> {
            linked.remove(previous.satelliteId());
            return linked.isEmpty() ? null : linked;
        }));
        receiver(next).ifPresent(receiver -> receiverLinks
                .computeIfAbsent(receiver, key -> new java.util.HashSet<>()).add(next.satelliteId()));
    }

    private static java.util.Optional<UUID> receiver(SatelliteState state) {
        return state != null && state.kindState() instanceof SatelliteKindState.Solar solar
                ? solar.receiver() : java.util.Optional.empty();
    }

    public synchronized int ownerSatellites(UUID ownerId) {
        return satellitesByOwner.getOrDefault(ownerId, 0);
    }

    public synchronized SatelliteOperationResult startMission(
            UUID satelliteId,
            UUID missionId,
            UUID ownerId,
            SatelliteDefinition definition,
            ResourceLocation targetBodyId,
            long observedGameTime,
            boolean discoveryRequired
    ) {
        Objects.requireNonNull(satelliteId, "satelliteId");
        Objects.requireNonNull(missionId, "missionId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(targetBodyId, "targetBodyId");
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
        if (missions.size() >= SatelliteLimits.MAX_MISSIONS
                || unfinishedMissionCount() >= SatelliteLimits.MAX_ACTIVE_MISSIONS) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, satellite, null);
        }

        MissionState mission;
        try {
            mission = MissionState.start(
                    missionId,
                    satelliteId,
                    ownerId,
                    SatelliteDefinitionSnapshot.from(definition),
                    targetBodyId,
                    logicalTime,
                    discoveryRequired,
                    saveEpoch
            );
        } catch (ArithmeticException exception) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, satellite, null);
        }
        SatelliteState updated = satellite.startMission(missionId);
        satellites.put(satelliteId, updated);
        missions.put(missionId, mission);
        scheduler.schedule(mission);
        return result(SatelliteOperationCode.SUCCESS, true, updated, mission);
    }

    public synchronized SchedulerPass completeDue(long observedGameTime) {
        long before = clock.logicalGameTime();
        long logicalTime = clock.advance(observedGameTime);
        MissionDeadlineScheduler.DrainResult drained = scheduler.drainDue(
                logicalTime,
                SatelliteLimits.MAX_COMPLETIONS_PER_PASS,
                id -> Optional.ofNullable(missions.get(id)),
                mission -> missions.put(mission.missionId(), mission.complete(logicalTime))
        );
        return new SchedulerPass(
                logicalTime,
                logicalTime != before,
                drained.completed(),
                drained.inspectedEntries(),
                drained.staleEntries(),
                drained.remainingScheduled()
        );
    }

    public synchronized SatelliteOperationResult claim(
            UUID missionId,
            UUID ownerId,
            long observedGameTime
    ) {
        Objects.requireNonNull(missionId, "missionId");
        Objects.requireNonNull(ownerId, "ownerId");
        long logicalTime = clock.advance(observedGameTime);
        MissionState mission = missions.get(missionId);
        if (mission == null) {
            return result(SatelliteOperationCode.MISSION_NOT_FOUND, false, null, null);
        }
        SatelliteState satellite = satellites.get(mission.satelliteId());
        if (!mission.ownerId().equals(ownerId)) {
            return result(SatelliteOperationCode.UNAUTHORIZED, false, satellite, mission);
        }
        boolean completedNow = false;
        if (mission.status() == MissionStatus.ACTIVE
                && logicalTime >= mission.completesAtLogicalTime()) {
            mission = mission.complete(logicalTime);
            missions.put(missionId, mission);
            completedNow = true;
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
        missions.put(missionId, claimed);
        if (claimed.status() == MissionStatus.CLAIMED) {
            satellite = finishSatelliteMission(satellite, missionId);
        }
        return new SatelliteOperationResult(
                claimed.status() == MissionStatus.CLAIM_PENDING_DISCOVERY
                        ? SatelliteOperationCode.PENDING_DISCOVERY
                        : SatelliteOperationCode.SUCCESS,
                true,
                Optional.ofNullable(satellite),
                Optional.of(claimed),
                updated.balance()
        );
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
        missions.put(missionId, claimed);
        satellite = finishSatelliteMission(satellite, missionId);
        return result(SatelliteOperationCode.SUCCESS, true, satellite, claimed);
    }

    public synchronized SatelliteOperationResult cancel(
            UUID missionId,
            UUID requesterId,
            boolean operator,
            long observedGameTime
    ) {
        Objects.requireNonNull(missionId, "missionId");
        Objects.requireNonNull(requesterId, "requesterId");
        long logicalTime = clock.advance(observedGameTime);
        MissionState mission = missions.get(missionId);
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
        if (mission.status() == MissionStatus.CLAIMED
                || mission.status() == MissionStatus.CLAIM_PENDING_DISCOVERY) {
            return result(SatelliteOperationCode.ALREADY_CLAIMED, false, satellite, mission);
        }
        MissionState cancelled = mission.cancel(logicalTime);
        missions.put(missionId, cancelled);
        satellite = finishSatelliteMission(satellite, missionId);
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
        List<SatelliteState> values = new ArrayList<>(satellites.values());
        values.sort((left, right) -> UUID_ORDER.compare(left.satelliteId(), right.satelliteId()));
        return Collections.unmodifiableList(values);
    }

    public synchronized List<MissionState> missions() {
        List<MissionState> values = new ArrayList<>(missions.values());
        values.sort((left, right) -> UUID_ORDER.compare(left.missionId(), right.missionId()));
        return Collections.unmodifiableList(values);
    }

    public synchronized List<ResearchAccount> accounts() {
        List<ResearchAccount> values = new ArrayList<>(accounts.values());
        values.sort((left, right) -> UUID_ORDER.compare(left.ownerId(), right.ownerId()));
        return Collections.unmodifiableList(values);
    }

    public synchronized List<MissionState> pendingDiscoveries() {
        return missions().stream()
                .filter(mission -> mission.status() == MissionStatus.CLAIM_PENDING_DISCOVERY)
                .toList();
    }

    public synchronized List<AsteroidInstance> instances() {
        List<AsteroidInstance> values = new ArrayList<>(instances.values());
        values.sort((left, right) -> UUID_ORDER.compare(left.instanceId(), right.instanceId()));
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
        }
    }

    public synchronized long logicalGameTime() {
        return clock.logicalGameTime();
    }

    public synchronized long lastObservedGameTime() {
        return clock.lastObservedGameTime();
    }

    public synchronized long unfinishedMissionCount() {
        return missions.values().stream().filter(state -> state.status().unfinished()).count();
    }

    private boolean hasCapacityFor(UUID ownerId) {
        return satellites.size() < SatelliteLimits.MAX_SATELLITES
                && missions.size() < SatelliteLimits.MAX_MISSIONS
                && unfinishedMissionCount() < SatelliteLimits.MAX_ACTIVE_MISSIONS
                && (accounts.containsKey(ownerId)
                || accounts.size() < SatelliteLimits.MAX_RESEARCH_ACCOUNTS);
    }

    private SatelliteState finishSatelliteMission(SatelliteState satellite, UUID missionId) {
        if (satellite == null) {
            throw new IllegalStateException("Mission satellite is missing");
        }
        SatelliteState updated = satellite.finishMission(missionId);
        satellites.put(updated.satelliteId(), updated);
        return updated;
    }

    private SatelliteOperationResult result(
            SatelliteOperationCode code,
            boolean changed,
            SatelliteState satellite,
            MissionState mission
    ) {
        UUID owner = mission != null ? mission.ownerId() : satellite == null ? null : satellite.ownerId();
        int balance = owner == null ? 0 : account(owner).balance();
        return new SatelliteOperationResult(
                code,
                changed,
                Optional.ofNullable(satellite),
                Optional.ofNullable(mission),
                balance
        );
    }

    public record SchedulerPass(
            long logicalGameTime,
            boolean clockAdvanced,
            int completed,
            int inspectedEntries,
            int staleEntries,
            int remainingScheduled
    ) {
    }
}

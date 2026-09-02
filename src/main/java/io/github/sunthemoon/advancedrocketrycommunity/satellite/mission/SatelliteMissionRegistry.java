package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
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
    private final MissionDeadlineScheduler scheduler = new MissionDeadlineScheduler();
    private final MonotonicMissionClock clock;

    private SatelliteMissionRegistry(MonotonicMissionClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static SatelliteMissionRegistry create(long observedGameTime) {
        return new SatelliteMissionRegistry(MonotonicMissionClock.create(observedGameTime));
    }

    public static SatelliteMissionRegistry restore(long logicalGameTime, long lastObservedGameTime) {
        return new SatelliteMissionRegistry(
                MonotonicMissionClock.restore(logicalGameTime, lastObservedGameTime)
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
            if (satellite == null
                    || !satellite.ownerId().equals(mission.ownerId())
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
            if (existing.ownerId().equals(ownerId)
                    && existing.definitionId().equals(definition.id())
                    && existingMission != null
                    && existingMission.satelliteId().equals(satelliteId)
                    && existingMission.ownerId().equals(ownerId)) {
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
                    discoveryRequired
            );
        } catch (ArithmeticException exception) {
            return result(SatelliteOperationCode.CAPACITY_REACHED, false, null, null);
        }
        satellite = satellite.startMission(missionId);
        satellites.put(satelliteId, satellite);
        missions.put(missionId, mission);
        accounts.computeIfAbsent(ownerId, ResearchAccount::empty);
        scheduler.schedule(mission);
        return result(SatelliteOperationCode.SUCCESS, true, satellite, mission);
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
                    discoveryRequired
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

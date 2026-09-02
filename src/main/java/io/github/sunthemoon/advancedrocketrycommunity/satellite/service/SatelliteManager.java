package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotSynchronizer;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Server-thread authority joining definitions, missions, research, and discovery. */
public final class SatelliteManager {
    private static final int MAX_DISCOVERY_REPLAYS_PER_TICK = 8;
    private static final String INITIAL_MISSION_PREFIX = "arce:data-satellite:first:";
    private static final String RELEASE_TEST_HOOK_PROPERTY =
            "advancedrocketrycommunity.releaseTestHooks";

    private final SatelliteCatalogManager satelliteCatalogs;
    private final CelestialCatalogManager celestialCatalogs;
    private final CelestialSnapshotSynchronizer snapshots;
    private final ArrayDeque<UUID> pendingDiscoveryReplay = new ArrayDeque<>();
    private boolean replayInitialized;

    public SatelliteManager(
            SatelliteCatalogManager satelliteCatalogs,
            CelestialCatalogManager celestialCatalogs,
            CelestialSnapshotSynchronizer snapshots
    ) {
        this.satelliteCatalogs = Objects.requireNonNull(satelliteCatalogs, "satelliteCatalogs");
        this.celestialCatalogs = Objects.requireNonNull(celestialCatalogs, "celestialCatalogs");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    public void onServerStarted(ServerStartedEvent event) {
        // The mod instance outlives integrated-server worlds. Reinstall the
        // narrow runtime bridge after ServerStoppedEvent cleared the old world.
        SatelliteRuntime.install(this);
        initializeDiscoveryReplay(event.getServer());
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (!replayInitialized) {
            initializeDiscoveryReplay(server);
        }
        replayDiscoveries(server);
        long gameTime = server.overworld().getGameTime();
        if (gameTime % SatelliteLimits.SCHEDULER_INTERVAL_TICKS != 0L) {
            return;
        }
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteMissionRegistry.SchedulerPass pass = data.completeDue(gameTime);
            if (pass.completed() > 0) {
                data.flush(server);
                AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_SATELLITE_SCHEDULER completed={} inspected={} remaining={}",
                        pass.completed(), pass.inspectedEntries(), pass.remainingScheduled()
                );
            }
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error("Satellite scheduler pass failed", exception);
        }
    }

    public SatelliteOperationResult launch(
            ServerPlayer player,
            SatelliteIdentity identity,
            ResourceLocation targetBodyId
    ) {
        SatelliteOperationResult rejected = validatePlayerIdentity(player, identity);
        if (rejected != null) {
            return rejected;
        }
        SatelliteDefinition definition = definition(identity.definitionId()).orElse(null);
        SatelliteOperationResult definitionFailure = validateDefinitionAndTarget(definition, targetBodyId);
        if (definitionFailure != null) {
            return definitionFailure;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return failure(SatelliteOperationCode.SERVER_ERROR);
        }
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteOperationResult result = data.launch(
                    identity.satelliteId(),
                    initialMissionId(identity.satelliteId()),
                    identity.ownerId(),
                    definition,
                    targetBodyId,
                    server.overworld().getGameTime(),
                    discoveryRequired(server, targetBodyId)
            );
            if (result.changed()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            logOperationFailure("launch", exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    public SatelliteOperationResult startMission(
            ServerPlayer player,
            SatelliteIdentity identity,
            ResourceLocation targetBodyId
    ) {
        SatelliteOperationResult rejected = validatePlayerIdentity(player, identity);
        if (rejected != null) {
            return rejected;
        }
        SatelliteDefinition definition = definition(identity.definitionId()).orElse(null);
        SatelliteOperationResult definitionFailure = validateDefinitionAndTarget(definition, targetBodyId);
        if (definitionFailure != null) {
            return definitionFailure;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return failure(SatelliteOperationCode.SERVER_ERROR);
        }
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteOperationResult result = data.startMission(
                    identity.satelliteId(),
                    UUID.randomUUID(),
                    identity.ownerId(),
                    definition,
                    targetBodyId,
                    server.overworld().getGameTime(),
                    discoveryRequired(server, targetBodyId)
            );
            if (result.changed()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            logOperationFailure("start", exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    public SatelliteOperationResult claimCurrent(ServerPlayer player, SatelliteIdentity identity) {
        SatelliteOperationResult rejected = validatePlayerIdentity(player, identity);
        if (rejected != null) {
            return rejected;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return failure(SatelliteOperationCode.SERVER_ERROR);
        }
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteState satellite = data.satellite(identity.satelliteId()).orElse(null);
        if (satellite == null) {
            return failure(SatelliteOperationCode.SATELLITE_NOT_FOUND);
        }
        UUID missionId = satellite.currentMissionId().orElse(null);
        return missionId == null
                ? failure(SatelliteOperationCode.MISSION_NOT_FOUND)
                : claimMission(server, missionId, player.getUUID());
    }

    /** Packaged-server hook; unavailable unless the dedicated evidence JVM flag is set. */
    public SatelliteOperationResult releaseTestLaunch(
            MinecraftServer server,
            UUID ownerId,
            ResourceLocation targetBodyId
    ) {
        if (!Boolean.getBoolean(RELEASE_TEST_HOOK_PROPERTY)) {
            return failure(SatelliteOperationCode.UNAUTHORIZED);
        }
        SatelliteDefinition definition = definition(io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds.DATA_SATELLITE)
                .orElse(null);
        SatelliteOperationResult definitionFailure = validateDefinitionAndTarget(definition, targetBodyId);
        if (definitionFailure != null) {
            return definitionFailure;
        }
        UUID satelliteId = UUID.randomUUID();
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteOperationResult result = data.launch(
                    satelliteId,
                    initialMissionId(satelliteId),
                    ownerId,
                    definition,
                    targetBodyId,
                    server.overworld().getGameTime(),
                    discoveryRequired(server, targetBodyId)
            );
            if (result.changed()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            logOperationFailure("release-test launch", exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    /** Packaged-server hook for restart and exact-once claim evidence. */
    public SatelliteOperationResult releaseTestClaim(MinecraftServer server, UUID missionId) {
        if (!Boolean.getBoolean(RELEASE_TEST_HOOK_PROPERTY)) {
            return failure(SatelliteOperationCode.UNAUTHORIZED);
        }
        MissionState mission = SatelliteMissionSavedData.get(server).mission(missionId).orElse(null);
        return mission == null
                ? failure(SatelliteOperationCode.MISSION_NOT_FOUND)
                : claimMission(server, missionId, mission.ownerId());
    }

    public SatelliteOperationResult cancelCurrent(
            ServerPlayer player,
            SatelliteIdentity identity,
            boolean operator
    ) {
        if (!operator && !identity.ownerId().equals(player.getUUID())) {
            return failure(SatelliteOperationCode.UNAUTHORIZED);
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return failure(SatelliteOperationCode.SERVER_ERROR);
        }
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteState satellite = data.satellite(identity.satelliteId()).orElse(null);
            if (satellite == null || satellite.currentMissionId().isEmpty()) {
                return failure(satellite == null
                        ? SatelliteOperationCode.SATELLITE_NOT_FOUND
                        : SatelliteOperationCode.MISSION_NOT_FOUND);
            }
            SatelliteOperationResult result = data.cancel(
                    satellite.currentMissionId().orElseThrow(),
                    player.getUUID(),
                    operator,
                    server.overworld().getGameTime()
            );
            if (result.changed()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            logOperationFailure("cancel", exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    public SatelliteOperationResult cancelAdmin(
            MinecraftServer server,
            UUID missionId,
            UUID requesterId
    ) {
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteOperationResult result = data.cancel(
                    missionId,
                    requesterId,
                    true,
                    server.overworld().getGameTime()
            );
            if (result.changed()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            logOperationFailure("admin cancel", exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    public Optional<SatelliteDefinition> definition(ResourceLocation definitionId) {
        return satelliteCatalogs.current().flatMap(catalog -> catalog.get(definitionId));
    }

    public List<ResourceLocation> targets(ResourceLocation definitionId) {
        return definition(definitionId).map(SatelliteDefinition::allowedTargets).orElse(List.of());
    }

    public Optional<SatelliteState> satellite(MinecraftServer server, UUID satelliteId) {
        return SatelliteMissionSavedData.get(server).satellite(satelliteId);
    }

    public Optional<MissionState> mission(MinecraftServer server, UUID missionId) {
        return SatelliteMissionSavedData.get(server).mission(missionId);
    }

    public Optional<MissionState> currentMission(MinecraftServer server, UUID satelliteId) {
        return satellite(server, satelliteId)
                .flatMap(state -> state.currentMissionId().flatMap(id -> mission(server, id)));
    }

    public List<SatelliteState> satellites(MinecraftServer server) {
        return SatelliteMissionSavedData.get(server).satellites();
    }

    public List<MissionState> missions(MinecraftServer server) {
        return SatelliteMissionSavedData.get(server).missions();
    }

    public int researchBalance(MinecraftServer server, UUID ownerId) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        return data.operational() ? data.account(ownerId).balance() : 0;
    }

    public boolean discovered(MinecraftServer server, ResourceLocation targetBodyId) {
        return CelestialSavedData.get(server).get(targetBodyId).isPresent();
    }

    public void clear() {
        pendingDiscoveryReplay.clear();
        replayInitialized = false;
        satelliteCatalogs.clear();
    }

    public static UUID initialMissionId(UUID satelliteId) {
        return UUID.nameUUIDFromBytes(
                (INITIAL_MISSION_PREFIX + satelliteId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private void initializeDiscoveryReplay(MinecraftServer server) {
        pendingDiscoveryReplay.clear();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        if (data.operational()) {
            data.pendingDiscoveries().stream()
                    .limit(SatelliteLimits.MAX_ACTIVE_MISSIONS)
                    .map(MissionState::missionId)
                    .forEach(pendingDiscoveryReplay::addLast);
        }
        replayInitialized = true;
    }

    private void replayDiscoveries(MinecraftServer server) {
        for (int replayed = 0;
             replayed < MAX_DISCOVERY_REPLAYS_PER_TICK && !pendingDiscoveryReplay.isEmpty();
             replayed++) {
            UUID missionId = pendingDiscoveryReplay.removeFirst();
            SatelliteOperationResult result = applyDiscovery(server, missionId);
            if (result.code() == SatelliteOperationCode.PENDING_DISCOVERY
                    || result.code() == SatelliteOperationCode.CATALOG_UNAVAILABLE) {
                pendingDiscoveryReplay.addLast(missionId);
                break;
            }
        }
    }

    private SatelliteOperationResult applyDiscovery(MinecraftServer server, UUID missionId) {
        SatelliteMissionSavedData missions = SatelliteMissionSavedData.get(server);
        MissionState mission = missions.mission(missionId).orElse(null);
        if (mission == null) {
            return failure(SatelliteOperationCode.MISSION_NOT_FOUND);
        }
        if (celestialCatalogs.current().flatMap(catalog -> catalog.get(mission.targetBodyId())).isEmpty()) {
            return result(SatelliteOperationCode.CATALOG_UNAVAILABLE, false, mission);
        }
        CelestialSavedData celestial = CelestialSavedData.get(server);
        CelestialSavedData.MutationResult discovery = celestial.discover(
                mission.targetBodyId(),
                server.overworld().getGameTime()
        );
        if (discovery == CelestialSavedData.MutationResult.UNSUPPORTED_SCHEMA
                || discovery == CelestialSavedData.MutationResult.CAPACITY_REACHED) {
            return result(SatelliteOperationCode.PENDING_DISCOVERY, false, mission);
        }
        SatelliteOperationResult finished = missions.finishDiscovery(missionId);
        if (finished.changed() || discovery == CelestialSavedData.MutationResult.CHANGED) {
            missions.flush(server);
            snapshots.sendAll(server);
        }
        return finished;
    }

    private SatelliteOperationResult claimMission(
            MinecraftServer server,
            UUID missionId,
            UUID ownerId
    ) {
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteOperationResult result = data.claim(
                    missionId,
                    ownerId,
                    server.overworld().getGameTime()
            );
            if (result.code() == SatelliteOperationCode.PENDING_DISCOVERY) {
                result = applyDiscovery(server, missionId);
            }
            if (result.changed() || data.isDirty()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            logOperationFailure("claim", exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    private boolean discoveryRequired(MinecraftServer server, ResourceLocation targetBodyId) {
        return CelestialSavedData.get(server).get(targetBodyId).isEmpty();
    }

    private SatelliteOperationResult validatePlayerIdentity(
            ServerPlayer player,
            SatelliteIdentity identity
    ) {
        if (!identity.ownerId().equals(player.getUUID())) {
            return failure(SatelliteOperationCode.UNAUTHORIZED);
        }
        return null;
    }

    private SatelliteOperationResult validateDefinitionAndTarget(
            SatelliteDefinition definition,
            ResourceLocation targetBodyId
    ) {
        if (definition == null) {
            return failure(satelliteCatalogs.current().isEmpty()
                    ? SatelliteOperationCode.CATALOG_UNAVAILABLE
                    : SatelliteOperationCode.DEFINITION_NOT_FOUND);
        }
        if (targetBodyId == null || !definition.allows(targetBodyId)) {
            return failure(SatelliteOperationCode.TARGET_NOT_ALLOWED);
        }
        if (celestialCatalogs.current().flatMap(catalog -> catalog.get(targetBodyId)).isEmpty()) {
            return failure(SatelliteOperationCode.TARGET_NOT_ALLOWED);
        }
        return null;
    }

    private static SatelliteOperationResult failure(SatelliteOperationCode code) {
        return new SatelliteOperationResult(code, false, Optional.empty(), Optional.empty(), 0);
    }

    private SatelliteOperationResult result(
            SatelliteOperationCode code,
            boolean changed,
            MissionState mission
    ) {
        return new SatelliteOperationResult(
                code,
                changed,
                Optional.empty(),
                Optional.of(mission),
                0
        );
    }

    private static void logOperationFailure(String operation, RuntimeException exception) {
        AdvancedRocketryCommunity.LOGGER.error("Satellite {} operation failed", operation, exception);
    }
}

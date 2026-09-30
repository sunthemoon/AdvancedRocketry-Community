package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotSynchronizer;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.scan.SurveyScanService;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

/** Server-thread authority joining definitions, missions, research, and discovery. */
public final class SatelliteManager {
    private static final String INITIAL_MISSION_PREFIX = "arce:data-satellite:first:";
    private static final String RELEASE_TEST_HOOK_PROPERTY =
            "advancedrocketrycommunity.releaseTestHooks";
    private static final int MAX_RELEASE_TEST_BATCH = 100;

    private final SatelliteCatalogManager satelliteCatalogs;
    private final CelestialCatalogManager celestialCatalogs;
    private final Consumer<MinecraftServer> snapshots;
    private final ReceiverDirectory receivers = new ReceiverDirectory();
    private final SolarLinks links;
    private final SatelliteKindLifecycle kinds;
    private final SurveyScanService scans;
    private final IntentRateLimiter intents = new IntentRateLimiter();
    private static final int COALESCED_FLUSH_TICKS = 100;
    private int lastCoalescedFlush = Integer.MIN_VALUE / 2;
    private long coalescedFlushes;
    private long coalescedFlushNanos;
    private long coalescedFlushMaxNanos;
    /**
     * Satellite post-tick work per server tick, indexed like {@code MinecraftServer.tickTimes}. Forge fires the
     * post-tick event after vanilla records the tick time, so this work is not in vanilla MSPT (C9 measurement).
     */
    private final long[] postTickNanos = new long[100];
    private boolean coalescedFailureReported;
    private final DiscoveryReplayQueue pendingDiscoveryReplay = new DiscoveryReplayQueue();
    private boolean replayInitialized;
    private int replayBackoffTicks;
    private boolean replayFailureReported;

    public SatelliteManager(
            SatelliteCatalogManager satelliteCatalogs,
            CelestialCatalogManager celestialCatalogs,
            CelestialSnapshotSynchronizer snapshots
    ) {
        this(satelliteCatalogs, celestialCatalogs, snapshots::sendAll);
    }

    SatelliteManager(SatelliteCatalogManager satelliteCatalogs, CelestialCatalogManager celestialCatalogs,
            Consumer<MinecraftServer> snapshots) {
        this.satelliteCatalogs = Objects.requireNonNull(satelliteCatalogs, "satelliteCatalogs");
        this.celestialCatalogs = Objects.requireNonNull(celestialCatalogs, "celestialCatalogs");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.links = new SolarLinks(celestialCatalogs, receivers);
        this.kinds = new SatelliteKindLifecycle(satelliteCatalogs, celestialCatalogs, links);
        this.scans = SurveyScanService.create(celestialCatalogs, CommonConfig::surveyScanSettings);
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
        long started = System.nanoTime();
        try {
            tickEnd(event.getServer());
        } finally {
            postTickNanos[Math.floorMod(event.getServer().getTickCount(), postTickNanos.length)] =
                    System.nanoTime() - started;
        }
    }

    /** The satellite post-tick work of the last 100 ticks, indexed like {@code MinecraftServer.tickTimes}. */
    public long[] postTickNanos() {
        return postTickNanos.clone();
    }

    private void tickEnd(MinecraftServer server) {
        try {
            scans.tick(server);
        } catch (RuntimeException exception) {
            // C7-M3: the service drops a failing job itself; this guards the tick against anything else.
            logOperationFailure("survey scan tick", exception);
        }
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
            if (!data.operational()) {
                // A blocked registry (ADR-050 section 9) was reported when it loaded; nothing is scheduled.
                return;
            }
            data.applyLimits(CommonConfig.registryLimits());
            io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SchedulerPass pass = data.completeDue(gameTime);
            if (pass.changed()) {
                // ADR-050 section 2: completions and pruning wait for the coalesced flush below.
                AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_SATELLITE_SCHEDULER completed={} pruned={} inspected={} remaining={}",
                        pass.completed(), pass.pruned(), pass.inspectedEntries(), pass.remainingScheduled()
                );
            }
            coalescedFlush(server, data);
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error("Satellite scheduler pass failed", exception);
        }
    }

    /**
     * ADR-050 section 2: while a state change is pending, one flush runs at most every 100 ticks, on top of the
     * barrier flushes. A failing flush keeps the change pending and is reported once until a flush succeeds.
     */
    private void coalescedFlush(MinecraftServer server, SatelliteMissionSavedData data) {
        int now = server.getTickCount();
        if (!data.flushPending() || now - lastCoalescedFlush < COALESCED_FLUSH_TICKS) {
            return;
        }
        lastCoalescedFlush = now;
        try {
            long started = System.nanoTime();
            data.flush(server);
            long elapsed = System.nanoTime() - started;
            coalescedFlushNanos += elapsed;
            coalescedFlushMaxNanos = Math.max(coalescedFlushMaxNanos, elapsed);
            coalescedFlushes++;
            coalescedFailureReported = false;
        } catch (RuntimeException exception) {
            if (!coalescedFailureReported) {
                logOperationFailure("coalesced flush (kept pending)", exception);
                coalescedFailureReported = true;
            }
        }
    }

    /** Coalesced flushes since the server started (ADR-050 section 2, reported by C9). */
    public long coalescedFlushes() {
        return coalescedFlushes;
    }

    /** Mean and maximum coalesced flush time in milliseconds since the server started (C9 cost measurement). */
    public String coalescedFlushTimes() {
        return String.format(java.util.Locale.ROOT, "coalesced_flush_mean_ms=%.3f coalesced_flush_max_ms=%.3f",
                coalescedFlushes == 0 ? 0.0D : coalescedFlushNanos / 1_000_000.0D / coalescedFlushes,
                coalescedFlushMaxNanos / 1_000_000.0D);
    }

    /**
     * ADR-049 section 10 / ADR-050 section 6: one state-changing intent per player per 10 ticks and one
     * selection intent per 2 ticks (COMMON config may lengthen both). Applied where client intents arrive.
     */
    public boolean allowIntent(ServerPlayer player, boolean selection) {
        MinecraftServer server = player.getServer();
        return server == null || intents.allow(player.getUUID(), selection, server.getTickCount(),
                CommonConfig.registryLimits());
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
        if (identity.kind() != SatelliteKind.DATA) {
            return kinds.launchIdle(player, identity, targetBodyId);
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
            if (result.changed() || data.isDirty()) {
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
        if (identity.kind() != SatelliteKind.DATA) {
            // A data mission needs a data satellite; resource missions have their own starts (ADR-051).
            return failure(SatelliteOperationCode.DEFINITION_NOT_FOUND);
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
            if (result.changed() || data.isDirty()) {
                data.flush(server);
            }
            return result;
        } catch (RuntimeException exception) {
            logOperationFailure("start", exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    /** ADR-049 section 7: see {@link SatelliteKindLifecycle#decommission}. */
    public SatelliteOperationResult decommission(ServerPlayer player, SatelliteIdentity identity, boolean operator) {
        return kinds.decommission(player, identity, operator);
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
        SatelliteDefinition definition = definition(SatelliteIds.DATA_SATELLITE).orElse(null);
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
            if (result.changed() || data.isDirty()) {
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

    /** Bounded packaged-server stress hook using the production registry and scheduler. */
    public ReleaseTestBatchResult releaseTestBatch(MinecraftServer server, int requested) {
        return releaseTestBatch(server, requested, 2);
    }

    /**
     * The same, spreading the missions over {@code owners} stress owners. Two owners keep the historical v0.8
     * owner keys; more owners stay within the ADR-050 per-owner limits for the C9 500/1,000-mission loads.
     */
    public ReleaseTestBatchResult releaseTestBatch(MinecraftServer server, int requested, int owners) {
        if (!Boolean.getBoolean(RELEASE_TEST_HOOK_PROPERTY)
                || requested < 1 || requested > MAX_RELEASE_TEST_BATCH) {
            return new ReleaseTestBatchResult(
                    SatelliteOperationCode.UNAUTHORIZED, requested, 0, requested, 0L
            );
        }
        SatelliteDefinition definition = definition(SatelliteIds.DATA_SATELLITE).orElse(null);
        if (definition == null || definition.allowedTargets().isEmpty()) {
            return new ReleaseTestBatchResult(
                    SatelliteOperationCode.CATALOG_UNAVAILABLE, requested, 0, requested, 0L
            );
        }
        long started = System.nanoTime();
        int created = 0;
        try {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            long gameTime = server.overworld().getGameTime();
            for (int index = 0; index < requested; index++) {
                UUID satelliteId = UUID.randomUUID();
                String ownerKey = owners == 2 ? "arce:v080:stress-owner:" + (index % 2)
                        : "arce:v160:stress-owner:" + (index % owners);
                UUID ownerId = UUID.nameUUIDFromBytes(ownerKey.getBytes(StandardCharsets.UTF_8));
                ResourceLocation target = definition.allowedTargets().get(
                        index % definition.allowedTargets().size()
                );
                SatelliteOperationResult result = data.launch(
                        satelliteId,
                        initialMissionId(satelliteId),
                        ownerId,
                        definition,
                        target,
                        gameTime,
                        discoveryRequired(server, target)
                );
                if (result.changed()) {
                    created++;
                }
            }
            if (created > 0) {
                data.flush(server);
            }
            return new ReleaseTestBatchResult(
                    created == requested
                            ? SatelliteOperationCode.SUCCESS
                            : SatelliteOperationCode.CAPACITY_REACHED,
                    requested,
                    created,
                    requested - created,
                    System.nanoTime() - started
            );
        } catch (RuntimeException exception) {
            logOperationFailure("release-test batch", exception);
            return new ReleaseTestBatchResult(
                    SatelliteOperationCode.UNSUPPORTED_DATA,
                    requested,
                    created,
                    requested - created,
                    System.nanoTime() - started
            );
        }
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
            if (dataMission(result) && (result.changed() || data.isDirty())) {
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
            if (dataMission(result) && (result.changed() || data.isDirty())) {
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

    /** Mission targets of a data definition, or launch targets of a kind definition. */
    public List<ResourceLocation> targets(ResourceLocation definitionId) {
        Optional<SatelliteDefinition> data = definition(definitionId);
        if (data.isPresent()) {
            return data.orElseThrow().allowedTargets();
        }
        return satelliteCatalogs.current()
                .flatMap(catalog -> catalog.kindDefinition(definitionId))
                .map(SatelliteKindDefinition::launchTargets)
                .orElse(List.of());
    }

    public Optional<SatelliteCatalog> catalog() { return satelliteCatalogs.current(); }

    public long catalogGeneration() { return satelliteCatalogs.status().generation(); }

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

    public long lifetimeResearch(MinecraftServer server, UUID ownerId) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        return data.operational() ? data.account(ownerId).lifetimeEarned() : 0L;
    }

    public boolean discovered(MinecraftServer server, ResourceLocation targetBodyId) {
        return CelestialSavedData.get(server).get(targetBodyId).isPresent();
    }

    /** ADR-049 section 8: a survey scan requested with the bound chip in hand. */
    public SatelliteOperationCode requestScan(ServerPlayer player, SatelliteIdentity identity) {
        try {
            return scans.request(player, identity);
        } catch (RuntimeException exception) {
            logOperationFailure("survey scan", exception);
            return SatelliteOperationCode.UNSUPPORTED_DATA;
        }
    }

    /** ADR-049 section 9: the 20-tick check of one loaded receiver. */
    public SolarLinks.ReceiverCheck checkReceiver(MinecraftServer server, UUID receiverId,
                                                  List<Optional<SatelliteIdentity>> chips) {
        try {
            return links.check(server, receiverId, chips);
        } catch (RuntimeException exception) {
            logOperationFailure("receiver check", exception);
            return SolarLinks.unavailable(chips);
        }
    }

    public void registerReceiver(UUID receiverId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level,
                                 net.minecraft.core.BlockPos position) {
        receivers.register(receiverId, level, position);
    }

    public void releaseReceiver(MinecraftServer server, UUID receiverId) {
        try {
            links.release(server, receiverId);
        } catch (RuntimeException exception) {
            logOperationFailure("receiver release", exception);
        }
    }

    public SatelliteOperationResult unlink(ServerPlayer player, SatelliteIdentity identity, boolean operator) {
        return links.unlink(player, identity, operator);
    }

    public SatelliteOperationResult unlinkAdmin(MinecraftServer server, UUID satelliteId, UUID actor) {
        return links.unlinkAdmin(server, satelliteId, actor);
    }

    public boolean scanRunning(UUID playerId) {
        return scans.running(playerId);
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        scans.cancel(event.getEntity().getUUID());
        intents.forget(event.getEntity().getUUID());
    }

    public Optional<CelestialCatalog> celestialCatalog() {
        return celestialCatalogs.current();
    }

    public void clear() {
        scans.clear();
        intents.clear();
        lastCoalescedFlush = Integer.MIN_VALUE / 2;
        coalescedFlushes = 0L;
        coalescedFlushNanos = 0L;
        coalescedFlushMaxNanos = 0L;
        coalescedFailureReported = false;
        receivers.clear();
        pendingDiscoveryReplay.clear();
        replayInitialized = false;
        replayBackoffTicks = 0;
        replayFailureReported = false;
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
            CelestialSavedData progress = CelestialSavedData.get(server);
            data.missions().stream()
                    .filter(DiscoveryClaimRecovery::paidReceipt)
                    .filter(mission -> mission.status() == MissionStatus.CLAIM_PENDING_DISCOVERY
                            || progress.get(mission.targetBodyId()).isEmpty())
                    .map(MissionState::missionId)
                    .forEach(pendingDiscoveryReplay::add);
        }
        replayInitialized = true;
    }

    private void replayDiscoveries(MinecraftServer server) {
        if (replayBackoffTicks > 0) { replayBackoffTicks--; return; }
        try {
            pendingDiscoveryReplay.drain(id -> !needsDiscoveryRetry(applyDiscovery(server, id)));
            if (pendingDiscoveryReplay.size() == 0) { replayFailureReported = false; }
        } catch (RuntimeException exception) {
            replayBackoffTicks = SatelliteLimits.SCHEDULER_INTERVAL_TICKS;
            if (!replayFailureReported) {
                logOperationFailure("discovery recovery (retained for retry)", exception);
                replayFailureReported = true;
            }
        }
    }

    private SatelliteOperationResult applyDiscovery(MinecraftServer server, UUID missionId) {
        SatelliteMissionSavedData missions = SatelliteMissionSavedData.get(server);
        CelestialSavedData celestial = CelestialSavedData.get(server);
        var before = celestial.entries();
        try {
            return DiscoveryClaimRecovery.recover(missions, celestial, missionId, server.overworld().getGameTime(),
                    id -> celestialCatalogs.current().flatMap(catalog -> catalog.get(id)).isPresent(),
                    data -> data.flush(server), data -> data.flush(server));
        } finally {
            // Discovery is already durable even if the final mission acknowledgment fails.
            if (!before.equals(celestial.entries())) { snapshots.accept(server); }
        }
    }

    private static boolean needsDiscoveryRetry(SatelliteOperationResult result) {
        return result.code() == SatelliteOperationCode.PENDING_DISCOVERY
                || result.code() == SatelliteOperationCode.CATALOG_UNAVAILABLE;
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
            if (result.code() == SatelliteOperationCode.PENDING_DISCOVERY
                    || result.code() == SatelliteOperationCode.ALREADY_CLAIMED
                    && result.mission().filter(DiscoveryClaimRecovery::paidReceipt).isPresent()) {
                pendingDiscoveryReplay.add(missionId);
                result = applyDiscovery(server, missionId);
                if (!needsDiscoveryRetry(result)) { pendingDiscoveryReplay.remove(missionId); }
            }
            if (data.isDirty()) {
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

    /** ADR-050 section 2: only the existing {@code data} paths keep their barrier; v1.6 kinds wait for the coalesced flush. */
    private static boolean dataMission(SatelliteOperationResult result) {
        return result.mission().map(mission -> mission.kind() == MissionKind.DATA).orElse(true);
    }

    static SatelliteOperationResult failure(SatelliteOperationCode code) {
        return new SatelliteOperationResult(code, false, Optional.empty(), Optional.empty(), 0);
    }

    static void logOperationFailure(String operation, RuntimeException exception) {
        AdvancedRocketryCommunity.LOGGER.error("Satellite {} operation failed", operation, exception);
    }

    public record ReleaseTestBatchResult(
            SatelliteOperationCode code,
            int requested,
            int created,
            int rejected,
            long elapsedNanos
    ) {
    }
}

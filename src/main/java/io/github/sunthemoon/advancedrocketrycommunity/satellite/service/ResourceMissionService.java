package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.DeliveryReconciliation;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.ResourceMissions;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.AsteroidType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.GasTable;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.MissionSeeds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceTables;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * ADR-051/052 server adapter for survey, asteroid and gas missions: resolves the craft's system and tables,
 * generates rewards from server-owned seeds, applies the registry operation and writes the delivery audit
 * lines. Starts, claims and cancels wait for the coalesced flush; only the REBIND_CONFLICT bind-back is a
 * barrier (ADR-050 section 2). Main thread only.
 */
public final class ResourceMissionService {
    private static final int MAX_REMEMBERED_RECONCILIATIONS = 1_024;

    private final SatelliteCatalogManager satelliteCatalogs;
    private final CelestialCatalogManager celestialCatalogs;
    private final ResourceTableReloadListener.Manager tables;
    private final MissionSeeds seeds = new MissionSeeds();
    private final TerminalDirectory directory = new TerminalDirectory();
    private final TerminalObservations observations = new TerminalObservations();
    private final Map<UUID, String> lastReconciliation = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, String> eldest) {
            return size() > MAX_REMEMBERED_RECONCILIATIONS;
        }
    };

    public ResourceMissionService(SatelliteCatalogManager satelliteCatalogs, CelestialCatalogManager celestialCatalogs,
                                  ResourceTableReloadListener.Manager tables) {
        this.satelliteCatalogs = Objects.requireNonNull(satelliteCatalogs, "satelliteCatalogs");
        this.celestialCatalogs = Objects.requireNonNull(celestialCatalogs, "celestialCatalogs");
        this.tables = Objects.requireNonNull(tables, "tables");
    }

    public TerminalDirectory directory() {
        return directory;
    }

    public TerminalObservations observations() {
        return observations;
    }

    /** ADR-051 section 3: a survey from an idle survey satellite over the system of its orbit body. */
    public SatelliteOperationResult startSurvey(ServerPlayer player, SatelliteIdentity chip) {
        return guarded("survey start", () -> {
            MinecraftServer server = player.getServer();
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteState satellite = data.satellite(chip.satelliteId()).orElse(null);
            SatelliteOperationCode refused = !chip.ownerId().equals(player.getUUID()) ? SatelliteOperationCode.UNAUTHORIZED
                    : satellite == null ? SatelliteOperationCode.SATELLITE_NOT_FOUND
                    : satellite.currentMissionId().isPresent() ? SatelliteOperationCode.MISSION_BUSY : null;
            if (refused != null) {
                return failure(refused);
            }
            ResourceLocation system = system(satellite).orElse(null);
            SatelliteKindDefinition.Survey parameters = satelliteCatalogs.current()
                    .flatMap(catalog -> catalog.kindDefinition(satellite.definitionId()))
                    .map(SatelliteKindDefinition::parameters)
                    .filter(SatelliteKindDefinition.Survey.class::isInstance)
                    .map(SatelliteKindDefinition.Survey.class::cast).orElse(null);
            ResourceTables current = tables.current().orElse(null);
            if (system == null || parameters == null || current == null) {
                return failure(system == null ? SatelliteOperationCode.BODY_UNAVAILABLE : parameters == null
                        ? SatelliteOperationCode.DEFINITION_NOT_FOUND : SatelliteOperationCode.CATALOG_UNAVAILABLE);
            }
            List<AsteroidType> candidates = ResourceAlgorithms.candidates(current.asteroidTypes(), system);
            if (candidates.isEmpty()) {
                return failure(SatelliteOperationCode.NO_ASTEROID_TYPES);
            }
            String fingerprint = ResourceAlgorithms.fingerprint(candidates);
            long seed = seeds.next();
            UUID missionId = UUID.randomUUID();
            ResourceMissions.SurveyGeneration generation = (count, createdAt) -> ResourceAlgorithms
                    .survey(seed, candidates, count).stream()
                    .map(generated -> new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, UUID.randomUUID(),
                            chip.ownerId(), system, generated.type().id(), generated.type().tableVersion(),
                            fingerprint, generated.seed(), generated.yield(), createdAt, OptionalLong.empty(),
                            InstanceState.PENDING, missionId, Optional.empty()))
                    .toList();
            return data.resources(missions -> missions.startSurvey(new ResourceMissions.SurveyStart(
                    chip.satelliteId(), missionId, chip.ownerId(), system, fingerprint,
                    parameters.instancesPerSurvey(), parameters.missionDurationTicks(), seed, generation),
                    gameTime(server)));
        });
    }

    /** ADR-051 section 4: mine one of the owner's AVAILABLE instances in the craft's current system. */
    public SatelliteOperationResult startAsteroid(ServerPlayer player, SatelliteIdentity chip, UUID instanceId,
                                                  DeliveryTerminal terminal) {
        return guarded("asteroid start", () -> {
            MinecraftServer server = player.getServer();
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteState satellite = data.satellite(chip.satelliteId()).orElse(null);
            SatelliteOperationCode refused = startRefusal(player, chip, satellite, terminal);
            if (refused != null) {
                return failure(refused);
            }
            ResourceLocation system = system(satellite).orElse(null);
            AsteroidInstance instance = instanceId == null ? null : data.instance(instanceId).orElse(null);
            AsteroidType type = instance == null ? null : tables.current()
                    .flatMap(current -> current.asteroidType(instance.asteroidType()))
                    .filter(candidate -> candidate.tableVersion().equals(instance.tableVersion())).orElse(null);
            if (system == null || instance == null || type == null) {
                return failure(system == null ? SatelliteOperationCode.BODY_UNAVAILABLE : instance == null
                        ? SatelliteOperationCode.INSTANCE_NOT_FOUND : SatelliteOperationCode.DEFINITION_NOT_FOUND);
            }
            SatelliteStats stats = satellite.blueprint().stats();
            List<RewardEntry> reward = ResourceAlgorithms.truncate(instance.yield(), stats.cargo());
            int duration = ResourceAlgorithms.asteroidDuration(type.timeMultiplierPct(),
                    CommonConfig.asteroidMissionTimePercent(), stats.rating());
            String version = ResourceAlgorithms.ASTEROID_V1 + "/" + type.id() + "/" + type.tableVersion();
            return data.resources(missions -> missions.startResource(new ResourceMissions.ResourceStart(
                    chip.satelliteId(), UUID.randomUUID(), chip.ownerId(), MissionKind.ASTEROID, system,
                    Optional.of(instance.instanceId()), reward, version, duration, seeds.next(), terminal.id(),
                    terminal.display()), gameTime(server)));
        });
    }

    /** ADR-051 section 4: harvest one product of the discovered gas giant the craft orbits. */
    public SatelliteOperationResult startGas(ServerPlayer player, SatelliteIdentity chip, ResourceLocation product,
                                             DeliveryTerminal terminal) {
        return guarded("gas start", () -> {
            MinecraftServer server = player.getServer();
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteState satellite = data.satellite(chip.satelliteId()).orElse(null);
            SatelliteOperationCode refused = startRefusal(player, chip, satellite, terminal);
            if (refused != null) {
                return failure(refused);
            }
            ResourceLocation body = satellite.orbitBody().orElse(null);
            CelestialBodyDefinition definition = body == null ? null
                    : celestialCatalogs.current().flatMap(catalog -> catalog.get(body)).orElse(null);
            if (definition == null) {
                return failure(SatelliteOperationCode.BODY_UNAVAILABLE);
            }
            if (!definition.capabilities().gasGiant() || CelestialSavedData.get(server).get(body).isEmpty()) {
                return failure(SatelliteOperationCode.TARGET_NOT_ALLOWED);
            }
            GasTable table = tables.current().flatMap(current -> current.gasTable(body)).orElse(null);
            GasTable.Product chosen = table == null ? null : table.products().stream()
                    .filter(candidate -> candidate.item().equals(product)).findFirst().orElse(null);
            if (chosen == null) {
                return failure(table == null ? SatelliteOperationCode.DEFINITION_NOT_FOUND
                        : SatelliteOperationCode.TARGET_NOT_ALLOWED);
            }
            SatelliteStats stats = satellite.blueprint().stats();
            ResourceAlgorithms.GasResult gas = ResourceAlgorithms.gas(chosen.amountPer1000Ticks(), stats.rating(),
                    stats.cargo(), CommonConfig.gasMissionTimePercent());
            String version = ResourceAlgorithms.GAS_V1 + "/" + table.id() + "/" + table.tableVersion();
            return data.resources(missions -> missions.startResource(new ResourceMissions.ResourceStart(
                    chip.satelliteId(), UUID.randomUUID(), chip.ownerId(), MissionKind.GAS, body, Optional.empty(),
                    List.of(new RewardEntry(chosen.item(), gas.amount())), version, gas.duration(), seeds.next(),
                    terminal.id(), terminal.display()), gameTime(server)));
        });
    }

    /** ADR-051 section 3: a survey is claimed at any terminal with the chip; no items move. */
    public SatelliteOperationResult claimSurvey(ServerPlayer player, SatelliteIdentity chip) {
        return guarded("survey claim", () -> {
            UUID missionId = currentMission(player, chip);
            return missionId == null ? failure(SatelliteOperationCode.MISSION_NOT_FOUND)
                    : SatelliteMissionSavedData.get(player.getServer()).resources(missions -> missions.claimSurvey(
                            missionId, player.getUUID(), CommonConfig.asteroidInstanceTtlTicks(),
                            gameTime(player.getServer())));
        });
    }

    /**
     * ADR-051 section 6: a claim at the bound terminal. {@code delivery} names the terminal's refusal (buffer or
     * receipts full) or null; on success the caller adds the reward and an unpersisted receipt in this tick.
     */
    public SatelliteOperationResult claimResource(ServerPlayer player, SatelliteIdentity chip,
                                                  DeliveryTerminal terminal,
                                                  Function<List<RewardEntry>, SatelliteOperationCode> delivery) {
        return guarded("resource claim", () -> {
            MinecraftServer server = player.getServer();
            UUID missionId = currentMission(player, chip);
            if (missionId == null) {
                return failure(SatelliteOperationCode.MISSION_NOT_FOUND);
            }
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            SatelliteOperationResult result = data.resources(missions -> missions.claimResource(missionId,
                    player.getUUID(), terminal.id(), delivery, gameTime(server)));
            if (result.code() == SatelliteOperationCode.WRONG_TERMINAL && result.mission()
                    .map(mission -> (MissionPayload.Resource) mission.payload())
                    .filter(resource -> TerminalDirectory.presence(server, resource.boundTerminal(),
                            resource.boundTerminalDisplay()) == TerminalDirectory.Presence.MISSING).isPresent()) {
                return new SatelliteOperationResult(SatelliteOperationCode.TERMINAL_MISSING, result.changed(),
                        result.satellite(), result.mission(), result.researchBalance());
            }
            if (result.success()) {
                audit(server, "CLAIM", result.mission().orElseThrow(), terminal.id(), "");
            }
            return result;
        });
    }

    /** ADR-051 section 8: an asteroid or gas cancel at this terminal, after its reconciliation. */
    public SatelliteOperationResult cancel(ServerPlayer player, SatelliteIdentity chip, boolean operator,
                                           DeliveryTerminal terminal) {
        return guarded("resource cancel", () -> {
            MinecraftServer server = player.getServer();
            SatelliteState satellite = SatelliteMissionSavedData.get(server).satellite(chip.satelliteId()).orElse(null);
            UUID missionId = satellite == null ? null : satellite.currentMissionId().orElse(null);
            if (missionId == null) {
                return failure(satellite == null ? SatelliteOperationCode.SATELLITE_NOT_FOUND
                        : SatelliteOperationCode.MISSION_NOT_FOUND);
            }
            SatelliteOperationResult result = SatelliteMissionSavedData.get(server).resources(missions -> missions.cancel(
                    missionId, player.getUUID(), operator, Optional.of(terminal.id()), gameTime(server)));
            if (result.success()) {
                audit(server, "CANCEL", result.mission().orElseThrow(), terminal.id(), operator ? "by=operator" : "");
            }
            return result;
        });
    }

    /** One ADR-051 section 7 row; the bind-back of REBIND_CONFLICT is flushed before it returns. */
    public ResourceMissions.Reconciled reconcile(MinecraftServer server, DeliveryTerminal terminal, UUID missionId,
                                                 DeliveryReconciliation.ReceiptView receipt) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        ResourceMissions.Reconciled reconciled = data.reconcile(terminal.id(), missionId, receipt, terminal.display(),
                gameTime(server));
        if (reconciled.barrier()) {
            try {
                data.flush(server);
            } catch (RuntimeException exception) {
                // C9-L2: the bind-back stays pending, so the coalesced flush retries it; the terminal keeps ticking.
                SatelliteManager.logOperationFailure("REBIND_CONFLICT barrier flush (kept pending)", exception);
            }
        }
        lastReconciliation.put(missionId, reconciled.action().name().toLowerCase(java.util.Locale.ROOT));
        String event = switch (reconciled.action()) {
            case SET_CLAIMED_PAID_HERE -> "CLAIM_RECOVERED";
            case SET_CLAIMED_PAID_HERE_BIND_BACK -> "REBIND_CONFLICT";
            case ACKNOWLEDGE -> "ACKNOWLEDGED";
            default -> null;
        };
        if (event != null) {
            audit(server, event, reconciled.mission().orElseThrow(), terminal.id(), "");
        }
        return reconciled;
    }

    /** One audit line about a mission at a terminal (the terminal reports its own drops and repayments). */
    public void audit(MinecraftServer server, String event, MissionState mission, UUID terminal, String detail) {
        long epoch = SatelliteMissionSavedData.get(server).operational()
                ? SatelliteMissionSavedData.get(server).saveEpoch() : 0L;
        List<RewardEntry> reward = mission.payload() instanceof MissionPayload.Resource resource
                ? resource.reward() : List.of();
        AdvancedRocketryCommunity.LOGGER.info(DeliveryAudit.line(event, mission.missionId(), terminal, epoch,
                mission.ownerId(), reward, detail));
    }

    /** An audit line for a mission the registry no longer holds (a dropped receipt of a pruned mission). */
    public void auditAbsent(MinecraftServer server, String event, UUID missionId, UUID terminal, UUID owner) {
        long epoch = SatelliteMissionSavedData.get(server).operational()
                ? SatelliteMissionSavedData.get(server).saveEpoch() : 0L;
        AdvancedRocketryCommunity.LOGGER.info(DeliveryAudit.line(event, missionId, terminal, epoch, owner, List.of(),
                "registry=absent"));
    }

    public Optional<String> lastReconciliation(UUID missionId) {
        return Optional.ofNullable(lastReconciliation.get(missionId));
    }

    /** The owner's AVAILABLE instances in the craft's current system (ADR-050 section 11). */
    public List<AsteroidInstance> instancesFor(MinecraftServer server, SatelliteState satellite) {
        ResourceLocation system = system(satellite).orElse(null);
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        return system == null || !data.operational() ? List.of()
                : data.resourceQuery(missions -> missions.available(satellite.ownerId(), system));
    }

    /** The gas table products of the craft's orbit body, in file order. */
    public List<GasTable.Product> productsFor(SatelliteState satellite) {
        return satellite.orbitBody().flatMap(body -> tables.current().flatMap(current -> current.gasTable(body)))
                .map(GasTable::products).orElse(List.of());
    }

    /** Claims this terminal paid that are not yet acknowledged; with the receipts, a pass's whole scope. */
    public List<UUID> awaitingDelivery(MinecraftServer server, UUID terminal) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        return data.operational() ? data.resourceQuery(missions -> missions.awaitingDelivery(terminal)) : List.of();
    }

    public List<MissionState> boundTo(MinecraftServer server, UUID terminal) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        return data.operational() ? data.resourceQuery(missions -> missions.boundTo(terminal)) : List.of();
    }

    // --- Operator commands (ADR-050 section 9, ADR-051 section 9) ---

    public SatelliteOperationResult rebind(MinecraftServer server, UUID missionId, UUID terminalId) {
        return guarded("rebind", () -> {
            SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
            TerminalDirectory.Location location = directory.loaded(terminalId).orElse(null);
            DeliveryEndpoint endpoint = directory.endpoint(server, terminalId).orElse(null);
            if (location == null || endpoint == null) {
                return failure(SatelliteOperationCode.UNLOADED_CHUNK);
            }
            if (!endpoint.terminalIdPersisted()) {
                return failure(SatelliteOperationCode.AWAITING_WORLD_SAVE);
            }
            UUID previous = data.mission(missionId).map(MissionState::payload)
                    .filter(MissionPayload.Resource.class::isInstance)
                    .map(payload -> ((MissionPayload.Resource) payload).boundTerminal()).orElse(null);
            DeliveryTerminal target = new DeliveryTerminal(terminalId, true, location.level(), location.pos());
            SatelliteOperationResult result = data.resources(missions -> missions.rebind(missionId, terminalId,
                    target.display(), gameTime(server)));
            if (result.success()) {
                audit(server, "REBIND", result.mission().orElseThrow(), terminalId, "old=" + previous);
            }
            return result;
        });
    }

    public SatelliteOperationResult purge(MinecraftServer server, UUID missionId) {
        return guarded("purge", () -> {
            SatelliteOperationResult result = SatelliteMissionSavedData.get(server)
                    .resources(missions -> missions.purge(missionId));
            if (result.success()) {
                MissionState purged = result.mission().orElseThrow();
                audit(server, "PURGE", purged, ((MissionPayload.Resource) purged.payload()).paidTerminal().orElse(null),
                        "note=reward_lost_if_never_materialized");
            }
            return result;
        });
    }

    public SatelliteOperationResult releaseInstance(MinecraftServer server, UUID instanceId) {
        return guarded("instance release", () -> SatelliteMissionSavedData.get(server).resources(missions ->
                missions.releaseInstance(instanceId, CommonConfig.asteroidInstanceTtlTicks(), gameTime(server))));
    }

    public void clear() {
        directory.clear();
        observations.clear();
        lastReconciliation.clear();
    }

    /** ADR-043 system of the craft's orbit body, from the current tree (ADR-050 section 11). */
    private Optional<ResourceLocation> system(SatelliteState satellite) {
        return satellite.orbitBody().flatMap(body -> celestialCatalogs.current()
                .flatMap(catalog -> catalog.systemOf(body)));
    }

    private SatelliteOperationCode startRefusal(ServerPlayer player, SatelliteIdentity chip,
                                                SatelliteState satellite, DeliveryTerminal terminal) {
        return !chip.ownerId().equals(player.getUUID()) ? SatelliteOperationCode.UNAUTHORIZED
                : satellite == null ? SatelliteOperationCode.SATELLITE_NOT_FOUND
                : satellite.currentMissionId().isPresent() ? SatelliteOperationCode.MISSION_BUSY
                // C9-L4 (ADR-050 section 11): a removed or changed kind definition refuses new starts.
                : satelliteCatalogs.current().flatMap(catalog -> catalog.kindDefinition(satellite.definitionId()))
                        .filter(definition -> definition.kind() == satellite.kind()).isEmpty()
                ? SatelliteOperationCode.DEFINITION_NOT_FOUND
                : !terminal.persisted() ? SatelliteOperationCode.AWAITING_WORLD_SAVE
                : satellite.blueprint().stats().cargo() < 1 || satellite.blueprint().stats().rating() < 1
                ? SatelliteOperationCode.INVALID_COMPONENTS : null;
    }

    private static UUID currentMission(ServerPlayer player, SatelliteIdentity chip) {
        if (!chip.ownerId().equals(player.getUUID())) {
            return null;
        }
        return SatelliteMissionSavedData.get(player.getServer()).satellite(chip.satelliteId())
                .flatMap(SatelliteState::currentMissionId).orElse(null);
    }

    private static long gameTime(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    private static SatelliteOperationResult guarded(String operation, Supplier<SatelliteOperationResult> action) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            SatelliteManager.logOperationFailure(operation, exception);
            return failure(SatelliteOperationCode.UNSUPPORTED_DATA);
        }
    }

    private static SatelliteOperationResult failure(SatelliteOperationCode code) {
        return SatelliteManager.failure(code);
    }
}

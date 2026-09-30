package io.github.sunthemoon.advancedrocketrycommunity.satellite.scan;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SatelliteNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.network.SurveyScanResultPacket;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * ADR-049 section 8 survey area scans: one job per player, a server-wide job limit, a per-player cooldown and
 * a per-tick read budget, all on the main thread. A job is cancelled when its player logs out, changes Level
 * or moves more than 64 blocks; a paid scan is not refunded. Runtime state only, cleared with the server.
 */
public final class SurveyScanService {
    public static final double MAX_DRIFT_BLOCKS = 64.0D;

    private final Supplier<Optional<CelestialCatalog>> celestialCatalogs;
    private final Supplier<ScanSettings> settings;
    private final BiConsumer<ServerPlayer, SurveyScanResultPacket> sender;
    private final Map<UUID, ActiveScan> jobs = new LinkedHashMap<>();
    private final Map<UUID, Integer> cooldownUntil = new HashMap<>();

    public SurveyScanService(
            Supplier<Optional<CelestialCatalog>> celestialCatalogs,
            Supplier<ScanSettings> settings,
            BiConsumer<ServerPlayer, SurveyScanResultPacket> sender
    ) {
        this.celestialCatalogs = Objects.requireNonNull(celestialCatalogs, "celestialCatalogs");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    /** The production service: current celestial catalog, COMMON config limits, the satellite channel. */
    public static SurveyScanService create(CelestialCatalogManager celestialCatalogs, Supplier<ScanSettings> settings) {
        return new SurveyScanService(celestialCatalogs::current, settings, SatelliteNetwork::sendScanResult);
    }

    /** Validates a scan request with the chip in hand, pays it and starts the job. */
    public SatelliteOperationCode request(ServerPlayer player, SatelliteIdentity identity) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return SatelliteOperationCode.SERVER_ERROR;
        }
        boolean operator = player.hasPermissions(2);
        if (identity.kind() != SatelliteKind.SURVEY) {
            return SatelliteOperationCode.DEFINITION_NOT_FOUND;
        }
        if (!operator && !identity.ownerId().equals(player.getUUID())) {
            return SatelliteOperationCode.UNAUTHORIZED;
        }
        ScanSettings limits = settings.get();
        int now = server.getTickCount();
        if (jobs.containsKey(player.getUUID()) || now < cooldownUntil.getOrDefault(player.getUUID(), Integer.MIN_VALUE)) {
            return SatelliteOperationCode.RATE_LIMITED;
        }
        if (jobs.size() >= limits.jobLimit()) {
            return SatelliteOperationCode.CAPACITY_REACHED;
        }
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        SatelliteState satellite = data.satellite(identity.satelliteId()).orElse(null);
        if (satellite == null) {
            return SatelliteOperationCode.SATELLITE_NOT_FOUND;
        }
        if (!(satellite.kindState() instanceof SatelliteKindState.Survey) || satellite.orbitBody().isEmpty()) {
            return SatelliteOperationCode.DEFINITION_NOT_FOUND;
        }
        CelestialCatalog catalog = celestialCatalogs.get().orElse(null);
        CelestialBodyDefinition body = catalog == null ? null : catalog.get(satellite.orbitBody().orElseThrow()).orElse(null);
        if (body == null) {
            return SatelliteOperationCode.BODY_UNAVAILABLE;
        }
        boolean underOrbit = catalog.forLevel(player.level().dimension())
                .map(CelestialBodyDefinition::id).filter(body.id()::equals).isPresent();
        if (!underOrbit) {
            return SatelliteOperationCode.TARGET_NOT_ALLOWED;
        }
        SatelliteOperationResult paid = data.payScan(identity.satelliteId(), player.getUUID(), operator,
                server.overworld().getGameTime());
        if (!paid.success()) {
            return paid.code();
        }
        SatelliteKindState.Survey survey = (SatelliteKindState.Survey) paid.satellite().orElseThrow().kindState();
        Level level = player.level();
        SurveyScanJob job = new SurveyScanJob(player.getBlockX(), player.getBlockZ(), survey.scanRadius(),
                survey.scanCell(), level.getMinBuildHeight(), level.getMaxBuildHeight());
        jobs.put(player.getUUID(), new ActiveScan(player, level.dimension(), player.position(), job));
        cooldownUntil.put(player.getUUID(), now + limits.cooldownTicks());
        return SatelliteOperationCode.SUCCESS;
    }

    /** Advances every job by at most the read budget; sends each finished grid to its player. */
    public void tick(MinecraftServer server) {
        int now = server.getTickCount();
        cooldownUntil.values().removeIf(until -> until <= now);
        if (jobs.isEmpty()) {
            return;
        }
        int budget = settings.get().readsPerTick();
        for (Map.Entry<UUID, ActiveScan> entry : new ArrayList<>(jobs.entrySet())) {
            ActiveScan scan = entry.getValue();
            ServerPlayer player = scan.player();
            // A logout or respawn retires this player object; a dimension change keeps it but changes Level.
            if (player.isRemoved() || player.hasDisconnected() || player.getServer() != server) {
                jobs.remove(entry.getKey());
                continue;
            }
            if (player.level().dimension() != scan.level()
                    || player.position().distanceTo(scan.start()) > MAX_DRIFT_BLOCKS) {
                jobs.remove(entry.getKey());
                player.displayClientMessage(Component.translatable(
                        "status.advancedrocketrycommunity.survey_scan.cancelled"), true);
                continue;
            }
            if (scan.job().step(new LevelScanSource(player.serverLevel()), budget)) {
                jobs.remove(entry.getKey());
                sender.accept(player, scan.job().result());
                player.displayClientMessage(Component.translatable(
                        "status.advancedrocketrycommunity.survey_scan.complete"), true);
            }
        }
    }

    /** A logged-out player's job ends at once; its cooldown stays. */
    public void cancel(UUID playerId) {
        jobs.remove(playerId);
    }

    public int activeJobs() {
        return jobs.size();
    }

    public boolean running(UUID playerId) {
        return jobs.containsKey(playerId);
    }

    public void clear() {
        jobs.clear();
        cooldownUntil.clear();
    }

    private record ActiveScan(ServerPlayer player, ResourceKey<Level> level, Vec3 start, SurveyScanJob job) {
    }
}

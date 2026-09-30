package io.github.sunthemoon.advancedrocketrycommunity.station.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Thin Forge adapter for ADR-045: builds the validator's snapshot from live server state, reads only,
 * and writes one audit line. It never loads a chunk, marks SavedData dirty or persists anything.
 */
public final class ElevatorEndpointService {
    private final CelestialCatalogManager catalogs;

    public ElevatorEndpointService(CelestialCatalogManager catalogs) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
    }

    public ElevatorEndpointValidator.Result check(MinecraftServer server, UUID requesterId, boolean operator,
                                                  ElevatorEndpointValidator.Request request) {
        Objects.requireNonNull(server, "server");
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        ElevatorEndpointValidator.Snapshot snapshot = new ElevatorEndpointValidator.Snapshot(
                data.updatesAvailable(),
                data.find(request.stationId()).map(ElevatorEndpointService::view),
                requesterId,
                operator,
                catalogs.current(),
                level -> server.getLevel(level) != null,
                (level, x, z) -> {
                    ServerLevel target = server.getLevel(level);
                    // The border is plain Level state; reading it never touches a chunk.
                    return target != null && target.getWorldBorder().isWithinBounds(new BlockPos(x, 0, z));
                });
        ElevatorEndpointValidator.Result result = ElevatorEndpointValidator.validate(request, snapshot);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_ELEVATOR_CHECK actor={} operator={} station={} body={} x={} z={} rule={} result={}",
                requesterId, operator, request.stationId(), request.bodyId(), request.x(), request.z(),
                result.code().rule(), result.code());
        return result;
    }

    private static ElevatorEndpointValidator.StationView view(StationState station) {
        return new ElevatorEndpointValidator.StationView(station.stationId(), station.ownerId(), station.orbitBody(),
                station.landingPad().x(), station.landingPad().z());
    }
}

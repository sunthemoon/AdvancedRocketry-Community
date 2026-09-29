package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Local owner/operator expansion of a station's permission region from 512 to 768 blocks.
 * Only claim bounds change: no blocks are read, moved or removed and no chunk is loaded.
 */
public final class StationExpansionService {
    private final StationAccessService access;
    private final StationExpansionConfirmations confirmations = new StationExpansionConfirmations();

    public StationExpansionService(StationAccessService access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    /** Checks the actor against the station at their position; {@code null} means allowed. */
    public static StationExpansionCode check(
            StationAccessService access,
            boolean authorityAvailable,
            boolean inSpace,
            boolean chunkLoaded,
            Optional<StationState> stationAtPosition,
            UUID actorId,
            boolean operator
    ) {
        if (!authorityAvailable) {
            return StationExpansionCode.AUTHORITY_UNAVAILABLE;
        }
        if (!inSpace) {
            return StationExpansionCode.NOT_IN_SPACE;
        }
        if (!chunkLoaded) {
            return StationExpansionCode.CHUNK_UNLOADED;
        }
        if (stationAtPosition.isEmpty()) {
            return StationExpansionCode.NOT_IN_STATION;
        }
        StationState station = stationAtPosition.orElseThrow();
        if (!access.allowed(station, actorId, operator, StationAccessAction.EXPAND)) {
            return StationExpansionCode.UNAUTHORIZED;
        }
        return station.expanded() ? StationExpansionCode.ALREADY_EXPANDED : null;
    }

    public StationExpansionResult request(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        Located located = locate(player);
        if (located.code() != null) {
            return audit("request", player, StationExpansionResult.of(located.code(), located.station()));
        }
        MinecraftServer server = player.getServer();
        boolean issued = confirmations.issue(player.getUUID(), located.station(), located.authority(),
                server.getTickCount());
        return audit("request", player, StationExpansionResult.of(
                issued ? StationExpansionCode.ISSUED : StationExpansionCode.CAPACITY_REACHED, located.station()));
    }

    public StationExpansionResult confirm(ServerPlayer player, UUID stationId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stationId, "stationId");
        MinecraftServer server = player.getServer();
        if (server == null) {
            return StationExpansionResult.failure(StationExpansionCode.AUTHORITY_UNAVAILABLE);
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        var outcome = confirmations.take(player.getUUID(), stationId, data, server.getTickCount());
        StationExpansionCode rejected = switch (outcome.status()) {
            case READY -> null;
            case MISSING -> StationExpansionCode.NO_CONFIRMATION;
            case EXPIRED -> StationExpansionCode.CONFIRMATION_EXPIRED;
            case MISMATCH -> StationExpansionCode.CONFIRMATION_MISMATCH;
        };
        if (rejected != null) {
            return audit("confirm", player, StationExpansionResult.failure(rejected));
        }
        // Recheck permission, position, loaded chunk and authority against current state.
        Located located = locate(player);
        if (located.code() != null) {
            return audit("confirm", player, StationExpansionResult.of(located.code(), located.station()));
        }
        StationState observed = outcome.observed();
        if (located.authority() != data || !observed.equals(located.station())) {
            return audit("confirm", player, StationExpansionResult.of(
                    StationExpansionCode.STATION_CHANGED, located.station()));
        }
        StationRegistrySavedData.CheckedExpansion written;
        try {
            written = data.checkedExpand(server, observed);
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_EXPANSION_WRITE_FAILED station={} stage=candidate", stationId, exception);
            written = StationRegistrySavedData.CheckedExpansion.WRITE_FAILED;
        }
        StationExpansionCode code = switch (written) {
            case EXPANDED -> StationExpansionCode.EXPANDED;
            case ALREADY_EXPANDED -> StationExpansionCode.ALREADY_EXPANDED;
            case STALE -> StationExpansionCode.STATION_CHANGED;
            case UNAVAILABLE -> StationExpansionCode.AUTHORITY_UNAVAILABLE;
            case WRITE_FAILED -> StationExpansionCode.WRITE_FAILED;
            case OUTCOME_UNKNOWN -> StationExpansionCode.OUTCOME_UNKNOWN;
        };
        return audit("confirm", player, StationExpansionResult.of(code,
                code == StationExpansionCode.EXPANDED ? data.find(stationId).orElse(null) : observed));
    }

    public void forget(UUID playerId) {
        confirmations.clear(playerId);
    }

    public void clear() {
        confirmations.clear();
    }

    private Located locate(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return new Located(StationExpansionCode.AUTHORITY_UNAVAILABLE, null, null);
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        ServerLevel level = player.serverLevel();
        boolean inSpace = level.dimension().equals(CelestialIds.SPACE_LEVEL)
                && server.getLevel(CelestialIds.SPACE_LEVEL) == level;
        BlockPos position = player.blockPosition();
        // hasChunkAt only inspects already-loaded chunks; it never loads or generates one.
        boolean chunkLoaded = inSpace && level.hasChunkAt(position);
        Optional<StationState> station = inSpace
                ? data.findAt(position.getX(), position.getZ())
                : Optional.empty();
        StationExpansionCode code = check(access, data.operational() && !data.expansionQuarantined(),
                inSpace, chunkLoaded, station, player.getUUID(), player.hasPermissions(2));
        return new Located(code, station.orElse(null), data);
    }

    private static StationExpansionResult audit(String action, ServerPlayer player, StationExpansionResult result) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_EXPANSION action={} code={} station={} actor={} width={}",
                action,
                result.code(),
                result.station().map(StationState::stationId).orElse(null),
                player.getUUID(),
                result.station().map(station -> station.region().width()).orElse(null)
        );
        return result;
    }

    private record Located(StationExpansionCode code, StationState station, StationRegistrySavedData authority) {
    }
}

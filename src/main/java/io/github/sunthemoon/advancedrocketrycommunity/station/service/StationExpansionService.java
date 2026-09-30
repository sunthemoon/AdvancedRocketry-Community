package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
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

    /** The shared local-actor rule plus idempotent growth; {@code null} means allowed. */
    public static StationManagementCode check(
            StationAccessService access,
            boolean authorityAvailable,
            boolean localPlayer,
            boolean inSpace,
            boolean chunkLoaded,
            Optional<StationState> stationAtPosition,
            UUID actorId,
            boolean operator
    ) {
        StationManagementCode code = StationLocalActor.check(access, authorityAvailable, localPlayer, inSpace,
                chunkLoaded, stationAtPosition, actorId, operator);
        if (code != null) {
            return code;
        }
        return stationAtPosition.orElseThrow().expanded() ? StationManagementCode.ALREADY_EXPANDED : null;
    }

    /**
     * @param issuedByPlayer whether the command source is the player itself, not a console,
     *                       command block, function, sign or {@code /execute as} wrapper
     */
    public StationManagementResult request(ServerPlayer player, boolean issuedByPlayer) {
        Objects.requireNonNull(player, "player");
        StationLocalActor.Located located = locate(player, issuedByPlayer);
        if (located.code() != null) {
            return audit("request", player, StationManagementResult.of(located.code(), located.station()));
        }
        MinecraftServer server = player.getServer();
        boolean issued = confirmations.issue(player.getUUID(), located.station(), located.authority(),
                server.getTickCount());
        return audit("request", player, StationManagementResult.of(
                issued ? StationManagementCode.ISSUED : StationManagementCode.CAPACITY_REACHED, located.station()));
    }

    public StationManagementResult confirm(ServerPlayer player, boolean issuedByPlayer, UUID stationId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stationId, "stationId");
        MinecraftServer server = player.getServer();
        if (server == null) {
            return StationManagementResult.failure(StationManagementCode.AUTHORITY_UNAVAILABLE);
        }
        if (!StationLocalActor.localPlayer(server, player, issuedByPlayer)) {
            // Rejected before take(): another source cannot consume the player's confirmation.
            return audit("confirm", player, StationManagementResult.failure(StationManagementCode.NOT_LOCAL_PLAYER));
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        var outcome = confirmations.take(player.getUUID(), stationId, data, server.getTickCount());
        StationManagementCode rejected = switch (outcome.status()) {
            case READY -> null;
            case MISSING -> StationManagementCode.NO_CONFIRMATION;
            case EXPIRED -> StationManagementCode.CONFIRMATION_EXPIRED;
            case MISMATCH -> StationManagementCode.CONFIRMATION_MISMATCH;
        };
        if (rejected != null) {
            return audit("confirm", player, StationManagementResult.failure(rejected));
        }
        // Recheck permission, position, loaded chunk and authority against current state.
        StationLocalActor.Located located = locate(player, issuedByPlayer);
        if (located.code() != null) {
            return audit("confirm", player, StationManagementResult.of(located.code(), located.station()));
        }
        StationState observed = outcome.observed();
        if (located.authority() != data || !observed.equals(located.station())) {
            return audit("confirm", player, StationManagementResult.of(
                    StationManagementCode.STATION_CHANGED, located.station()));
        }
        StationRegistrySavedData.CheckedUpdate written;
        try {
            written = data.checkedExpand(server, observed);
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_EXPANSION_WRITE_FAILED station={} stage=candidate", stationId, exception);
            written = StationRegistrySavedData.CheckedUpdate.WRITE_FAILED;
        }
        StationManagementCode code = switch (written) {
            case COMMITTED -> StationManagementCode.EXPANDED;
            case UNCHANGED -> StationManagementCode.ALREADY_EXPANDED;
            case STALE -> StationManagementCode.STATION_CHANGED;
            case UNAVAILABLE -> StationManagementCode.AUTHORITY_UNAVAILABLE;
            case WRITE_FAILED -> StationManagementCode.WRITE_FAILED;
            case OUTCOME_UNKNOWN -> StationManagementCode.OUTCOME_UNKNOWN;
        };
        return audit("confirm", player, StationManagementResult.of(code,
                code == StationManagementCode.EXPANDED ? data.find(stationId).orElse(null) : observed));
    }

    public void forget(UUID playerId) {
        confirmations.clear(playerId);
    }

    public void clear() {
        confirmations.clear();
    }

    private StationLocalActor.Located locate(ServerPlayer player, boolean issuedByPlayer) {
        StationLocalActor.Located located = StationLocalActor.locate(access, player, issuedByPlayer);
        if (located.code() == null && located.station().expanded()) {
            return new StationLocalActor.Located(StationManagementCode.ALREADY_EXPANDED, located.station(),
                    located.authority());
        }
        return located;
    }

    private static StationManagementResult audit(String action, ServerPlayer player, StationManagementResult result) {
        return StationLocalActor.audit("EXPANSION", action, player, result);
    }
}

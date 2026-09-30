package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** ADR-041 local owner/operator gravity setting (0..100 % of 1 g), written through a checked commit. */
public final class StationGravityService {
    public static final int MAX_PERCENT = 100;

    private final StationAccessService access;

    public StationGravityService(StationAccessService access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    public StationManagementResult set(ServerPlayer player, boolean issuedByPlayer, int percent) {
        Objects.requireNonNull(player, "player");
        if (percent < 0 || percent > MAX_PERCENT) {
            throw new IllegalArgumentException("Station gravity percent is outside 0..100");
        }
        StationLocalActor.Located located = StationLocalActor.locate(access, player, issuedByPlayer);
        if (located.code() != null) {
            return audit(player, StationManagementResult.of(located.code(), located.station()));
        }
        MinecraftServer server = player.getServer();
        StationState observed = located.station();
        StationRegistrySavedData data = located.authority();
        StationRegistrySavedData.CheckedUpdate written;
        try {
            written = data.checkedSetGravity(server, observed, percent * 10);
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_GRAVITY_WRITE_FAILED station={} stage=candidate", observed.stationId(), exception);
            written = StationRegistrySavedData.CheckedUpdate.WRITE_FAILED;
        }
        StationManagementCode code = switch (written) {
            case COMMITTED -> StationManagementCode.GRAVITY_SET;
            case UNCHANGED -> StationManagementCode.GRAVITY_UNCHANGED;
            case STALE -> StationManagementCode.STATION_CHANGED;
            case UNAVAILABLE -> StationManagementCode.AUTHORITY_UNAVAILABLE;
            case WRITE_FAILED -> StationManagementCode.WRITE_FAILED;
            case OUTCOME_UNKNOWN -> StationManagementCode.OUTCOME_UNKNOWN;
        };
        return audit(player, StationManagementResult.of(code, code == StationManagementCode.GRAVITY_SET
                ? data.find(observed.stationId()).orElse(null) : observed));
    }

    private static StationManagementResult audit(ServerPlayer player, StationManagementResult result) {
        return StationLocalActor.audit("GRAVITY", "set", player, result);
    }
}

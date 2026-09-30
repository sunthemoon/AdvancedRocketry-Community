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
    /** Each committed gravity write rewrites the whole registry; allow one per station per 5 s. */
    public static final long WRITE_COOLDOWN_TICKS = 100L;

    private final StationAccessService access;
    private final StationWriteCooldown cooldown = new StationWriteCooldown(WRITE_COOLDOWN_TICKS);

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
        if (observed.environment().gravityMilli() == percent * 10) {
            // No write, so no cooldown.
            return audit(player, StationManagementResult.of(StationManagementCode.GRAVITY_UNCHANGED, observed));
        }
        if (!cooldown.ready(observed.stationId(), server.getTickCount())) {
            return audit(player, StationManagementResult.of(StationManagementCode.GRAVITY_COOLDOWN, observed));
        }
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
            // INSUFFICIENT_ENERGY belongs to relocation only and cannot be returned here.
            case UNAVAILABLE, INSUFFICIENT_ENERGY -> StationManagementCode.AUTHORITY_UNAVAILABLE;
            case WRITE_FAILED -> StationManagementCode.WRITE_FAILED;
            case OUTCOME_UNKNOWN -> StationManagementCode.OUTCOME_UNKNOWN;
        };
        if (code != StationManagementCode.GRAVITY_UNCHANGED && code != StationManagementCode.STATION_CHANGED
                && code != StationManagementCode.AUTHORITY_UNAVAILABLE) {
            // Committed and failed writes both start the cooldown, so a failing disk is not retried every tick.
            cooldown.record(observed.stationId(), server.getTickCount());
        }
        return audit(player, StationManagementResult.of(code, code == StationManagementCode.GRAVITY_SET
                ? data.find(observed.stationId()).orElse(null) : observed));
    }

    public void clear() {
        cooldown.clear();
    }

    private static StationManagementResult audit(ServerPlayer player, StationManagementResult result) {
        return StationLocalActor.audit("GRAVITY", "set", player, result);
    }
}

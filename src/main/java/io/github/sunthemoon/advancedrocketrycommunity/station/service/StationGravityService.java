package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * ADR-041 local owner/operator gravity setting (0..100 % of 1 g), written through a checked commit. The
 * ADR-065 {@code station_gravity_controller} is a front end to the same method, with the same authority,
 * unchanged-value no-op, per-station cooldown and server-wide write budget.
 */
public final class StationGravityService {
    public static final int MAX_PERCENT = 100;
    /** Each committed gravity write rewrites the whole registry; allow one per station per 5 s. */
    public static final long WRITE_COOLDOWN_TICKS = 100L;

    private final StationAccessService access;
    private final StationWriteBudget budget;
    private final StationWriteCooldown cooldown;

    public StationGravityService(StationAccessService access, StationWriteBudget budget) {
        this(access, budget, new StationWriteCooldown(WRITE_COOLDOWN_TICKS));
    }

    /** {@code cooldown} is the per-station settings cooldown, shared with the orbital controls. */
    public StationGravityService(StationAccessService access, StationWriteBudget budget, StationWriteCooldown cooldown) {
        this.access = Objects.requireNonNull(access, "access");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    public StationManagementResult set(ServerPlayer player, boolean issuedByPlayer, int percent) {
        return set(player, StationActorContext.command(issuedByPlayer), Optional.empty(), percent);
    }

    /**
     * {@code expected}, when present, is the station a controller screen last showed: the write is refused
     * as {@code STATION_CHANGED} unless the live station still equals it.
     */
    public StationManagementResult set(ServerPlayer player, StationActorContext context, Optional<StationState> expected,
                                       int percent) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(expected, "expected");
        if (percent < 0 || percent > MAX_PERCENT) {
            throw new IllegalArgumentException("Station gravity percent is outside 0..100");
        }
        StationLocalActor.Located located = StationLocalActor.locate(access, player, context);
        if (located.code() != null) {
            return audit(player, StationManagementResult.of(located.code(), located.station()));
        }
        MinecraftServer server = player.getServer();
        StationState observed = located.station();
        StationRegistrySavedData data = located.authority();
        if (expected.isPresent() && !expected.orElseThrow().equals(observed)) {
            return audit(player, StationManagementResult.of(StationManagementCode.STATION_CHANGED, observed));
        }
        if (observed.environment().gravityMilli() == percent * 10) {
            // No write, so no cooldown.
            return audit(player, StationManagementResult.of(StationManagementCode.GRAVITY_UNCHANGED, observed));
        }
        if (!cooldown.ready(observed.stationId(), server.getTickCount())) {
            return audit(player, StationManagementResult.of(StationManagementCode.GRAVITY_COOLDOWN, observed));
        }
        if (!budget.ready(server.getTickCount(), data.recordCount())) {
            return audit(player, StationManagementResult.of(StationManagementCode.REGISTRY_BUSY, observed));
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
            // INSUFFICIENT_ENERGY and the two FIRST_EVENT codes belong to relocation only and cannot be returned here.
            case UNAVAILABLE, INSUFFICIENT_ENERGY, FIRST_EVENT_OVER_BUDGET, FIRST_EVENT_UNAVAILABLE -> StationManagementCode.AUTHORITY_UNAVAILABLE;
            case WRITE_FAILED -> StationManagementCode.WRITE_FAILED;
            case OUTCOME_UNKNOWN -> StationManagementCode.OUTCOME_UNKNOWN;
        };
        if (startsCooldown(code)) {
            // Committed and failed writes both start the cooldown, so a failing disk is not retried every tick.
            cooldown.record(observed.stationId(), server.getTickCount());
            budget.record(server.getTickCount());
        }
        return audit(player, StationManagementResult.of(code, code == StationManagementCode.GRAVITY_SET
                ? data.find(observed.stationId()).orElse(null) : observed));
    }

    /**
     * Committed writes and failed writes (including an unknown outcome) start the cooldown, so a
     * failing disk is not retried every tick; refusals before any write do not (review F8).
     */
    static boolean startsCooldown(StationManagementCode code) {
        return code == StationManagementCode.GRAVITY_SET || code == StationManagementCode.WRITE_FAILED
                || code == StationManagementCode.OUTCOME_UNKNOWN;
    }

    public void clear() {
        cooldown.clear();
    }

    private static StationManagementResult audit(ServerPlayer player, StationManagementResult result) {
        return StationLocalActor.audit("GRAVITY", "set", player, result);
    }
}

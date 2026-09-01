package io.github.sunthemoon.advancedrocketrycommunity.station.service;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;

/** Narrow lifecycle bridge used by the deployment item. */
public final class StationRuntime {
    private static volatile StationOperationService service;

    private StationRuntime() {
    }

    public static void install(StationOperationService installed) {
        StationOperationService checked = Objects.requireNonNull(installed, "installed");
        checked.onInstalled();
        service = checked;
    }

    public static StationCreationResult createForPlayer(ServerPlayer player) {
        StationOperationService current = service;
        return current == null
                ? StationCreationResult.failure(StationCreationCode.SERVICE_UNAVAILABLE)
                : current.createForPlayer(player);
    }

    public static void clear() {
        service = null;
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.station.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.nio.file.Path;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Detached first-warp fixtures for the isolated A1 GameTests (W44 section 10): a detached registry is written to its
 * own fixture file through the same checked writer, so a shared election cannot steal its slot. GameTestServer only.
 */
public final class StationFirstWarpGameTestSupport {
    private StationFirstWarpGameTestSupport() {
    }

    /** The electing relocation of a detached registry, written to {@code file}. */
    public static StationRegistrySavedData.CheckedUpdate relocate(MinecraftServer server, StationRegistrySavedData registry,
                                                                  Path file, StationState observed,
                                                                  ResourceLocation target, int cost,
                                                                  FirstWarpRequest request) {
        require(server);
        return registry.relocation(file, observed, target, cost, CheckedSavedDataPolicy.atomic(ManagedSavedDataType.STATIONS),
                CheckedSavedDataPolicy.DirectoryForce.PLATFORM, request);
    }

    /** A relocation whose replacement is refused before the move: the cancelled, uncommitted case. */
    public static StationRegistrySavedData.CheckedUpdate refusedRelocation(MinecraftServer server,
                                                                           StationRegistrySavedData registry, Path file,
                                                                           StationState observed, ResourceLocation target,
                                                                           int cost, FirstWarpRequest request) {
        require(server);
        return registry.relocation(file, observed, target, cost, new CheckedSavedDataPolicy(ManagedSavedDataType.STATIONS,
                (staged, destination) -> { throw new java.io.IOException("Fixture refuses the replacement"); }),
                CheckedSavedDataPolicy.DirectoryForce.PLATFORM, request);
    }

    /** The root-only acknowledgement of a detached registry, written to {@code file}. */
    public static StationRegistrySavedData.CheckedUpdate acknowledge(MinecraftServer server,
                                                                     StationRegistrySavedData registry, Path file,
                                                                     FirstEvent event, FirstEvent receipt,
                                                                     StationCheckedWriter.Diagnostics log) {
        require(server);
        return registry.checkedDeliverFirstWarp(file, event, receipt,
                CheckedSavedDataPolicy.atomic(ManagedSavedDataType.STATIONS), CheckedSavedDataPolicy.DirectoryForce.PLATFORM,
                log);
    }

    private static void require(MinecraftServer server) {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("Detached first-warp fixtures require the GameTestServer thread");
        }
    }
}

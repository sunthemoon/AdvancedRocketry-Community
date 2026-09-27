package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import java.util.Optional;

/** Narrow lifecycle bridge for blocks/entities; all mutable state belongs to the installed manager. */
public final class RocketRuntime {
    private static volatile RocketOperationService service;

    private RocketRuntime() {
    }

    public static void install(RocketOperationService installedService) {
        RocketOperationService checked = Objects.requireNonNull(installedService, "installedService");
        checked.onInstalled();
        service = checked;
    }

    public static void clear() {
        service = null;
    }

    public static void requestAssembler(
            ServerPlayer player,
            BlockPos assemblerPosition,
            boolean assemble
    ) {
        RocketOperationService current = service;
        if (current == null) {
            player.displayClientMessage(
                    Component.translatable("message.advancedrocketrycommunity.rocket.service_unavailable"),
                    true
            );
            return;
        }
        current.requestAssembler(player, assemblerPosition, assemble);
    }

    public static void requestDisassembly(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        if (current == null) {
            player.displayClientMessage(
                    Component.translatable("message.advancedrocketrycommunity.rocket.service_unavailable"),
                    true
            );
            return;
        }
        current.requestDisassembly(player, rocket);
    }

    public static void openFlightMenu(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        if (current == null) {
            unavailable(player);
            return;
        }
        current.openFlightMenu(player, rocket);
    }

    public static Optional<TravelTarget> resolveCurrentTarget(ServerLevel level, BlockPos position) {
        RocketOperationService current = service;
        return current == null
                ? Optional.empty()
                : current.resolveCurrentTarget(level, position);
    }

    public static Optional<io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData>
            migrateLegacyFlightData(
                    ServerLevel level,
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData legacy
            ) {
        RocketOperationService current = service;
        return current == null
                ? Optional.empty()
                : current.migrateLegacyFlightData(level, legacy);
    }

    public static Optional<RocketTransferRecord> migrateCommittedLegacyTransfer(
            MinecraftServer server,
            RocketTransferRecord legacy
    ) {
        RocketOperationService current = service;
        return current == null
                ? Optional.empty()
                : current.migrateCommittedLegacyTransfer(server, legacy);
    }

    public static RocketFlightQuotes flightQuotes(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        return current == null ? RocketFlightQuotes.empty() : current.flightQuotes(player, rocket);
    }

    public static io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation navigation(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        return current == null ? io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation.empty()
                : current.navigation(player, rocket);
    }

    public static Optional<io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket> navigationCatalog(
            ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        return current == null ? Optional.empty() : current.navigationCatalog(player, rocket);
    }

    public static void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            TravelTarget target,
            UUID requestId
    ) {
        RocketOperationService current = service;
        if (current == null) {
            unavailable(player);
            return;
        }
        current.requestFlightIntent(player, rocketEntityId, action, target, requestId);
    }

    public static void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID requestId
    ) {
        requestFlightIntent(player, rocketEntityId, action, destination, null, requestId);
    }

    public static void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID destinationStationId,
            UUID requestId
    ) {
        requestFlightIntent(
                player,
                rocketEntityId,
                action,
                LegacyTravelTargetAdapter.fromLegacy(destination, destinationStationId),
                requestId
        );
    }

    /** Server-only operator/test boundary; no client data can invoke this method. */
    public static RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            RocketDestination destination,
            UUID requestId
    ) {
        RocketOperationService current = service;
        if (current == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return current.requestAdminFlight(rocket, destination, requestId);
    }

    /** Server-only operator/test path for typed targets. */
    public static RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            TravelTarget destination,
            UUID requestId
    ) {
        RocketOperationService current = service;
        if (current == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return current.requestAdminFlight(rocket, destination, requestId);
    }

    /** Server-only test/operator path; coordinates are still resolved from StationState. */
    public static RocketFlightRequestResult requestAdminStationFlight(
            RocketEntity rocket,
            UUID stationId,
            UUID requestId
    ) {
        RocketOperationService current = service;
        if (current == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return current.requestAdminStationFlight(rocket, stationId, requestId);
    }

    private static void unavailable(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.advancedrocketrycommunity.rocket.service_unavailable"),
                true
        );
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

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

    public static void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID requestId
    ) {
        RocketOperationService current = service;
        if (current == null) {
            unavailable(player);
            return;
        }
        current.requestFlightIntent(player, rocketEntityId, action, destination, requestId);
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

    private static void unavailable(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.advancedrocketrycommunity.rocket.service_unavailable"),
                true
        );
    }
}

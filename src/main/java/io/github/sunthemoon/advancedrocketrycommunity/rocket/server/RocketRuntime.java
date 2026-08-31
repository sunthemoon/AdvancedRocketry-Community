package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Narrow lifecycle bridge for blocks/entities; all mutable state belongs to the installed manager. */
public final class RocketRuntime {
    private static volatile RocketOperationService service;

    private RocketRuntime() {
    }

    public static void install(RocketOperationService installedService) {
        service = Objects.requireNonNull(installedService, "installedService");
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
}

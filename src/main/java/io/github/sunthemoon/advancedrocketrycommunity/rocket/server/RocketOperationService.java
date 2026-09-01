package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public interface RocketOperationService {
    default void onInstalled() {
    }

    void requestAssembler(ServerPlayer player, BlockPos assemblerPosition, boolean assemble);

    void requestDisassembly(ServerPlayer player, RocketEntity rocket);

    void openFlightMenu(ServerPlayer player, RocketEntity rocket);

    void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID requestId
    );

    RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            RocketDestination destination,
            UUID requestId
    );
}

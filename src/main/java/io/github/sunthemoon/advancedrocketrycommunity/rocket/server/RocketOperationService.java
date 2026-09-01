package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public interface RocketOperationService {
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
}

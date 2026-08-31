package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public interface RocketOperationService {
    void requestAssembler(ServerPlayer player, BlockPos assemblerPosition, boolean assemble);

    void requestDisassembly(ServerPlayer player, RocketEntity rocket);
}

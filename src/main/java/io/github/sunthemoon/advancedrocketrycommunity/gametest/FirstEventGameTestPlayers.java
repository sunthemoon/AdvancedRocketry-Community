package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Public access to the connected mock players for the W44 A1 fixtures outside this package. */
public final class FirstEventGameTestPlayers {
    private FirstEventGameTestPlayers() {
    }

    /** A connected, living, non-spectator mock player in {@code level} at {@code position}. */
    public static ServerPlayer join(MinecraftServer server, UUID id, String name, ServerLevel level, BlockPos position) {
        return ConnectedTestPlayers.join(server, id, name, level, position, new ArrayList<>());
    }
}

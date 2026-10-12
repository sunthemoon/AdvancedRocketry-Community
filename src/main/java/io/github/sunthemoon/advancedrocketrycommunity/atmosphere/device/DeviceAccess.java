package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.util.FakePlayer;

public final class DeviceAccess {
    public static boolean loaded(BlockEntity entity) {
        if (entity.isRemoved() || !(entity.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || level.getServer().getLevel(level.dimension()) != level) { return false; }
        var pos = entity.getBlockPos(); var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(pos) == entity;
    }
    public static boolean player(BlockEntity entity, Player player) {
        return loaded(entity) && player instanceof ServerPlayer server && !(player instanceof FakePlayer)
                && player.isAlive() && !player.isSpectator() && player.level() == entity.getLevel()
                && server.connection != null && server.connection.connection.isConnected()
                && server.server.getPlayerList().getPlayer(player.getUUID()) == player
                && player.distanceToSqr(entity.getBlockPos().getCenter()) <= 64
                && entity.getLevel().mayInteract(player, entity.getBlockPos());
    }
    private DeviceAccess() { }
}

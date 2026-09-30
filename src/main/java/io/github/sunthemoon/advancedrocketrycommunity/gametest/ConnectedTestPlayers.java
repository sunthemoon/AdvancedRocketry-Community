package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Connected mock players (as GameTestHelper.makeMockServerPlayerInLevel) and reply capture for GameTests. */
final class ConnectedTestPlayers {
    static final String ACTION_BAR = "[action bar] ";

    private ConnectedTestPlayers() {
    }

    /** Joins a creative, non-operator player with the given UUID over an embedded channel and moves it. */
    static ServerPlayer join(MinecraftServer server, UUID id, String name, ServerLevel level, BlockPos position,
                             List<String> replies) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(id, name)) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }

            @Override
            public boolean hasPermissions(int permissionLevel) {
                return false;
            }

            @Override
            public void sendSystemMessage(Component message) {
                replies.add(message.getString());
            }

            /** Chat and action-bar messages ({@code displayClientMessage}); the action bar is marked. */
            @Override
            public void sendSystemMessage(Component message, boolean overlay) {
                replies.add(overlay ? ACTION_BAR + message.getString() : message.getString());
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.teleportTo(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    static CommandSource capture(List<String> replies) {
        return new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                replies.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
    }
}

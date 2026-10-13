package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
        return join(server, id, name, level, position, replies, false);
    }

    /**
     * As {@link #join}; with {@code serverPermissions} the player's permission level comes from the
     * server's ops list, so {@code op}/{@code deop} change it as for a real player.
     */
    static ServerPlayer join(MinecraftServer server, UUID id, String name, ServerLevel level, BlockPos position,
                             List<String> replies, boolean serverPermissions) {
        return join(server, id, name, level, position, replies, serverPermissions, new ArrayList<>());
    }

    /** As {@link #join}; every message is also recorded in {@code tokens} as its {@link #token}. */
    static ServerPlayer join(MinecraftServer server, UUID id, String name, ServerLevel level, BlockPos position,
                             List<String> replies, boolean serverPermissions, List<String> tokens) {
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
                return serverPermissions && server.getProfilePermissions(getGameProfile()) >= permissionLevel;
            }

            @Override
            public void sendSystemMessage(Component message) {
                replies.add(message.getString());
                tokens.add(token(message));
            }

            /** Chat and action-bar messages ({@code displayClientMessage}); the action bar is marked. */
            @Override
            public void sendSystemMessage(Component message, boolean overlay) {
                replies.add(overlay ? ACTION_BAR + message.getString() : message.getString());
                tokens.add(overlay ? ACTION_BAR + token(message) : token(message));
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.teleportTo(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        return player;
    }

    /**
     * The code-token form of a message, independent of any language file: a translatable component is its key, followed
     * by its arguments' tokens in parentheses when it has any; other contents are their text; siblings follow.
     */
    static String token(Component message) {
        StringBuilder out = new StringBuilder();
        if (message.getContents() instanceof TranslatableContents translatable) {
            out.append(translatable.getKey());
            Object[] arguments = translatable.getArgs();
            if (arguments.length > 0) {
                out.append('(');
                for (int index = 0; index < arguments.length; index++) {
                    out.append(index == 0 ? "" : ", ").append(arguments[index] instanceof Component argument
                            ? token(argument) : String.valueOf(arguments[index]));
                }
                out.append(')');
            }
        } else {
            message.getContents().visit(text -> {
                out.append(text);
                return Optional.empty();
            });
        }
        message.getSiblings().forEach(sibling -> out.append(token(sibling)));
        return out.toString();
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

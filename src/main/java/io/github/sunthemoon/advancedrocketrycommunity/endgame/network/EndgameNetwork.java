package io.github.sunthemoon.advancedrocketrycommunity.endgame.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * ADR-054 section 4: the exact-version, server-to-client {@code advancedrocketrycommunity:endgame} channel, protocol
 * 1, with the device view as its only message. There is no client-to-server message: intents are menu buttons. A
 * client without the channel cannot join.
 */
public final class EndgameNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static volatile EndgameNetwork installed;

    private final SimpleChannel channel;

    public EndgameNetwork() {
        channel = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("endgame"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        channel.messageBuilder(EndgameDeviceView.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EndgameDeviceView::encode)
                .decoder(EndgameDeviceView::decode)
                .consumerMainThread(EndgameDeviceView::handle)
                .add();
    }

    /** The channel is registered once per game; it holds no world state. */
    public static void install(EndgameNetwork network) {
        installed = Objects.requireNonNull(network, "network");
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public static void sendView(ServerPlayer player, EndgameDeviceView view) {
        EndgameNetwork current = installed;
        if (current != null) {
            current.channel.send(PacketDistributor.PLAYER.with(() -> player), view);
        }
    }
}

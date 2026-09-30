package io.github.sunthemoon.advancedrocketrycommunity.satellite.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** ADR-049 section 10: the exact-version, server-to-client satellite channel. */
public final class SatelliteNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static volatile SatelliteNetwork installed;

    private final SimpleChannel channel;

    public SatelliteNetwork() {
        channel = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("satellite"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        channel.messageBuilder(SatelliteTerminalViewPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SatelliteTerminalViewPacket::encode)
                .decoder(SatelliteTerminalViewPacket::decode)
                .consumerMainThread(SatelliteTerminalViewPacket::handle)
                .add();
        channel.messageBuilder(SurveyScanResultPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SurveyScanResultPacket::encode)
                .decoder(SurveyScanResultPacket::decode)
                .consumerMainThread(SurveyScanResultPacket::handle)
                .add();
    }

    /** The channel is registered once per game; it holds no world state. */
    public static void install(SatelliteNetwork network) {
        installed = Objects.requireNonNull(network, "network");
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public static void sendScanResult(ServerPlayer player, SurveyScanResultPacket packet) {
        SatelliteNetwork current = installed;
        if (current != null) {
            current.channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendTerminalView(ServerPlayer player, SatelliteTerminalViewPacket packet) {
        SatelliteNetwork current = installed;
        if (current != null) {
            current.channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}

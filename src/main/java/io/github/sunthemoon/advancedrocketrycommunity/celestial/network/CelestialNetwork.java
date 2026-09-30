package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Instance-owned, exact-version SimpleChannel for the display snapshot and the station sky context. */
public final class CelestialNetwork {
    /** 3: ADR-047 added message 1, the station sky context. */
    private static final String PROTOCOL_VERSION = "3";

    private final SimpleChannel channel;

    public CelestialNetwork() {
        channel = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("celestial_snapshot"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        channel.messageBuilder(CelestialSnapshotPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CelestialSnapshotPacket::encode)
                .decoder(CelestialSnapshotPacket::decode)
                .consumerMainThread(CelestialSnapshotPacket::handle)
                .add();
        channel.messageBuilder(StationSkyContextPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(StationSkyContextPacket::encode)
                .decoder(StationSkyContextPacket::decode)
                .consumerMainThread(StationSkyContextPacket::handle)
                .add();
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public void send(ServerPlayer player, CelestialSnapshotPacket packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public void sendSkyContext(ServerPlayer player, StationSkyContextPacket packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Instance-owned SimpleChannel wrapper for the v0.3 display snapshot. */
public final class CelestialNetwork {
    private static final String PROTOCOL_VERSION = "1";

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
    }

    public void send(ServerPlayer player, CelestialSnapshotPacket packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}

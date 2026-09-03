package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** S2C-only tracking-player channel; clients cannot submit blocks or statistics. */
public final class RocketVisualNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private final SimpleChannel channel;

    public RocketVisualNetwork() {
        channel = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("rocket_visual"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        channel.messageBuilder(RocketVisualChunkPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RocketVisualChunkPacket::encode)
                .decoder(RocketVisualChunkPacket::decode)
                .consumerMainThread(RocketVisualChunkPacket::handle)
                .add();
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public int send(ServerPlayer player, RocketEntity rocket) {
        if (!rocket.operational()) {
            return 0;
        }
        RocketVisualSnapshot visual = RocketVisualSnapshot.fromServerSnapshot(
                rocket.snapshot().orElseThrow()
        );
        List<RocketVisualChunkPacket> packets = RocketVisualChunker.chunk(rocket.getUUID(), visual);
        for (RocketVisualChunkPacket packet : packets) {
            channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
        return packets.size();
    }
}

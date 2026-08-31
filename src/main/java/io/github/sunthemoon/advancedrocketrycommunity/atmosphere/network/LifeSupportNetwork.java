package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class LifeSupportNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private final SimpleChannel channel;

    public LifeSupportNetwork() {
        channel = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("life_support_status"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        channel.messageBuilder(LifeSupportStatusPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LifeSupportStatusPacket::encode)
                .decoder(LifeSupportStatusPacket::decode)
                .consumerMainThread(LifeSupportStatusPacket::handle)
                .add();
    }

    public void send(ServerPlayer player, PlayerLifeSupportSnapshot snapshot) {
        channel.send(
                PacketDistributor.PLAYER.with(() -> player),
                LifeSupportStatusPacket.current(snapshot)
        );
    }
}

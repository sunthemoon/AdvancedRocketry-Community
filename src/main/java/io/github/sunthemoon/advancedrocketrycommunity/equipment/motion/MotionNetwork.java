package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * ADR-066 section 5.2: the exact-version {@code advancedrocketrycommunity:suit_motion} channel, protocol 2. The
 * client sends only the fixed jetpack intent; the server sends paid increments and equipment authorization.
 */
public final class MotionNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static SimpleChannel channel;

    /** Called once on both physical sides during common setup, before the network registry locks. */
    public static void register() {
        if (channel != null) { throw new IllegalStateException("Suit motion channel is already registered"); }
        SimpleChannel created = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("suit_motion"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        created.messageBuilder(JetpackIntentPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(JetpackIntentPacket::encode)
                .decoder(JetpackIntentPacket::decode)
                .consumerMainThread(JetpackIntentPacket::handle)
                .add();
        created.messageBuilder(JetpackImpulsePacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(JetpackImpulsePacket::encode)
                .decoder(JetpackImpulsePacket::decode)
                .consumerMainThread(JetpackImpulsePacket::handle)
                .add();
        created.messageBuilder(EquipmentEnabledPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EquipmentEnabledPacket::encode)
                .decoder(EquipmentEnabledPacket::decode)
                .consumerMainThread(EquipmentEnabledPacket::handle)
                .add();
        channel = created;
    }

    public static String protocolVersion() { return PROTOCOL_VERSION; }

    public static void sendImpulse(ServerPlayer player, JetpackPhysics.Impulse impulse) {
        SimpleChannel current = channel;
        if (current != null) { current.send(PacketDistributor.PLAYER.with(() -> player), JetpackImpulsePacket.of(impulse)); }
    }

    public static void sendIntent(JetpackIntentPacket intent) {
        SimpleChannel current = channel;
        if (current != null) { current.sendToServer(intent); }
    }

    public static void sendEquipmentEnabled(ServerPlayer player, boolean enabled) {
        SimpleChannel current = channel;
        if (current != null) { current.send(PacketDistributor.PLAYER.with(() -> player), new EquipmentEnabledPacket(enabled)); }
    }

    private MotionNetwork() { }
}

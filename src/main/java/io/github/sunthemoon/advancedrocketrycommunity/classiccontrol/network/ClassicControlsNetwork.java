package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * ADR-065 section 11.1 / R92: the exact-version {@code advancedrocketrycommunity:classic_controls} channel, protocol 2.
 * Index 0 is the C2S control intent, index 1 the S2C view of the open control menu and index 2 the S2C beacon
 * marker snapshot of C17c, sent by the beacon finder service. Forge disconnects a message that arrives in the wrong
 * direction and drops an unknown index; a client without this exact version cannot join.
 */
public final class ClassicControlsNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static volatile ClassicControlsNetwork installed;

    private final SimpleChannel channel;

    public ClassicControlsNetwork() {
        channel = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("classic_controls"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(ClassicControlsNetwork::acceptsProtocol)
                .serverAcceptedVersions(ClassicControlsNetwork::acceptsProtocol)
                .simpleChannel();
        channel.messageBuilder(ClassicControlIntentPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ClassicControlIntentPacket::encode)
                .decoder(ClassicControlIntentPacket::decode)
                .consumerMainThread(ClassicControlIntentPacket::handle)
                .add();
        channel.messageBuilder(ClassicControlViewPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClassicControlViewPacket::encode)
                .decoder(ClassicControlViewPacket::decode)
                .consumerMainThread(ClassicControlViewPacket::handle)
                .add();
        channel.messageBuilder(BeaconMarkerSnapshotPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BeaconMarkerSnapshotPacket::encode)
                .decoder(BeaconMarkerSnapshotPacket::decode)
                .consumerMainThread(BeaconMarkerSnapshotPacket::handle)
                .add();
    }

    /** The channel is registered once per game; it holds no world state. */
    public static void install(ClassicControlsNetwork network) {
        installed = Objects.requireNonNull(network, "network");
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    /** Exact-version negotiation: only protocol 2, never a protocol-1, absent or vanilla peer. */
    public static boolean acceptsProtocol(String version) {
        return PROTOCOL_VERSION.equals(version);
    }

    /** Server: a view to the one player who holds the menu. */
    public static void sendView(ServerPlayer player, ClassicControlViewPacket view) {
        ClassicControlsNetwork current = installed;
        if (current != null) {
            current.channel.send(PacketDistributor.PLAYER.with(() -> player), view);
        }
    }

    /** Server: one private beacon marker snapshot to the one eligible player it belongs to (C17c). */
    public static void sendBeaconSnapshot(ServerPlayer player, BeaconMarkerSnapshotPacket snapshot) {
        ClassicControlsNetwork current = installed;
        if (current != null) {
            current.channel.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
        }
    }

    /** Client: one intent for the open menu. */
    public static void sendIntent(ClassicControlIntentPacket intent) {
        ClassicControlsNetwork current = installed;
        if (current != null) {
            current.channel.sendToServer(intent);
        }
    }
}

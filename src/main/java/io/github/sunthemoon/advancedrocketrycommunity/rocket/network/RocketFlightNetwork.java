package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import java.util.UUID;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RocketFlightNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static SimpleChannel channel;

    public RocketFlightNetwork() {
        if (channel != null) {
            throw new IllegalStateException("Rocket flight channel is already initialized");
        }
        SimpleChannel created = NetworkRegistry.ChannelBuilder
                .named(ModIdentity.id("rocket_flight"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
        created.messageBuilder(RocketFlightIntentPacket.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RocketFlightIntentPacket::encode)
                .decoder(RocketFlightIntentPacket::decode)
                .consumerMainThread(RocketFlightIntentPacket::handle)
                .add();
        channel = created;
    }

    public static void sendIntent(
            RocketFlightAction action,
            int rocketEntityId,
            RocketDestination destination
    ) {
        sendIntent(action, rocketEntityId, destination, null);
    }

    public static void sendIntent(
            RocketFlightAction action,
            int rocketEntityId,
            RocketDestination destination,
            UUID destinationStationId
    ) {
        SimpleChannel current = channel;
        if (current == null) {
            throw new IllegalStateException("Rocket flight channel is not initialized");
        }
        current.sendToServer(new RocketFlightIntentPacket(
                action,
                rocketEntityId,
                destination,
                destinationStationId,
                UUID.randomUUID()
        ));
    }
}

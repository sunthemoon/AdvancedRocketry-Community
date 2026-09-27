package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class RocketFlightNetwork {
    private static final String PROTOCOL_VERSION = "8";
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
        created.messageBuilder(RocketFlightPlanPacket.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RocketFlightPlanPacket::encode)
                .decoder(RocketFlightPlanPacket::decode)
                .consumerMainThread(RocketFlightPlanPacket::handle)
                .add();
        created.messageBuilder(CelestialSnapshotPacket.class, 2, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CelestialSnapshotPacket::encode)
                .decoder(CelestialSnapshotPacket::decode)
                .consumerMainThread(CelestialSnapshotPacket::handle)
                .add();
        channel = created;
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }

    public static void sendPlan(ServerPlayer player, RocketFlightPlanPacket packet) {
        if (channel == null) {
            throw new IllegalStateException("Rocket flight channel is not initialized");
        }
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendCatalog(ServerPlayer player, CelestialSnapshotPacket packet) {
        if (channel == null) { throw new IllegalStateException("Rocket flight channel is not initialized"); }
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
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
        sendIntent(action, rocketEntityId,
                LegacyTravelTargetAdapter.fromLegacy(destination, destinationStationId));
    }

    public static void sendIntent(
            RocketFlightAction action,
            int rocketEntityId,
            TravelTarget target
    ) {
        SimpleChannel current = channel;
        if (current == null) {
            throw new IllegalStateException("Rocket flight channel is not initialized");
        }
        current.sendToServer(new RocketFlightIntentPacket(
                action,
                rocketEntityId,
                target,
                UUID.randomUUID()
        ));
    }
}

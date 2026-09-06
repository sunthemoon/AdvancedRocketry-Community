package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.client.RocketFlightPlanHandler;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Fixed-size, S2C-only plan update for one open flight console. */
public record RocketFlightPlanPacket(int containerId, int rocketEntityId, RocketFlightPlanSnapshot plan,
                                    RocketFlightQuotes quotes) {
    public static final int MIN_ENCODED_BYTES = 22;
    public static final int MAX_ENCODED_BYTES = 38;

    public RocketFlightPlanPacket {
        if (containerId < 0 || rocketEntityId < 0) {
            throw new IllegalArgumentException("Flight menu identifiers must be nonnegative");
        }
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(quotes, "quotes");
    }

    public static void encode(RocketFlightPlanPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.containerId());
        buffer.writeInt(packet.rocketEntityId());
        buffer.writeByte(packet.plan().destination() == null ? -1 : packet.plan().destination().networkId());
        if (packet.plan().stationId() != null) {
            buffer.writeUUID(packet.plan().stationId());
        }
        buffer.writeInt(packet.quotes().earth().requiredFuel());
        buffer.writeInt(packet.quotes().moon().requiredFuel());
        buffer.writeInt(packet.quotes().station().requiredFuel());
        buffer.writeByte((packet.quotes().earth().canLaunch() ? 1 : 0)
                | (packet.quotes().moon().canLaunch() ? 2 : 0)
                | (packet.quotes().station().canLaunch() ? 4 : 0));
    }

    public static RocketFlightPlanPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readableBytes();
        if (size != MIN_ENCODED_BYTES && size != MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Flight plan frame has an invalid fixed size");
        }
        int containerId = buffer.readInt();
        int entityId = buffer.readInt();
        int destinationId = buffer.readByte();
        RocketDestination destination = destinationId == -1 ? null : RocketDestination.fromNetworkId(destinationId);
        RocketFlightPlanSnapshot plan = new RocketFlightPlanSnapshot(destination,
                destination == RocketDestination.SPACE_STATION ? buffer.readUUID() : null);
        int earth = buffer.readInt();
        int moon = buffer.readInt();
        int station = buffer.readInt();
        int flags = buffer.readUnsignedByte();
        if ((flags & ~7) != 0) {
            throw new IllegalArgumentException("Flight quote flags contain unknown bits");
        }
        RocketFlightQuotes quotes = new RocketFlightQuotes(
                new RocketFlightQuotes.Quote(earth, (flags & 1) != 0),
                new RocketFlightQuotes.Quote(moon, (flags & 2) != 0),
                new RocketFlightQuotes.Quote(station, (flags & 4) != 0));
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Flight plan frame contains trailing bytes");
        }
        return new RocketFlightPlanPacket(containerId, entityId, plan, quotes);
    }

    public boolean targets(int openContainerId, int openRocketEntityId) {
        return containerId == openContainerId && rocketEntityId == openRocketEntityId;
    }

    public static void handle(RocketFlightPlanPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RocketFlightPlanHandler.handle(packet));
        contextSupplier.get().setPacketHandled(true);
    }
}

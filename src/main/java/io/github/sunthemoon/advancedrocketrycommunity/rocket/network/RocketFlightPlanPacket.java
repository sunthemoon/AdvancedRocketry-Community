package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.client.RocketFlightPlanHandler;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.travel.network.TravelTargetWireCodec;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Bounded, S2C-only typed plan and quote update for one open flight console. */
public record RocketFlightPlanPacket(int containerId, int rocketEntityId, RocketFlightPlanSnapshot plan,
                                    RocketFlightQuotes quotes) {
    public static final int MIN_ENCODED_BYTES = 10;
    public static final int MAX_ENCODED_BYTES = 8 + 1 + TravelTargetWireCodec.MAX_ENCODED_BYTES
            + 3 + RocketFlightQuotes.MAX_QUOTES * (TravelTargetWireCodec.MAX_ENCODED_BYTES + 5);

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
        buffer.writeBoolean(packet.plan().target() != null);
        if (packet.plan().target() != null) {
            TravelTargetWireCodec.encode(buffer, packet.plan().target());
        }
        buffer.writeVarInt(packet.quotes().entries().size());
        for (RocketFlightQuotes.TargetQuote entry : packet.quotes().entries()) {
            TravelTargetWireCodec.encode(buffer, entry.target());
            buffer.writeInt(entry.quote().requiredFuel());
            buffer.writeByte(entry.quote().canLaunch() ? 1 : 0);
        }
    }

    public static RocketFlightPlanPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readableBytes();
        if (size < MIN_ENCODED_BYTES || size > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Flight plan frame is outside the fixed size bound");
        }
        int containerId = buffer.readInt();
        int entityId = buffer.readInt();
        RocketFlightPlanSnapshot plan = buffer.readBoolean()
                ? new RocketFlightPlanSnapshot(TravelTargetWireCodec.decode(buffer))
                : RocketFlightPlanSnapshot.empty();
        int countStart = buffer.readerIndex();
        int count = buffer.readVarInt();
        if (buffer.readerIndex() - countStart != FriendlyByteBuf.getVarIntSize(count)
                || count < 0
                || count > RocketFlightQuotes.MAX_QUOTES) {
            throw new IllegalArgumentException("Flight quote count is invalid or non-canonical");
        }
        java.util.ArrayList<RocketFlightQuotes.TargetQuote> entries = new java.util.ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            var target = TravelTargetWireCodec.decode(buffer);
            int requiredFuel = buffer.readInt();
            int launchable = buffer.readUnsignedByte();
            if (launchable > 1) {
                throw new IllegalArgumentException("Flight quote launch flag is invalid");
            }
            entries.add(new RocketFlightQuotes.TargetQuote(
                    target,
                    new RocketFlightQuotes.Quote(requiredFuel, launchable == 1)
            ));
        }
        RocketFlightQuotes quotes = new RocketFlightQuotes(entries);
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

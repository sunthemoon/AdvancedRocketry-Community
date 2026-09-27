package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.client.RocketFlightPlanHandler;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightPlanSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.network.TravelTargetWireCodec;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Bounded, S2C-only typed plan and quote update for one open flight console. */
public record RocketFlightPlanPacket(int containerId, int rocketEntityId, RocketFlightPlanSnapshot plan,
                                    RocketNavigation navigation) {
    public static final int MIN_ENCODED_BYTES = 19;
    public static final int MAX_ENCODED_BYTES = 8 + 1 + TravelTargetWireCodec.MAX_ENCODED_BYTES
            + 3 + RocketFlightQuotes.MAX_QUOTES * (TravelTargetWireCodec.MAX_ENCODED_BYTES + 5)
            + 8 + 3 + StationLimits.MAX_ACCESSIBLE_DESTINATIONS
            * (16 + 2 + 3 * StationLimits.MAX_NAME_LENGTH + 2 + TravelTarget.MAX_RESOURCE_LOCATION_CHARS);

    public RocketFlightPlanPacket(int containerId, int rocketEntityId, RocketFlightPlanSnapshot plan, RocketFlightQuotes quotes) {
        this(containerId, rocketEntityId, plan, new RocketNavigation(0, quotes, java.util.List.of()));
    }

    public RocketFlightQuotes quotes() { return navigation.quotes(); }

    public RocketFlightPlanPacket {
        if (containerId < 0 || rocketEntityId < 0) {
            throw new IllegalArgumentException("Flight menu identifiers must be nonnegative");
        }
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(navigation, "navigation");
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
            buffer.writeByte(entry.quote().status().wireId());
        }
        buffer.writeLong(packet.navigation().generation());
        buffer.writeVarInt(packet.navigation().stations().size());
        for (var station : packet.navigation().stations()) {
            buffer.writeUUID(station.stationId());
            buffer.writeUtf(station.name(), StationLimits.MAX_NAME_LENGTH);
            buffer.writeUtf(station.orbitBody().toString(), TravelTarget.MAX_RESOURCE_LOCATION_CHARS);
        }
    }

    public static RocketFlightPlanPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readableBytes();
        if (size < MIN_ENCODED_BYTES || size > MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Flight plan frame is outside the fixed size bound");
        }
        int containerId = buffer.readInt();
        int entityId = buffer.readInt();
        int flag = buffer.readUnsignedByte();
        if (flag > 1) { throw new IllegalArgumentException("Invalid optional plan flag"); }
        RocketFlightPlanSnapshot plan = flag == 1
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
            var status = RocketNavigationStatus.fromWire(buffer.readUnsignedByte());
            entries.add(new RocketFlightQuotes.TargetQuote(
                    target,
                    new RocketFlightQuotes.Quote(requiredFuel, status)
            ));
        }
        RocketFlightQuotes quotes = new RocketFlightQuotes(entries);
        long generation = buffer.readLong();
        int stationCountStart = buffer.readerIndex();
        int stationCount = buffer.readVarInt();
        if (stationCount < 0 || stationCount > StationLimits.MAX_ACCESSIBLE_DESTINATIONS
                || buffer.readerIndex() - stationCountStart != FriendlyByteBuf.getVarIntSize(stationCount)) {
            throw new IllegalArgumentException("Invalid navigation station count");
        }
        var stations = new java.util.ArrayList<RocketNavigation.Station>(stationCount);
        for (int index = 0; index < stationCount; index++) {
            var id = buffer.readUUID();
            String name = buffer.readUtf(StationLimits.MAX_NAME_LENGTH);
            String rawBody = buffer.readUtf(TravelTarget.MAX_RESOURCE_LOCATION_CHARS);
            var body = net.minecraft.resources.ResourceLocation.tryParse(rawBody);
            if (body == null || !body.toString().equals(rawBody)) {
                throw new IllegalArgumentException("Invalid station orbit ID");
            }
            var station = new RocketNavigation.Station(id, name, body);
            if (!station.name().equals(name)) { throw new IllegalArgumentException("Noncanonical station name"); }
            stations.add(station);
        }
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Flight plan frame contains trailing bytes");
        }
        return new RocketFlightPlanPacket(containerId, entityId, plan, new RocketNavigation(generation, quotes, stations));
    }

    public boolean targets(int openContainerId, int openRocketEntityId) {
        return containerId == openContainerId && rocketEntityId == openRocketEntityId;
    }

    public static void handle(RocketFlightPlanPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RocketFlightPlanHandler.handle(packet));
        contextSupplier.get().setPacketHandled(true);
    }
}

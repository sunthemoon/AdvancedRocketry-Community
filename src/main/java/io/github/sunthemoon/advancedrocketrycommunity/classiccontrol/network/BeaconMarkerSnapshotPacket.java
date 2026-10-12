package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-065 sections 7 and 11.1, {@code classic_controls} index 2, server to client: the private beacon marker
 * snapshot of C17c. This slice freezes and registers its codec only; the server does not send it yet, and the
 * client only caches it. Schema byte 1, a count of at most {@link #MAX_MARKERS}, then per marker the beacon UUID,
 * its integer block position, a strict 0/1 live flag (0: last known or inactive) and its name of at most
 * {@link #MAX_NAME_CHARS} characters; at most {@link #MAX_BYTES} in all, and only in its canonical encoding. No
 * owner, member or other body is sent.
 */
public record BeaconMarkerSnapshotPacket(List<Marker> markers) {
    public static final int SCHEMA = 1;
    public static final int MAX_MARKERS = 8;
    public static final int MAX_NAME_CHARS = 32;
    /** The frozen ADR-065 bound. */
    public static final int MAX_BYTES = 1024;
    /** One marker at most: UUID, three ints, the flag and a name of 32 three-byte characters with its length. */
    public static final int MAX_MARKER_BYTES = 16 + 3 * Integer.BYTES + 1 + 1 + 3 * MAX_NAME_CHARS;
    public static final int ENCODED_MAXIMUM = 1 + 1 + MAX_MARKERS * MAX_MARKER_BYTES;
    public static final BeaconMarkerSnapshotPacket EMPTY = new BeaconMarkerSnapshotPacket(List.of());

    public BeaconMarkerSnapshotPacket {
        markers = List.copyOf(markers);
        if (markers.size() > MAX_MARKERS) {
            throw new IllegalArgumentException("A beacon snapshot has at most " + MAX_MARKERS + " markers");
        }
        if (new HashSet<>(markers.stream().map(Marker::beaconId).toList()).size() != markers.size()) {
            throw new IllegalArgumentException("A beacon snapshot names each beacon once");
        }
    }

    public static void encode(BeaconMarkerSnapshotPacket packet, FriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        buffer.writeByte(SCHEMA);
        buffer.writeByte(packet.markers().size());
        for (Marker marker : packet.markers()) {
            buffer.writeUUID(marker.beaconId());
            buffer.writeInt(marker.x());
            buffer.writeInt(marker.y());
            buffer.writeInt(marker.z());
            buffer.writeBoolean(marker.live());
            buffer.writeUtf(marker.name(), MAX_NAME_CHARS);
        }
        if (buffer.writerIndex() - start > MAX_BYTES) {
            throw new EncoderException("A beacon marker snapshot exceeds " + MAX_BYTES + " bytes");
        }
    }

    public static BeaconMarkerSnapshotPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("A beacon marker snapshot exceeds " + MAX_BYTES + " bytes");
        }
        int start = buffer.readerIndex();
        try {
            if (buffer.readUnsignedByte() != SCHEMA) {
                throw new DecoderException("Unsupported beacon marker snapshot schema");
            }
            int count = buffer.readUnsignedByte();
            if (count > MAX_MARKERS) {
                throw new DecoderException("A beacon snapshot has at most " + MAX_MARKERS + " markers");
            }
            List<Marker> markers = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                UUID beaconId = buffer.readUUID();
                int x = buffer.readInt();
                int y = buffer.readInt();
                int z = buffer.readInt();
                byte flag = buffer.readByte();
                if (flag != 0 && flag != 1) {
                    throw new DecoderException("A beacon marker live flag must be 0 or 1");
                }
                markers.add(new Marker(beaconId, x, y, z, flag == 1, buffer.readUtf(MAX_NAME_CHARS)));
            }
            if (buffer.isReadable()) {
                throw new DecoderException("Trailing bytes after the beacon marker snapshot");
            }
            BeaconMarkerSnapshotPacket snapshot = new BeaconMarkerSnapshotPacket(markers);
            ClassicCodecs.requireCanonical(buffer, start, snapshot, BeaconMarkerSnapshotPacket::encode,
                    "beacon marker snapshot");
            return snapshot;
        } catch (IllegalArgumentException | IndexOutOfBoundsException malformed) {
            throw new DecoderException("Malformed beacon marker snapshot", malformed);
        }
    }

    /** Client only: C17c will show the markers; this slice only keeps the last snapshot. */
    public static void handle(BeaconMarkerSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection().getReceptionSide().isClient()) {
            BeaconMarkerCache.accept(packet);
        }
        context.setPacketHandled(true);
    }

    /**
     * One owned beacon: a block position inside the world limits ({@link Level#MAX_LEVEL_SIZE} horizontally and
     * the dimension-type height range), whether it is live, and its name.
     */
    public record Marker(UUID beaconId, int x, int y, int z, boolean live, String name) {
        public Marker {
            Objects.requireNonNull(beaconId, "beaconId");
            Objects.requireNonNull(name, "name");
            if (Math.abs((long) x) > Level.MAX_LEVEL_SIZE || Math.abs((long) z) > Level.MAX_LEVEL_SIZE
                    || y < DimensionType.MIN_Y || y > DimensionType.MAX_Y) {
                throw new IllegalArgumentException("A beacon marker lies outside the world limits");
            }
            if (name.length() > MAX_NAME_CHARS || name.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("A beacon name has at most " + MAX_NAME_CHARS
                        + " printable characters");
            }
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationOrbitalControls;
import io.netty.handler.codec.DecoderException;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-047, payload schema 2 (ADR-065 section 11.1): the sky context of the station region the player stands
 * in, or none. It carries no station UUID, name, owner or member, and the client uses it for presentation
 * only: the sky kind, an optional body, the logical distance, the three orientation phases and rates, their
 * epoch and the server's logical sample time.
 */
public record StationSkyContextPacket(Optional<Context> context) {
    public static final int PAYLOAD_SCHEMA = 2;
    /**
     * Schema, presence, kind and body-presence bytes, a VarInt length and at most 128 identifier characters,
     * the distance short, three phase ints, three rate bytes and two longs: 167 bytes.
     */
    public static final int ENCODED_MAXIMUM = 4 + 2 + BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS + 2 + 12 + 3 + 16;
    /** The frozen ADR-065 bound; a larger message is a decoding error before anything is read. */
    public static final int MAX_BYTES = 256;
    public static final StationSkyContextPacket NONE = new StationSkyContextPacket(Optional.empty());

    public StationSkyContextPacket {
        Objects.requireNonNull(context, "context");
    }

    /** The body, when the context names one. */
    public Optional<ResourceLocation> orbitBody() {
        return context.flatMap(Context::body);
    }

    public static void encode(StationSkyContextPacket packet, FriendlyByteBuf buffer) {
        buffer.writeByte(PAYLOAD_SCHEMA);
        buffer.writeBoolean(packet.context().isPresent());
        if (packet.context().isEmpty()) {
            return;
        }
        Context context = packet.context().orElseThrow();
        buffer.writeByte(context.kind().ordinal());
        buffer.writeBoolean(context.body().isPresent());
        context.body().ifPresent(id -> buffer.writeUtf(id.toString(), BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS));
        StationOrbitalControls controls = context.controls();
        buffer.writeShort(controls.distance());
        buffer.writeInt(controls.eastPhase());
        buffer.writeInt(controls.upPhase());
        buffer.writeInt(controls.northPhase());
        buffer.writeByte(controls.eastRate());
        buffer.writeByte(controls.upRate());
        buffer.writeByte(controls.northRate());
        buffer.writeLong(controls.epoch());
        buffer.writeLong(context.sample());
    }

    public static StationSkyContextPacket decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_BYTES) {
            throw new DecoderException("Station sky context exceeds " + MAX_BYTES + " bytes");
        }
        if (buffer.readByte() != PAYLOAD_SCHEMA) {
            throw new DecoderException("Unsupported station sky context payload schema");
        }
        if (!readFlag(buffer, "context presence")) {
            requireEnd(buffer);
            return NONE;
        }
        byte kind = buffer.readByte();
        if (kind < 0 || kind >= SkyKind.values().length) {
            throw new DecoderException("Unknown station sky kind " + kind);
        }
        Optional<ResourceLocation> body = Optional.empty();
        if (readFlag(buffer, "body presence")) {
            String raw = buffer.readUtf(BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS);
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null || !id.toString().equals(raw)) {
                throw new DecoderException("Invalid or non-canonical station sky body");
            }
            body = Optional.of(id);
        }
        int distance = buffer.readUnsignedShort();
        int east = buffer.readInt();
        int up = buffer.readInt();
        int north = buffer.readInt();
        byte eastRate = buffer.readByte();
        byte upRate = buffer.readByte();
        byte northRate = buffer.readByte();
        long epoch = buffer.readLong();
        long sample = buffer.readLong();
        requireEnd(buffer);
        try {
            return new StationSkyContextPacket(Optional.of(new Context(SkyKind.values()[kind], body,
                    new StationOrbitalControls(distance, east, up, north, eastRate, upRate, northRate, epoch), sample)));
        } catch (IllegalArgumentException invalid) {
            throw new DecoderException("Station sky context is outside its ranges", invalid);
        }
    }

    public static void handle(StationSkyContextPacket packet,
                              java.util.function.Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        StationSkyContextCache.accept(packet);
        context.setPacketHandled(true);
    }

    private static boolean readFlag(FriendlyByteBuf buffer, String name) {
        byte flag = buffer.readByte();
        if (flag != 0 && flag != 1) {
            throw new DecoderException("Station sky " + name + " must be 0 or 1");
        }
        return flag == 1;
    }

    private static void requireEnd(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new DecoderException("Trailing bytes after the station sky context");
        }
    }

    /** The stable wire bytes of the sky kind; the server alone chooses it. */
    public enum SkyKind {
        /** The body is missing from the server catalog (it may still be named) or none is known. */
        SPACE_FALLBACK,
        /** A current catalog body. */
        PLANETARY,
        /** A current catalog body with an accepted ADR-057 singularity profile. */
        SINGULARITY
    }

    /**
     * One station context: a planetary or singularity kind requires a body; the epoch is at or before the
     * nonnegative sample, which is the server's logical orbital time when the context was built.
     */
    public record Context(SkyKind kind, Optional<ResourceLocation> body, StationOrbitalControls controls, long sample) {
        public Context {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(controls, "controls");
            body.ifPresent(id -> BoundedCelestialCodecs.requireId(id, "orbit body"));
            if (kind != SkyKind.SPACE_FALLBACK && body.isEmpty()) {
                throw new IllegalArgumentException("A planetary or singularity sky needs its body");
            }
            if (sample < 0L || controls.epoch() > sample) {
                throw new IllegalArgumentException("The station sky epoch must be at or before the sample");
            }
        }

        /** The same context without its sample time: what a change of context means. */
        public Context basis() {
            return new Context(kind, body, controls, controls.epoch());
        }
    }
}

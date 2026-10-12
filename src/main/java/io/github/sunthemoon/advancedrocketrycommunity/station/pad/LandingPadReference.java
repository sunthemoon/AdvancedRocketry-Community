package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

/** Coordinates are deliberately absent; a station TravelTarget supplies the station binding. */
public record LandingPadReference(UUID padId, long epoch) {
    public static final int MAX_WIRE_BYTES = 25;

    public LandingPadReference {
        Objects.requireNonNull(padId, "padId");
        if (epoch < 0) { throw new IllegalArgumentException("Negative landing-pad epoch"); }
    }

    public static void writeOptional(FriendlyByteBuf buffer, LandingPadReference reference) {
        buffer.writeByte(reference == null ? 0 : 1);
        if (reference != null) { buffer.writeUUID(reference.padId()); buffer.writeLong(reference.epoch()); }
    }

    public static LandingPadReference readOptional(FriendlyByteBuf buffer) {
        int flag = buffer.readUnsignedByte();
        if (flag > 1) { throw new IllegalArgumentException("Invalid landing-pad presence flag"); }
        return flag == 0 ? null : new LandingPadReference(buffer.readUUID(), buffer.readLong());
    }
}

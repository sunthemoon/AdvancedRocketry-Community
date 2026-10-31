package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public final class SatelliteNbtSize {
    private SatelliteNbtSize() {
    }

    public static int uncompressedBytes(CompoundTag tag) {
        try (DataOutputStream output = new DataOutputStream(OutputStream.nullOutputStream())) {
            // As in RocketNbtSize: execute the native writer, including modified UTF, without retaining counted bytes.
            // The stream's count saturates at Integer.MAX_VALUE, above every satellite bound, rather than wrapping.
            NbtIo.write(tag, output);
            return output.size();
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory satellite NBT sizing failed", exception);
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

public final class RocketNbtSize {
    private RocketNbtSize() {
    }

    public static int uncompressedBytes(CompoundTag tag) {
        try (DataOutputStream output = new DataOutputStream(OutputStream.nullOutputStream())) {
            // Use the real writer (including modified UTF), but do not retain bytes needed only for their count.
            // DataOutputStream saturates at Integer.MAX_VALUE, above every NBT bound, rather than wrapping.
            NbtIo.write(tag, output);
            return output.size();
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory NBT sizing failed", exception);
        }
    }
}

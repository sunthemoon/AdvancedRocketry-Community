package io.github.sunthemoon.advancedrocketrycommunity.travel.persistence;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

final class TravelTargetNbtSize {
    private TravelTargetNbtSize() {
    }

    static int uncompressedBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(tag, output);
            }
            return bytes.size();
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory travel target NBT sizing failed", exception);
        }
    }
}

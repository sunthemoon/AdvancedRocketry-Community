package io.github.sunthemoon.advancedrocketrycommunity.sound;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Transient client projection only; never includes a machine's saved roots. */
public final class ProcessingFlagCodec {
    private ProcessingFlagCodec() { }

    public static CompoundTag encode(boolean active) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        return tag;
    }

    /** NBT has no boolean type. Only the canonical boolean byte 1 means active. */
    public static boolean decode(@Nullable CompoundTag tag) {
        return tag != null && tag.contains("active", Tag.TAG_BYTE) && tag.getByte("active") == 1;
    }
}

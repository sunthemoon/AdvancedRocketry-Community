package io.github.sunthemoon.advancedrocketrycommunity.travel.persistence;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/** Strict NBT boundary for one independently versioned travel target. */
public final class TravelTargetNbtCodec {
    public static final int MAX_TARGET_NBT_BYTES = 256;

    private TravelTargetNbtCodec() {
    }

    public static DataResult<CompoundTag> encode(TravelTarget target) {
        return TravelTarget.CODEC.encodeStart(NbtOps.INSTANCE, target)
                .flatMap(TravelTargetNbtCodec::requireCompound)
                .flatMap(TravelTargetNbtCodec::requireBound);
    }

    public static DataResult<TravelTarget> decode(Tag source) {
        return requireCompound(source)
                .flatMap(TravelTargetNbtCodec::requireBound)
                .flatMap(tag -> TravelTarget.CODEC.parse(NbtOps.INSTANCE, tag));
    }

    public static int encodedBytes(CompoundTag source) {
        return TravelTargetNbtSize.uncompressedBytes(source);
    }

    private static DataResult<CompoundTag> requireCompound(Tag source) {
        if (!(source instanceof CompoundTag compound)) {
            return DataResult.error(() -> "Travel target NBT root must be a compound");
        }
        return DataResult.success(compound);
    }

    private static DataResult<CompoundTag> requireBound(CompoundTag source) {
        int encodedBytes = encodedBytes(source);
        if (encodedBytes > MAX_TARGET_NBT_BYTES) {
            return DataResult.error(() -> "Travel target NBT exceeds 256 bytes: " + encodedBytes);
        }
        return DataResult.success(source);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Defensive, adapter-owned BlockEntity payload. World coordinates are not permitted. */
public final class RocketBlockEntityPayload {
    private final ResourceLocation adapterId;
    private final CompoundTag data;
    private final int uncompressedBytes;

    public RocketBlockEntityPayload(ResourceLocation adapterId, CompoundTag data) {
        this.adapterId = Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(data, "data");
        if (adapterId.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw invalid("BlockEntity adapter identifier exceeds the fixed length limit");
        }
        if (data.contains("x") || data.contains("y") || data.contains("z") || data.contains("id")) {
            throw invalid("Approved BlockEntity payload must not contain world identity fields");
        }
        this.data = data.copy();
        uncompressedBytes = RocketNbtSize.uncompressedBytes(this.data);
        if (uncompressedBytes > RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES) {
            throw new RocketSnapshotException(
                    RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE,
                    "BlockEntity payload is " + uncompressedBytes + " bytes; limit is "
                            + RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES
            );
        }
    }

    public ResourceLocation adapterId() {
        return adapterId;
    }

    public CompoundTag data() {
        return data.copy();
    }

    public int uncompressedBytes() {
        return uncompressedBytes;
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketBlockEntityPayload other
                && adapterId.equals(other.adapterId)
                && data.equals(other.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(adapterId, data);
    }

    private static RocketSnapshotException invalid(String message) {
        return new RocketSnapshotException(RocketValidationCode.INVALID_BLOCK_ENTITY_DATA, message);
    }
}

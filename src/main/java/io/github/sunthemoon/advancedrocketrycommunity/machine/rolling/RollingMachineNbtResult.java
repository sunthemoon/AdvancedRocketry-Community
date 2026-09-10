package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.Tag;

/** Immutable decode result shared by the independent Rolling process roots. */
record RollingMachineNbtResult<T>(
        MultiblockNbtStatus status,
        Optional<T> value,
        Optional<Tag> preservedRoot
) {
    RollingMachineNbtResult {
        Objects.requireNonNull(status, "status");
        value = Objects.requireNonNull(value, "value");
        preservedRoot = Objects.requireNonNull(preservedRoot, "preservedRoot").map(Tag::copy);
    }

    static <T> RollingMachineNbtResult<T> empty() {
        return new RollingMachineNbtResult<>(
                MultiblockNbtStatus.EMPTY,
                Optional.empty(),
                Optional.empty()
        );
    }

    static <T> RollingMachineNbtResult<T> supported(T value) {
        return new RollingMachineNbtResult<>(
                MultiblockNbtStatus.SUPPORTED,
                Optional.of(value),
                Optional.empty()
        );
    }

    static <T> RollingMachineNbtResult<T> rejected(MultiblockNbtStatus status, Tag root) {
        if (status == MultiblockNbtStatus.EMPTY || status == MultiblockNbtStatus.SUPPORTED) {
            throw new IllegalArgumentException("rejected NBT requires a blocking status");
        }
        return new RollingMachineNbtResult<>(status, Optional.empty(), Optional.ofNullable(root));
    }

    @Override
    public Optional<Tag> preservedRoot() {
        return preservedRoot.map(Tag::copy);
    }
}

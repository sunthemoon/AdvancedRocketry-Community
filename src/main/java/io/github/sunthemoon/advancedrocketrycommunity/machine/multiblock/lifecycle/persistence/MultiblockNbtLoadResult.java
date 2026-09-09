package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Load result that preserves unsupported or damaged roots instead of rewriting them. */
public record MultiblockNbtLoadResult<T>(
        MultiblockNbtStatus status,
        Optional<T> value,
        Optional<Tag> preservedRoot
) {
    public MultiblockNbtLoadResult {
        Objects.requireNonNull(status, "status");
        value = Objects.requireNonNull(value, "value");
        preservedRoot = Objects.requireNonNull(preservedRoot, "preservedRoot").map(Tag::copy);
        if ((status == MultiblockNbtStatus.SUPPORTED) != value.isPresent()) {
            throw new IllegalArgumentException("supported NBT results require exactly one value");
        }
        if ((status == MultiblockNbtStatus.UNSUPPORTED_SCHEMA || status == MultiblockNbtStatus.INVALID_DATA)
                != preservedRoot.isPresent()) {
            throw new IllegalArgumentException("rejected NBT results require preserved source data");
        }
    }

    public static <T> MultiblockNbtLoadResult<T> empty() {
        return new MultiblockNbtLoadResult<>(MultiblockNbtStatus.EMPTY, Optional.empty(), Optional.empty());
    }

    public static <T> MultiblockNbtLoadResult<T> supported(T value) {
        return new MultiblockNbtLoadResult<>(
                MultiblockNbtStatus.SUPPORTED,
                Optional.of(Objects.requireNonNull(value, "value")),
                Optional.empty()
        );
    }

    public static <T> MultiblockNbtLoadResult<T> rejected(
            MultiblockNbtStatus status,
            Tag source
    ) {
        if (status != MultiblockNbtStatus.UNSUPPORTED_SCHEMA && status != MultiblockNbtStatus.INVALID_DATA) {
            throw new IllegalArgumentException("rejected NBT status is not preservable");
        }
        return new MultiblockNbtLoadResult<>(status, Optional.empty(), Optional.of(source));
    }

    public void writeRoot(CompoundTag parent, String rootName, Function<T, CompoundTag> encoder) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(rootName, "rootName");
        Objects.requireNonNull(encoder, "encoder");
        if (value.isPresent()) {
            parent.put(rootName, encoder.apply(value.get()));
        } else if (preservedRoot.isPresent()) {
            parent.put(rootName, preservedRoot.get().copy());
        } else {
            parent.remove(rootName);
        }
    }

    @Override
    public Optional<Tag> preservedRoot() {
        return preservedRoot.map(Tag::copy);
    }
}

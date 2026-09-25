package io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.Tag;

/** Immutable decode result that retains rejected roots for lossless fail-closed saves. */
public record ProcessNbtResult<T>(
        ProcessNbtStatus status,
        Optional<T> value,
        Optional<Tag> preservedRoot
) {
    public ProcessNbtResult {
        Objects.requireNonNull(status, "status");
        value = Objects.requireNonNull(value, "value");
        preservedRoot = Objects.requireNonNull(preservedRoot, "preservedRoot").map(Tag::copy);
    }

    public static <T> ProcessNbtResult<T> empty() {
        return new ProcessNbtResult<>(ProcessNbtStatus.EMPTY, Optional.empty(), Optional.empty());
    }

    public static <T> ProcessNbtResult<T> supported(T value) {
        return new ProcessNbtResult<>(
                ProcessNbtStatus.SUPPORTED,
                Optional.of(Objects.requireNonNull(value, "value")),
                Optional.empty()
        );
    }

    public static <T> ProcessNbtResult<T> rejected(ProcessNbtStatus status, Tag root) {
        if (status == ProcessNbtStatus.EMPTY || status == ProcessNbtStatus.SUPPORTED) {
            throw new IllegalArgumentException("rejected process NBT requires a blocking status");
        }
        return new ProcessNbtResult<>(status, Optional.empty(), Optional.ofNullable(root));
    }

    @Override
    public Optional<Tag> preservedRoot() {
        return preservedRoot.map(Tag::copy);
    }
}

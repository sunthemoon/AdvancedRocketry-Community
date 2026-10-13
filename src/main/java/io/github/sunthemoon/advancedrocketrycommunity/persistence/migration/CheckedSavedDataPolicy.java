package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** Per-type bounded writer policy; the transfer journal retains its existing ~140 MiB quota. */
public record CheckedSavedDataPolicy(ManagedSavedDataType type, CheckedSavedDataFile.Committer committer) {
    public CheckedSavedDataPolicy { Objects.requireNonNull(type); Objects.requireNonNull(committer); }
    public static CheckedSavedDataPolicy atomic(ManagedSavedDataType type) {
        return new CheckedSavedDataPolicy(type, CheckedSavedDataFile::atomicMove);
    }
    public void replace(Path file, Supplier<CompoundTag> payload) {
        CheckedSavedDataFile.replace(file, type, payload, committer);
    }
    public Optional<CompoundTag> read(Path file) { return CheckedSavedDataFile.readPayload(file, type); }
    public long maxUncompressedBytes() { return type.maxUncompressedBytes(); }

    /** Windows has no portable directory-channel force; staging data is still forced before atomic move. */
    public static void forceDirectory(Path directory) throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) { return; }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }
}

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

/**
 * Per-type bounded writer policy; the transfer journal retains its existing ~140 MiB quota. The readback
 * {@code reader} is a component so a gated release-test seam can replace it (W44 section 6.1); every production
 * policy reads with {@link CheckedSavedDataFile#readPayload}.
 */
public record CheckedSavedDataPolicy(ManagedSavedDataType type, CheckedSavedDataFile.Committer committer,
                                     Reader reader) {
    public CheckedSavedDataPolicy {
        Objects.requireNonNull(type); Objects.requireNonNull(committer); Objects.requireNonNull(reader);
    }
    public CheckedSavedDataPolicy(ManagedSavedDataType type, CheckedSavedDataFile.Committer committer) {
        this(type, committer, CheckedSavedDataFile::readPayload);
    }
    public static CheckedSavedDataPolicy atomic(ManagedSavedDataType type) {
        return new CheckedSavedDataPolicy(type, CheckedSavedDataFile::atomicMove);
    }
    public CheckedSavedDataPolicy withCommitter(CheckedSavedDataFile.Committer replacement) {
        return new CheckedSavedDataPolicy(type, replacement, reader);
    }
    public void replace(Path file, Supplier<CompoundTag> payload) {
        CheckedSavedDataFile.replace(file, type, payload, committer);
    }
    public Optional<CompoundTag> read(Path file) { return reader.read(file, type); }
    public long maxUncompressedBytes() { return type.maxUncompressedBytes(); }

    /** Windows has no portable directory-channel force; staging data is still forced before atomic move. */
    public static void forceDirectory(Path directory) throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) { return; }
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }

    /** The bounded readback of a written root; empty when the regular file is absent. */
    @FunctionalInterface
    public interface Reader {
        Optional<CompoundTag> read(Path file, ManagedSavedDataType type);
    }

    /** Directory durability after a replacement: the journal checkpoint, the station writer and the progression barrier. */
    @FunctionalInterface
    public interface DirectoryForce {
        DirectoryForce PLATFORM = CheckedSavedDataPolicy::forceDirectory;

        void force(Path directory) throws IOException;
    }
}

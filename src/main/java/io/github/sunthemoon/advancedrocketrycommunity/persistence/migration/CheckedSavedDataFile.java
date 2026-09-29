package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Forced, read-back-validated, atomically replaced SavedData file; no non-atomic fallback. */
public final class CheckedSavedDataFile {
    private CheckedSavedDataFile() {
    }

    public static void replace(Path file, ManagedSavedDataType type, Supplier<CompoundTag> payload) {
        replace(file, type, payload, CheckedSavedDataFile::atomicMove);
    }

    public static void atomicMove(Path staged, Path target) throws IOException {
        Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Throws before acknowledging; the target is replaced only by {@code committer}. */
    public static void replace(Path supplied, ManagedSavedDataType type, Supplier<CompoundTag> payload,
                               Committer committer) {
        Path file = supplied.toAbsolutePath().normalize();
        Path pending = file.resolveSibling(type.fileName() + ".arce-pending");
        boolean staged = false;
        try {
            if (!file.getFileName().toString().equals(type.fileName())) {
                throw new IOException("SavedData target does not match its stable identity");
            }
            for (Path parent = file.getParent(); parent != null; parent = parent.getParent()) {
                var attributes = stat(parent);
                if (attributes == null || !attributes.isDirectory()) {
                    throw new IOException("SavedData ancestry must contain only real directories");
                }
            }
            requireFileOrAbsent(file);
            requireFileOrAbsent(pending);
            CompoundTag outer = new CompoundTag();
            outer.put("data", payload.get());
            NbtUtils.addCurrentDataVersion(outer);
            byte[] encoded = BoundedSavedDataIo.write(outer, type);
            try (var output = FileChannel.open(pending, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                staged = true;
                var buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) { output.write(buffer); }
                output.force(true);
            }
            if (!outer.equals(BoundedSavedDataIo.read(pending, type))) {
                throw new IOException("SavedData staging readback differs");
            }
            requireFileOrAbsent(file);
            committer.commit(pending, file);
        } catch (IOException | RuntimeException exception) {
            if (staged) {
                try { Files.deleteIfExists(pending); }
                catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            }
            if (exception instanceof IOException io) { throw new UncheckedIOException(io); }
            throw (RuntimeException) exception;
        }
    }

    /** Bounded read of the current payload; empty when the regular file is absent. */
    public static Optional<CompoundTag> readPayload(Path supplied, ManagedSavedDataType type) {
        Path file = supplied.toAbsolutePath().normalize();
        try {
            var attributes = stat(file);
            if (attributes == null) {
                return Optional.empty();
            }
            if (!attributes.isRegularFile()) {
                throw new IOException("SavedData authority must be a regular file");
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return Optional.of(BoundedSavedDataIo.read(file, type).getCompound("data"));
    }

    private static void requireFileOrAbsent(Path file) throws IOException {
        var attributes = stat(file);
        if (attributes != null && !attributes.isRegularFile()) {
            throw new IOException("SavedData authority and staging paths must be regular files");
        }
    }

    private static BasicFileAttributes stat(Path path) throws IOException {
        try { return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); }
        catch (NoSuchFileException absent) { return null; }
    }

    @FunctionalInterface
    public interface Committer {
        void commit(Path staged, Path target) throws IOException;
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;

/**
 * The file operations of one checked write (W368-DESIGN section 2.1): the staging open, write, force, close and
 * readback of {@link CheckedSavedDataFile#replace}, the atomic move that every committer reaches, the directory force
 * and the staging cleanup. {@link #DIRECT} is the only production implementation. A gated release-test installer
 * ({@link ReleaseTestStorageTrace}) may replace it with a recorder that calls {@code DIRECT} and never changes a
 * result.
 */
interface CheckedFileOps {
    /** Opens the staging file for one write; it is created, or truncated if a leftover exists. */
    FileChannel open(Path pending) throws IOException;

    /** One write call on the staging channel; returns the bytes it accepted. */
    int write(FileChannel channel, ByteBuffer buffer) throws IOException;

    void force(FileChannel channel) throws IOException;

    void close(FileChannel channel) throws IOException;

    /** The bounded readback of the closed staging file. */
    CompoundTag readStaged(Path pending, ManagedSavedDataType type);

    void move(Path staged, Path target) throws IOException;

    void forceDirectory(Path directory) throws IOException;

    /** The staging cleanup after a failed write. */
    void delete(Path pending) throws IOException;

    /** The file operations as they were before the seam existed, call for call. */
    CheckedFileOps DIRECT = new Direct();

    /** Stateless; its initializer constructs nothing and reads no world, registry or configuration. */
    final class Direct implements CheckedFileOps {
        private Direct() {
        }

        @Override
        public FileChannel open(Path pending) throws IOException {
            return FileChannel.open(pending, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public int write(FileChannel channel, ByteBuffer buffer) throws IOException {
            return channel.write(buffer);
        }

        @Override
        public void force(FileChannel channel) throws IOException {
            channel.force(true);
        }

        @Override
        public void close(FileChannel channel) throws IOException {
            channel.close();
        }

        @Override
        public CompoundTag readStaged(Path pending, ManagedSavedDataType type) {
            return BoundedSavedDataIo.read(pending, type);
        }

        @Override
        public void move(Path staged, Path target) throws IOException {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        /** Windows has no portable directory-channel force; staging data is still forced before atomic move. */
        @Override
        public void forceDirectory(Path directory) throws IOException {
            if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) { return; }
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
        }

        @Override
        public void delete(Path pending) throws IOException {
            Files.deleteIfExists(pending);
        }

        @Override
        public String toString() {
            return "CheckedFileOps.DIRECT";
        }
    }
}

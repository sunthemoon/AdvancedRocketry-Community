package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import com.mojang.logging.LogUtils;
import java.io.File;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;

/** Checked replacement for the two discovery authorities, including ordinary autosave. */
public abstract class AtomicSavedData extends SavedData {
    private final ManagedSavedDataType type;
    private boolean saveFailureReported;

    protected AtomicSavedData(ManagedSavedDataType type) {
        if (type != ManagedSavedDataType.CELESTIAL && type != ManagedSavedDataType.SATELLITE_MISSIONS) {
            throw new IllegalArgumentException("Atomic discovery storage only supports its two authorities");
        }
        this.type = type;
    }

    @Override
    public final void save(File file) {
        if (!isDirty()) { return; }
        try {
            flush(file.toPath());
        } catch (RuntimeException exception) {
            if (!saveFailureReported) {
                LogUtils.getLogger().error("Atomic SavedData save failed for {}; retained dirty state", type.dataName(), exception);
                saveFailureReported = true;
            }
        }
    }

    /** Always acknowledge the current snapshot, even after an unchanged replay result. */
    public final void flush(MinecraftServer server) {
        flush(server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(type.fileName()));
    }

    public final void flush(Path file) {
        flush(file, (from, to) -> Files.move(from, to,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    final void flush(Path supplied, Committer committer) {
        Path file = supplied.toAbsolutePath().normalize();
        Path pending = file.resolveSibling(type.fileName() + ".arce-pending");
        boolean staged = false;
        // A failed explicit barrier must also remain eligible for a later ordinary save.
        setDirty();
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
            outer.put("data", save(new CompoundTag()));
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
        // No fallible operation follows replacement before acknowledgment.
        setDirty(false);
        saveFailureReported = false;
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
    interface Committer {
        void commit(Path staged, Path target) throws IOException;
    }
}

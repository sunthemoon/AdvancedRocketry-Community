package io.github.sunthemoon.advancedrocketrycommunity.celestial.binding;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;

/** World-owned acknowledged persistence; never replaces an unreadable or externally changed authority. */
public final class PlanetaryBindingStore {
    public static final String FILE_NAME = "advancedrocketrycommunity_planetary_bindings.json";
    public static final String PENDING_NAME = FILE_NAME + ".pending";
    private final Path file;
    private final Path pending;
    private final Committer committer;
    private final Inspector inspector;
    private byte[] acceptedBytes;
    private PlanetaryBindings bindings;

    private PlanetaryBindingStore(Path directory, Committer committer, Inspector inspector) {
        file = directory.resolve(FILE_NAME);
        pending = directory.resolve(PENDING_NAME);
        this.committer = committer;
        this.inspector = inspector;
    }

    public static PlanetaryBindingStore open(Path worldRoot, CelestialCatalog initial) throws IOException {
        return open(worldRoot, initial, (from, to) -> Files.move(from, to,
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    static PlanetaryBindingStore open(Path worldRoot, CelestialCatalog initial, Committer committer) throws IOException {
        return open(worldRoot, initial, committer,
                path -> Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));
    }

    static PlanetaryBindingStore open(Path worldRoot, CelestialCatalog initial, Committer committer,
            Inspector inspector) throws IOException {
        Path root = worldRoot.toAbsolutePath().normalize();
        Path directory = root.resolve("data");
        PlanetaryBindingStore store = new PlanetaryBindingStore(directory, committer, inspector);
        store.requireDirectory(root);
        if (store.stat(directory) == null) {
            Files.createDirectory(directory);
        }
        store.requireDirectory(directory);
        if (store.stat(store.pending) != null) {
            throw new IOException("Unfinished planetary binding commit; keep the world stopped and recover from a complete backup");
        }
        if (store.stat(store.file) != null) {
            store.acceptedBytes = store.read(store.file);
            store.bindings = PlanetaryBindingsCodec.decode(store.acceptedBytes);
        }
        store.accept(initial);
        return store;
    }

    public synchronized PlanetaryBindings current() {
        return bindings;
    }

    public synchronized void accept(CelestialCatalog catalog) throws IOException {
        PlanetaryBindings next = bindings == null ? PlanetaryBindings.adopt(catalog) : bindings.reconcile(catalog);
        verifyUnchanged();
        if (next == bindings) {
            return;
        }
        byte[] bytes = PlanetaryBindingsCodec.encode(next);
        boolean staged = false;
        try {
            try (FileChannel output = FileChannel.open(pending, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                staged = true;
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                output.force(true);
            }
            byte[] readback = read(pending);
            if (!Arrays.equals(bytes, readback)
                    || !next.entries().equals(PlanetaryBindingsCodec.decode(readback).entries())) {
                throw new IOException("Planetary binding staging readback differs");
            }
            verifyUnchanged();
            committer.commit(pending, file);
        } catch (IOException | RuntimeException exception) {
            if (staged) {
                try {
                    Files.deleteIfExists(pending);
                } catch (IOException cleanup) {
                    exception.addSuppressed(cleanup);
                }
            }
            throw exception;
        }
        // Nothing fallible follows the acknowledged replacement before the in-memory authority advances.
        acceptedBytes = bytes;
        bindings = next;
    }

    private void verifyUnchanged() throws IOException {
        requireDirectory(file.getParent());
        boolean exists = stat(file) != null;
        if (acceptedBytes == null ? exists : !exists || !Arrays.equals(acceptedBytes, read(file))) {
            throw new IOException("Planetary binding file changed or disappeared while the world was running");
        }
    }

    private byte[] read(Path file) throws IOException {
        BasicFileAttributes attributes = stat(file);
        if (attributes == null || !attributes.isRegularFile()) {
            throw new IOException("Planetary binding path must be a regular file");
        }
        try (var input = Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(PlanetaryBindingsCodec.MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > PlanetaryBindingsCodec.MAX_BYTES) {
                throw new IOException("Planetary binding file is empty or exceeds its byte limit");
            }
            return bytes;
        }
    }

    private void requireDirectory(Path path) throws IOException {
        for (Path ancestor = path; ancestor != null; ancestor = ancestor.getParent()) {
            BasicFileAttributes attributes = stat(ancestor);
            if (attributes == null || !attributes.isDirectory()) {
                throw new IOException("Planetary binding world/data ancestry must contain only real directories");
            }
        }
    }

    private BasicFileAttributes stat(Path path) throws IOException {
        try {
            return inspector.inspect(path);
        } catch (NoSuchFileException absent) {
            return null;
        }
    }

    @FunctionalInterface
    interface Committer {
        void commit(Path staged, Path target) throws IOException;
    }

    @FunctionalInterface
    interface Inspector {
        BasicFileAttributes inspect(Path path) throws IOException;
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * The gated recorder of checked-write file operations (W368-DESIGN sections 2.2 and 6, task W369). It is installed
 * only on a release-test dedicated server that names a trace directory, and it is removed when that server stops.
 * It calls {@link CheckedFileOps#DIRECT} for every operation and never changes a result: when it cannot record, it
 * logs {@link #INVALID} once and stops recording. It adds no command, network message, configuration key or saved
 * field, and the trace lies outside the world directory.
 */
public final class ReleaseTestStorageTrace {
    public static final String HOOKS = "advancedrocketrycommunity.releaseTestHooks";
    public static final String PROPERTY = "advancedrocketrycommunity.storageTrace";
    public static final String INVALID = "ARCE_RELEASE_STORAGE_TRACE_INVALID";
    static final int MAX_OPERATIONS = 4_096;
    static final long MAX_BLOB_BYTES = 256L * 1_024L * 1_024L;
    static final String PENDING_SUFFIX = ".arce-pending";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Recorder installed;
    private static boolean listening;

    private ReleaseTestStorageTrace() {
    }

    /**
     * The trace directory this JVM asks for; null unless the release-test hooks property is true, the server is a
     * dedicated server and the trace property names a directory.
     */
    @Nullable
    static String requested(boolean hooks, boolean dedicated, @Nullable String property) {
        return hooks && dedicated && property != null && !property.isEmpty() ? property : null;
    }

    /**
     * Start-up hook, on the server thread. {@code releaseTestGate} is the open release-test gate
     * ({@code EscrowTestEndpoints.requireEnabled}); it throws on a server that is not a release-test server, as it
     * does for every other gated seam.
     */
    public static synchronized void installAtStartup(MinecraftServer server, Consumer<MinecraftServer> releaseTestGate) {
        restore();
        String directory = requested(Boolean.getBoolean(HOOKS), server.isDedicatedServer(), System.getProperty(PROPERTY));
        if (directory == null) {
            return;
        }
        releaseTestGate.accept(server);
        Recorder recorder = Recorder.open(directory, server.getWorldPath(LevelResource.ROOT), CheckedFileOps.DIRECT,
                MAX_OPERATIONS, MAX_BLOB_BYTES, LOGGER::error);
        if (recorder == null) {
            return;
        }
        if (!listening) {
            listening = true;
            // LOWEST: the writers that drain in their own stop listeners are still recorded.
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerStoppedEvent stopped) -> restore());
        }
        installed = recorder;
        CheckedSavedDataFile.installFileOps(recorder);
        LOGGER.info("ARCE_RELEASE_STORAGE_TRACE installed directory={}", recorder.directory);
    }

    /** Puts {@link CheckedFileOps#DIRECT} back and closes the trace. */
    static synchronized void restore() {
        CheckedSavedDataFile.installFileOps(null);
        Recorder recorder = installed;
        installed = null;
        if (recorder != null) {
            recorder.close();
            LOGGER.info("ARCE_RELEASE_STORAGE_TRACE closed operations={} blob_bytes={} valid={}",
                    recorder.operations(), recorder.storedBytes(), recorder.valid());
        }
    }

    /** Records each operation after {@code delegate} performed it. */
    static final class Recorder implements CheckedFileOps {
        private final CheckedFileOps delegate;
        private final Path directory;
        private final Path world;
        private final int maxOperations;
        private final long maxBytes;
        private final Consumer<String> marker;
        private final Object lock = new Object();
        private final Map<FileChannel, Staging> open = new ConcurrentHashMap<>();
        private final Set<String> blobs = new HashSet<>();
        private FileChannel lines;
        private boolean recording = true;
        private boolean valid = true;
        private int operations;
        private long storedBytes;

        private record Staging(Path pending, String store) {
        }

        /** What one operation adds to its line; it may store a blob or a copy of the old target. */
        @FunctionalInterface
        private interface Detail {
            void add(StringBuilder json, int sequence) throws IOException;
        }

        private Recorder(CheckedFileOps delegate, Path directory, Path world, int maxOperations, long maxBytes,
                         Consumer<String> marker, FileChannel lines) {
            this.delegate = delegate;
            this.directory = directory;
            this.world = world;
            this.maxOperations = maxOperations;
            this.maxBytes = maxBytes;
            this.marker = marker;
            this.lines = lines;
        }

        /**
         * A recorder over a new or empty trace directory outside {@code suppliedWorld}; null, after one marker, when
         * the directory is unusable.
         */
        @Nullable
        static Recorder open(String suppliedDirectory, Path suppliedWorld, CheckedFileOps delegate, int maxOperations,
                             long maxBytes, Consumer<String> marker) {
            try {
                Path world = suppliedWorld.toAbsolutePath().normalize();
                Path directory = Path.of(suppliedDirectory).toAbsolutePath().normalize();
                if (directory.startsWith(world)) {
                    marker.accept(INVALID + " reason=directory-inside-world operations=0");
                    return null;
                }
                Files.createDirectories(directory);
                Path realWorld = Files.exists(world) ? world.toRealPath() : world;
                if (directory.toRealPath().startsWith(realWorld)) {
                    marker.accept(INVALID + " reason=directory-inside-world operations=0");
                    return null;
                }
                try (var entries = Files.list(directory)) {
                    if (entries.findAny().isPresent()) {
                        marker.accept(INVALID + " reason=directory-not-empty operations=0");
                        return null;
                    }
                }
                Files.createDirectory(directory.resolve("blobs"));
                Files.createDirectory(directory.resolve("pre"));
                FileChannel lines = FileChannel.open(directory.resolve("ops.jsonl"), StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                return new Recorder(delegate, directory, world, maxOperations, maxBytes, marker, lines);
            } catch (IOException | InvalidPathException | SecurityException failure) {
                marker.accept(INVALID + " reason=" + failure.getClass().getSimpleName() + " operations=0");
                return null;
            }
        }

        int operations() {
            synchronized (lock) { return operations; }
        }

        long storedBytes() {
            synchronized (lock) { return storedBytes; }
        }

        /** False once the refusal marker was logged. */
        boolean valid() {
            synchronized (lock) { return valid; }
        }

        void close() {
            synchronized (lock) {
                recording = false;
                closeLines();
            }
            open.clear();
        }

        @Override
        public FileChannel open(Path pending) throws IOException {
            String name = pending.getFileName().toString();
            Path target = name.endsWith(PENDING_SUFFIX)
                    ? pending.resolveSibling(name.substring(0, name.length() - PENDING_SUFFIX.length())) : null;
            String store = store(target);
            FileChannel channel;
            try {
                channel = delegate.open(pending);
            } catch (IOException | RuntimeException failure) {
                record("open", store, pending, failure, null);
                throw failure;
            }
            open.put(channel, new Staging(pending, store));
            record("open", store, pending, null, (json, sequence) -> {
                if (target == null) {
                    return;
                }
                text(json.append(",\"target\":"), relative(target));
                preImage(json, sequence, target);
            });
            return channel;
        }

        @Override
        public int write(FileChannel channel, ByteBuffer buffer) throws IOException {
            Staging staging = open.get(channel);
            long offset = position(channel);
            ByteBuffer view = buffer.duplicate();
            int written;
            try {
                written = delegate.write(channel, buffer);
            } catch (IOException | RuntimeException failure) {
                int consumed = Math.max(0, buffer.position() - view.position());
                record("write", staging, failure, (json, sequence) ->
                        json.append(",\"offset\":").append(offset).append(",\"length\":").append(consumed));
                throw failure;
            }
            record("write", staging, null, (json, sequence) -> {
                int length = Math.max(0, Math.min(written, view.remaining()));
                requireBudget(length);
                byte[] bytes = new byte[length];
                view.get(bytes);
                json.append(",\"offset\":").append(offset).append(",\"length\":").append(length);
                text(json.append(",\"sha256\":"), blob(bytes));
            });
            return written;
        }

        @Override
        public void force(FileChannel channel) throws IOException {
            Staging staging = open.get(channel);
            try {
                delegate.force(channel);
            } catch (IOException | RuntimeException failure) {
                record("force", staging, failure, null);
                throw failure;
            }
            record("force", staging, null, null);
        }

        @Override
        public void close(FileChannel channel) throws IOException {
            Staging staging = open.remove(channel);
            try {
                delegate.close(channel);
            } catch (IOException | RuntimeException failure) {
                record("close", staging, failure, null);
                throw failure;
            }
            record("close", staging, null, null);
        }

        @Override
        public CompoundTag readStaged(Path pending, ManagedSavedDataType type) {
            CompoundTag read;
            try {
                read = delegate.readStaged(pending, type);
            } catch (RuntimeException failure) {
                record("readStaged", type.name(), pending, failure, null);
                throw failure;
            }
            record("readStaged", type.name(), pending, null, null);
            return read;
        }

        @Override
        public void move(Path staged, Path target) throws IOException {
            String store = store(target);
            Detail detail = (json, sequence) -> text(json.append(",\"target\":"), relative(target));
            try {
                delegate.move(staged, target);
            } catch (IOException | RuntimeException failure) {
                record("move", store, staged, failure, detail);
                throw failure;
            }
            record("move", store, staged, null, detail);
        }

        @Override
        public void forceDirectory(Path directory) throws IOException {
            try {
                delegate.forceDirectory(directory);
            } catch (IOException | RuntimeException failure) {
                record("forceDirectory", null, directory, failure, null);
                throw failure;
            }
            record("forceDirectory", null, directory, null, null);
        }

        @Override
        public void delete(Path pending) throws IOException {
            String name = pending.getFileName().toString();
            String store = store(name.endsWith(PENDING_SUFFIX)
                    ? pending.resolveSibling(name.substring(0, name.length() - PENDING_SUFFIX.length())) : null);
            try {
                delegate.delete(pending);
            } catch (IOException | RuntimeException failure) {
                record("delete", store, pending, failure, null);
                throw failure;
            }
            record("delete", store, pending, null, null);
        }

        private void record(String operation, @Nullable Staging staging, @Nullable Throwable failure,
                            @Nullable Detail detail) {
            record(operation, staging == null ? "UNKNOWN" : staging.store, staging == null ? null : staging.pending,
                    failure, detail);
        }

        /** Appends one line with one unbuffered write; any failure here ends the recording, never the operation. */
        private void record(String operation, @Nullable String store, @Nullable Path path, @Nullable Throwable failure,
                            @Nullable Detail detail) {
            synchronized (lock) {
                if (!recording) {
                    return;
                }
                try {
                    if (operations >= maxOperations) {
                        invalid("operation-bound");
                        return;
                    }
                    int sequence = operations;
                    StringBuilder json = new StringBuilder(256).append("{\"seq\":").append(sequence);
                    text(json.append(",\"thread\":"), Thread.currentThread().getName());
                    if (store == null) { json.append(",\"store\":null"); } else { text(json.append(",\"store\":"), store); }
                    text(json.append(",\"op\":"), operation);
                    if (path == null) { json.append(",\"path\":null"); } else { text(json.append(",\"path\":"), relative(path)); }
                    text(json.append(",\"result\":"), failure == null ? "ok" : failure.getClass().getName());
                    if (detail != null) {
                        detail.add(json, sequence);
                    }
                    ByteBuffer line = ByteBuffer.wrap(json.append("}\n").toString().getBytes(StandardCharsets.UTF_8));
                    while (line.hasRemaining()) { lines.write(line); }
                    operations = sequence + 1;
                } catch (BoundExceeded bound) {
                    invalid("blob-bound");
                } catch (IOException | RuntimeException problem) {
                    invalid(problem.getClass().getSimpleName());
                }
            }
        }

        private void invalid(String reason) {
            recording = false;
            valid = false;
            closeLines();
            try {
                marker.accept(INVALID + " reason=" + reason + " operations=" + operations);
            } catch (RuntimeException ignored) {
                // The marker sink must not reach the write either.
            }
        }

        private void closeLines() {
            FileChannel channel = lines;
            lines = null;
            if (channel != null) {
                try { channel.close(); } catch (IOException ignored) { /* the trace is already complete or invalid */ }
            }
        }

        /** The old content of the target of the write that this open starts: a copy, or a marker of its absence. */
        private void preImage(StringBuilder json, int sequence, Path target) throws IOException {
            BasicFileAttributes attributes;
            try {
                attributes = Files.readAttributes(target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException absent) {
                Files.createFile(directory.resolve("pre").resolve(sequence + ".absent"));
                json.append(",\"pre\":\"absent\"");
                return;
            }
            if (!attributes.isRegularFile()) {
                throw new IOException("The old target is not a regular file");
            }
            requireBudget(attributes.size());
            byte[] bytes = Files.readAllBytes(target);
            requireBudget(bytes.length);
            Files.write(directory.resolve("pre").resolve(Integer.toString(sequence)), bytes,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            storedBytes += bytes.length;
            text(json.append(",\"pre\":"), sha256(bytes));
            json.append(",\"preLength\":").append(bytes.length);
        }

        /** Stores the bytes once per SHA-256 and returns the digest. */
        private String blob(byte[] bytes) throws IOException {
            String digest = sha256(bytes);
            if (!blobs.contains(digest)) {
                requireBudget(bytes.length);
                Files.write(directory.resolve("blobs").resolve(digest), bytes,
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                storedBytes += bytes.length;
                blobs.add(digest);
            }
            return digest;
        }

        private void requireBudget(long more) {
            if (more > maxBytes - storedBytes) {
                throw new BoundExceeded();
            }
        }

        private String relative(Path supplied) {
            Path path = supplied.toAbsolutePath().normalize();
            return (path.startsWith(world) ? world.relativize(path) : path).toString().replace('\\', '/');
        }

        private static String store(@Nullable Path target) {
            if (target != null) {
                String name = target.getFileName().toString();
                for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
                    if (type.fileName().equals(name)) {
                        return type.name();
                    }
                }
            }
            return "UNKNOWN";
        }

        private static long position(FileChannel channel) {
            try {
                return channel.position();
            } catch (IOException | RuntimeException unknown) {
                return -1L;
            }
        }

        private static String sha256(byte[] bytes) {
            try {
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        private static void text(StringBuilder json, String value) {
            json.append('"');
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (character == '"' || character == '\\') {
                    json.append('\\').append(character);
                } else if (character < 0x20) {
                    json.append(String.format("\\u%04x", (int) character));
                } else {
                    json.append(character);
                }
            }
            json.append('"');
        }

        private static final class BoundExceeded extends RuntimeException {
            private static final long serialVersionUID = 1L;

            BoundExceeded() {
                super(null, null, false, false);
            }
        }
    }
}

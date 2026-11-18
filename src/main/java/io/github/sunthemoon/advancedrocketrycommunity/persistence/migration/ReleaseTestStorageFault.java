package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import org.slf4j.Logger;

/**
 * The gated injector of checked-write storage faults (W368-DESIGN sections 4 and 6, task W379). It is installed only
 * on a release-test dedicated server that names one fault as {@code <store>:<fault>:<count>[:<skip>]}, and it is
 * removed when that server stops. It acts on the named store only: the store's first {@code skip} writes after the
 * installation pass unchanged, the next {@code count} writes get the fault, and everything else goes to
 * {@link CheckedFileOps#DIRECT}. It adds no command, network message, configuration key or saved field.
 */
public final class ReleaseTestStorageFault {
    public static final String HOOKS = "advancedrocketrycommunity.releaseTestHooks";
    public static final String PROPERTY = "advancedrocketrycommunity.storageFault";
    public static final String MARKER = "ARCE_RELEASE_STORAGE_FAULT";
    public static final String INVALID = "ARCE_RELEASE_STORAGE_FAULT_INVALID";
    /** A count of 4 outlasts the writers' three-failure limit; the bound keeps a mistyped count from holding a server. */
    static final int MAX_COUNT = 16;
    /** With {@link #MAX_COUNT}, every skipped and every faulted write is among the reported attempts. */
    static final int MAX_SKIP = 48;
    /** The attempts of the named store that get an {@code attempt} line; later attempts are only counted. */
    static final int MAX_REPORTED_ATTEMPTS = 64;
    static final String PENDING_SUFFIX = ".arce-pending";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Injector installed;
    private static boolean listening;

    private ReleaseTestStorageFault() {
    }

    /** The fault kinds; the identifiers in brackets are the rows of W368-DESIGN section 4. */
    public enum Fault {
        /** [F1] The first write call of the attempt throws; nothing reaches the staging file. */
        WRITE_ERROR,
        /** [F2] The first write call stores half of its bytes and then throws "not enough space". */
        DISK_FULL,
        /** [F2, variant] The staging open throws "not enough space". */
        DISK_FULL_OPEN,
        /** [F3] The force throws; the staging data is not forced. */
        FLUSH_ERROR,
        /** [F4] The move throws before the rename. */
        MOVE_ERROR,
        /** [F4, variant] The move throws {@link AccessDeniedException} before the rename: a Windows sharing violation. */
        MOVE_DENIED,
        /** [F4, variant] The rename happens and the move then throws. */
        MOVE_AFTER,
        /** [F5] The channel is closed after a successful force, and the close then throws. */
        CLOSE_ERROR,
        /** [F6] Every write call of the attempt reports success and stores nothing. */
        LOST_WRITE,
        /** [F8] As {@link #WRITE_ERROR}, and the staging cleanup of that attempt throws without deleting. */
        DELETE_ERROR,
        /** [F9] As {@link #MOVE_AFTER}, and the next readback of the store throws. */
        READ_ERROR,
        /** [F9, variant] As {@link #MOVE_AFTER}, and the next readback of the store returns a different root. */
        READ_CORRUPT
    }

    /** One parsed fault; {@code skip} is the number of the store's writes that pass before the first faulted one. */
    record Spec(ManagedSavedDataType store, Fault fault, int count, int skip) {
        Spec(ManagedSavedDataType store, Fault fault, int count) {
            this(store, fault, count, 0);
        }

        /** Empty without a skip, so that the lines of the three-field form are the ones it always had. */
        String skipField() {
            return skip == 0 ? "" : " skip=" + skip;
        }
    }

    /**
     * The fault this JVM asks for; null unless the release-test hooks property is true, the server is a dedicated
     * server and the fault property is set.
     */
    @Nullable
    static String requested(boolean hooks, boolean dedicated, @Nullable String property) {
        return hooks && dedicated && property != null && !property.isEmpty() ? property : null;
    }

    /**
     * Parses {@code <store>:<fault>:<count>[:<skip>]}. The store is a {@link ManagedSavedDataType} name, the count is 1
     * to {@link #MAX_COUNT} and the skip is 0 to {@link #MAX_SKIP}; without the fourth field the skip is 0.
     */
    static Spec parse(String property) {
        String[] parts = property.split(":", -1);
        if (parts.length != 3 && parts.length != 4) {
            throw new IllegalArgumentException("form");
        }
        ManagedSavedDataType store;
        Fault fault;
        int count;
        int skip = 0;
        try {
            store = ManagedSavedDataType.valueOf(parts[0]);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("store");
        }
        try {
            fault = Fault.valueOf(parts[1]);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("fault");
        }
        try {
            count = Integer.parseInt(parts[2]);
        } catch (NumberFormatException unknown) {
            throw new IllegalArgumentException("count");
        }
        if (count < 1 || count > MAX_COUNT || !parts[2].equals(Integer.toString(count))) {
            throw new IllegalArgumentException("count");
        }
        if (parts.length == 4) {
            try {
                skip = Integer.parseInt(parts[3]);
            } catch (NumberFormatException unknown) {
                throw new IllegalArgumentException("skip");
            }
            if (skip < 0 || skip > MAX_SKIP || !parts[3].equals(Integer.toString(skip))) {
                throw new IllegalArgumentException("skip");
            }
        }
        return new Spec(store, fault, count, skip);
    }

    /**
     * Start-up hook, on the server thread, after {@link ReleaseTestStorageTrace#installAtStartup}.
     * {@code releaseTestGate} is the open release-test gate ({@code EscrowTestEndpoints.requireEnabled}); it throws on
     * a server that is not a release-test server, as it does for every other gated seam.
     */
    public static synchronized void installAtStartup(MinecraftServer server, Consumer<MinecraftServer> releaseTestGate) {
        restore();
        String property = requested(Boolean.getBoolean(HOOKS), server.isDedicatedServer(), System.getProperty(PROPERTY));
        if (property == null) {
            return;
        }
        releaseTestGate.accept(server);
        if (!install(property, LOGGER::info, LOGGER::error)) {
            return;
        }
        if (!listening) {
            listening = true;
            // LOWEST: the writers that drain in their own stop listeners still run under the injector.
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ServerStoppedEvent stopped) -> restore());
        }
    }

    /**
     * Installs the injector for {@code property}; false, after one {@link #INVALID} line, when the property is
     * malformed or another file-operations implementation (the recorder) is already installed.
     */
    static synchronized boolean install(String property, Consumer<String> info, Consumer<String> error) {
        Spec spec;
        try {
            spec = parse(property);
        } catch (IllegalArgumentException malformed) {
            error.accept(INVALID + " reason=malformed-" + malformed.getMessage());
            return false;
        }
        if (CheckedSavedDataFile.fileOps() != CheckedFileOps.DIRECT || CheckedSavedDataPolicy.readerWrapped()) {
            error.accept(INVALID + " reason=file-operations-already-replaced");
            return false;
        }
        Injector injector = new Injector(spec, CheckedFileOps.DIRECT, info);
        installed = injector;
        CheckedSavedDataFile.installFileOps(injector);
        CheckedSavedDataPolicy.installReaderWrapper(injector::wrap);
        info.accept(MARKER + " installed store=" + spec.store() + " fault=" + spec.fault() + " count=" + spec.count()
                + spec.skipField());
        return true;
    }

    /** Puts {@link CheckedFileOps#DIRECT} and the unwrapped readers back, if this class replaced them. */
    static synchronized void restore() {
        Injector injector = installed;
        installed = null;
        if (injector == null) {
            return;
        }
        if (CheckedSavedDataFile.fileOps() == injector) {
            CheckedSavedDataFile.installFileOps(null);
        }
        CheckedSavedDataPolicy.installReaderWrapper(null);
        injector.closed();
    }

    /**
     * Passes the first {@link Spec#skip} writes of {@link Spec#store} to the delegate and injects {@link Spec#fault}
     * into the {@link Spec#count} writes after them. One write (attempt) is one staging open with the operations that
     * follow it, so a retry of the same candidate is a write of its own. Each passed write, each injection, the end of
     * each attempt and a move that follows a lost write are reported as one {@link #MARKER} line, so a run's log shows
     * where the fault acted and whether the write failed in the session. The bookkeeping is synchronized; no delegated
     * operation runs under the lock.
     */
    static final class Injector implements CheckedFileOps {
        private final Spec spec;
        private final CheckedFileOps delegate;
        private final Consumer<String> report;
        private final String targetName;
        private final Map<FileChannel, Attempt> channels = new HashMap<>();
        private final Map<Path, Attempt> stagings = new HashMap<>();
        private int remaining;
        private int toSkip;
        private int injected;
        private int attempts;
        private int readFaults;

        private static final class Attempt {
            final int index;
            final MessageDigest digest = sha256();
            long bytes;
            boolean faulted;
            boolean passed;
            boolean lost;
            boolean moved;
            boolean keepStaging;

            Attempt(int index) {
                this.index = index;
            }
        }

        Injector(Spec spec, CheckedFileOps delegate, Consumer<String> report) {
            this.spec = spec;
            this.delegate = delegate;
            this.report = report;
            this.targetName = spec.store().fileName();
            this.remaining = spec.count();
            this.toSkip = spec.skip();
        }

        synchronized int injected() {
            return injected;
        }

        synchronized int remaining() {
            return remaining;
        }

        private void closed() {
            int total;
            int seen;
            synchronized (this) {
                total = injected;
                seen = attempts;
                remaining = 0;
                readFaults = 0;
                channels.clear();
                stagings.clear();
            }
            say(MARKER + " closed store=" + spec.store() + " fault=" + spec.fault() + " injected=" + total
                    + " attempts=" + seen + spec.skipField());
        }

        // ---- The file operations ------------------------------------------------------------------------------------

        @Override
        public FileChannel open(Path pending) throws IOException {
            if (!named(pending, targetName + PENDING_SUFFIX)) {
                return delegate.open(pending);
            }
            Attempt attempt;
            boolean refuse;
            synchronized (this) {
                attempt = new Attempt(++attempts);
                if (toSkip > 0) {
                    // This write comes before the faulted ones: every operation of it goes to the delegate.
                    toSkip--;
                    attempt.passed = true;
                    say(MARKER + " passed store=" + spec.store() + " fault=" + spec.fault() + " attempt=" + attempt.index
                            + " skip=" + spec.skip() + " left=" + toSkip);
                }
                refuse =spec.fault() == Fault.DISK_FULL_OPEN && arm(attempt, "open");
            }
            if (refuse) {
                ended(attempt, "open-failed");
                throw new IOException("ARCE injected fault: there is not enough space on the disk");
            }
            FileChannel channel = delegate.open(pending);
            synchronized (this) {
                channels.put(channel, attempt);
                stagings.put(key(pending), attempt);
            }
            return channel;
        }

        @Override
        public int write(FileChannel channel, ByteBuffer buffer) throws IOException {
            Attempt attempt;
            Fault act = null;
            synchronized (this) {
                attempt = channels.get(channel);
                if (attempt != null) {
                    Fault fault = spec.fault();
                    if (attempt.lost) {
                        act = Fault.LOST_WRITE;
                    } else if ((fault == Fault.WRITE_ERROR || fault == Fault.DISK_FULL || fault == Fault.LOST_WRITE
                            || fault == Fault.DELETE_ERROR) && arm(attempt, "write")) {
                        act = fault;
                        attempt.lost = fault == Fault.LOST_WRITE;
                        attempt.keepStaging = fault == Fault.DELETE_ERROR;
                    }
                }
            }
            if (attempt == null) {
                return delegate.write(channel, buffer);
            }
            if (act == Fault.LOST_WRITE) {
                // The caller is told that every byte was stored; none was.
                int dropped = buffer.remaining();
                count(attempt, buffer.duplicate());
                buffer.position(buffer.limit());
                return dropped;
            }
            if (act == Fault.DISK_FULL) {
                ByteBuffer half = buffer.duplicate();
                half.limit(half.position() + half.remaining() / 2);
                count(attempt, half.duplicate());
                while (half.hasRemaining()) { delegate.write(channel, half); }
                buffer.position(half.position());
                throw new IOException("ARCE injected fault: there is not enough space on the disk");
            }
            if (act != null) {
                throw new IOException("ARCE injected fault: write error");
            }
            ByteBuffer view = buffer.duplicate();
            int written = delegate.write(channel, buffer);
            view.limit(view.position() + Math.max(0, Math.min(written, view.remaining())));
            count(attempt, view);
            return written;
        }

        @Override
        public void force(FileChannel channel) throws IOException {
            boolean fail;
            synchronized (this) {
                Attempt attempt = channels.get(channel);
                fail = attempt != null && spec.fault() == Fault.FLUSH_ERROR && arm(attempt, "force");
            }
            if (fail) {
                throw new IOException("ARCE injected fault: flush error");
            }
            delegate.force(channel);
        }

        @Override
        public void close(FileChannel channel) throws IOException {
            boolean fail;
            synchronized (this) {
                Attempt attempt = channels.remove(channel);
                // A close after an earlier failure of the same attempt is not a second fault.
                fail = attempt != null && spec.fault() == Fault.CLOSE_ERROR && arm(attempt, "close");
            }
            // The handle is always released; a leaked handle would block every later move on Windows.
            delegate.close(channel);
            if (fail) {
                throw new IOException("ARCE injected fault: close error");
            }
        }

        @Override
        public CompoundTag readStaged(Path pending, ManagedSavedDataType type) {
            return delegate.readStaged(pending, type);
        }

        @Override
        public void move(Path staged, Path target) throws IOException {
            Attempt attempt;
            Fault act = null;
            boolean unseen;
            synchronized (this) {
                attempt = named(target, targetName) ? stagings.get(key(staged)) : null;
                unseen = attempt != null && attempt.lost;
                if (attempt != null) {
                    Fault fault = spec.fault();
                    if ((fault == Fault.MOVE_ERROR || fault == Fault.MOVE_DENIED || fault == Fault.MOVE_AFTER
                            || fault == Fault.READ_ERROR || fault == Fault.READ_CORRUPT) && arm(attempt, "move")) {
                        act = fault;
                    }
                }
            }
            if (attempt == null) {
                delegate.move(staged, target);
                return;
            }
            if (unseen) {
                // The write that lost its bytes passed the staging readback: the session did not see the fault.
                say(MARKER + " unseen store=" + spec.store() + " fault=" + spec.fault() + " operation=move attempt="
                        + attempt.index);
            }
            if (act == Fault.MOVE_ERROR) {
                throw new IOException("ARCE injected fault: move error");
            }
            if (act == Fault.MOVE_DENIED) {
                throw new AccessDeniedException(staged.toString(), target.toString(),
                        "ARCE injected fault: the file is used by another process");
            }
            delegate.move(staged, target);
            synchronized (this) {
                attempt.moved = true;
                if (act == Fault.READ_ERROR || act == Fault.READ_CORRUPT) {
                    readFaults++;
                }
            }
            if (act != null) {
                throw new IOException("ARCE injected fault: move error after the rename");
            }
            ended(attempt, "moved");
        }

        @Override
        public void forceDirectory(Path directory) throws IOException {
            delegate.forceDirectory(directory);
        }

        @Override
        public void delete(Path pending) throws IOException {
            Attempt attempt;
            synchronized (this) {
                attempt = stagings.remove(key(pending));
            }
            if (attempt == null) {
                delegate.delete(pending);
                return;
            }
            ended(attempt, "failed");
            if (attempt.keepStaging) {
                say(MARKER + " injected store=" + spec.store() + " fault=" + spec.fault()
                        + " operation=delete attempt=" + attempt.index + " remaining=" + remaining());
                throw new IOException("ARCE injected fault: staging cleanup error");
            }
            delegate.delete(pending);
        }

        // ---- The readback -------------------------------------------------------------------------------------------

        /** The {@link CheckedSavedDataPolicy.Reader} wrapper: the readback that follows a faulted move fails once. */
        CheckedSavedDataPolicy.Reader wrap(CheckedSavedDataPolicy.Reader reader) {
            return (file, type) -> {
                boolean fail;
                synchronized (this) {
                    fail = type == spec.store() && readFaults > 0;
                    if (fail) {
                        readFaults--;
                    }
                }
                if (!fail) {
                    return reader.read(file, type);
                }
                say(MARKER + " injected store=" + spec.store() + " fault=" + spec.fault() + " operation=read attempt=0"
                        + " remaining=" + remaining());
                if (spec.fault() == Fault.READ_ERROR) {
                    throw new UncheckedIOException(new IOException("ARCE injected fault: unreadable readback"));
                }
                CompoundTag different = reader.read(file, type).map(CompoundTag::copy).orElseGet(CompoundTag::new);
                different.putBoolean("arce_injected_read_corruption", true);
                return Optional.of(different);
            };
        }

        // ---- Bookkeeping --------------------------------------------------------------------------------------------

        /** Under the lock: takes one of the remaining faults for an attempt that has none yet and was not skipped. */
        private boolean arm(Attempt attempt, String operation) {
            if (attempt.passed || attempt.faulted || remaining <= 0) {
                return false;
            }
            attempt.faulted = true;
            remaining--;
            injected++;
            say(MARKER + " injected store=" + spec.store() + " fault=" + spec.fault() + " operation=" + operation
                    + " attempt=" + attempt.index + " remaining=" + remaining + spec.skipField());
            return true;
        }

        /** The bytes the caller handed over for this attempt, whether or not they were stored. */
        private void count(Attempt attempt, ByteBuffer consumed) {
            synchronized (this) {
                attempt.bytes += consumed.remaining();
                attempt.digest.update(consumed);
            }
        }

        private void ended(Attempt attempt, String result) {
            String digest;
            long bytes;
            boolean moved;
            boolean faulted;
            synchronized (this) {
                if (attempt.index > MAX_REPORTED_ATTEMPTS) {
                    return;
                }
                try {
                    digest = HexFormat.of().formatHex(((MessageDigest) attempt.digest.clone()).digest());
                } catch (CloneNotSupportedException impossible) {
                    throw new IllegalStateException(impossible);
                }
                bytes = attempt.bytes;
                moved = attempt.moved;
                faulted = attempt.faulted;
            }
            say(MARKER + " attempt store=" + spec.store() + " attempt=" + attempt.index + " result=" + result
                    + " moved=" + moved + " faulted=" + faulted + " bytes=" + bytes + " sha256=" + digest);
        }

        private void say(String line) {
            try {
                report.accept(line);
            } catch (RuntimeException ignored) {
                // The log sink must not add a failure that the fault did not ask for.
            }
        }

        private static boolean named(Path path, String name) {
            Path file = path.getFileName();
            return file != null && file.toString().equals(name);
        }

        private static Path key(Path path) {
            return path.toAbsolutePath().normalize();
        }

        private static MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        @Override
        public String toString() {
            return "ReleaseTestStorageFault[" + spec.store() + ":" + spec.fault().name().toLowerCase(Locale.ROOT) + "]";
        }
    }
}

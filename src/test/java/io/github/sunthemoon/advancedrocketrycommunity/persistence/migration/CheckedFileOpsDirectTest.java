package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W369: the seam holds {@link CheckedFileOps#DIRECT} by default, and {@code DIRECT} behaves as the code did before the
 * seam. {@link #legacyReplace} is that earlier code, copied from commit {@code 44dda90db}, as the reference.
 */
class CheckedFileOpsDirectTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.BAY_RECEIPTS;
    @TempDir Path root;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restore() { CheckedSavedDataFile.installFileOps(null); }

    @Test void theSeamHoldsDirectWithoutTheProperty() {
        assertNull(System.getProperty(ReleaseTestStorageTrace.PROPERTY));
        assertSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
        assertNull(ReleaseTestStorageTrace.requested(true, true, null));
        assertNull(ReleaseTestStorageTrace.requested(true, true, ""));
        assertNull(ReleaseTestStorageTrace.requested(false, true, "trace"));
        assertNull(ReleaseTestStorageTrace.requested(true, false, "trace"));
        assertNull(ReleaseTestStorageTrace.requested(false, false, "trace"));
        assertEquals("trace", ReleaseTestStorageTrace.requested(true, true, "trace"));
        CheckedSavedDataFile.installFileOps(new Spy(new ArrayList<>()));
        assertNotSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
        CheckedSavedDataFile.installFileOps(null);
        assertSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
    }

    @Test void aNormalWriteLeavesTheSameBytes() throws Exception {
        Scenario first = (file, writer) -> writer.replace(file, () -> payload(7), plainMove());
        assertSameOutcome(first, false);
        // A replacement of an existing target over a leftover staging file.
        Scenario second = (file, writer) -> {
            Files.write(file, new byte[] {1, 2, 3});
            Files.write(pending(file), new byte[4_096]);
            writer.replace(file, () -> payload(9), plainMove());
        };
        Outcome outcome = assertSameOutcome(second, false);
        assertNull(outcome.failure);
        assertFalse(outcome.pendingExists);
        Path check = Files.createDirectories(root.resolve("check")).resolve(TYPE.fileName());
        CheckedSavedDataFile.replace(check, TYPE, () -> payload(9));
        assertArrayEquals(Files.readAllBytes(check), outcome.target);
        assertEquals(payload(9), CheckedSavedDataFile.readPayload(check, TYPE).orElseThrow());
    }

    @Test void aFailedReadbackThrowsTheSameException() throws Exception {
        // NaN never equals itself, so the staged root differs from the candidate after an honest round trip.
        Scenario scenario = (file, writer) -> {
            Files.write(file, new byte[] {5});
            CompoundTag payload = payload(1);
            payload.putDouble("nan", Double.NaN);
            writer.replace(file, () -> payload, (staged, target) -> fail("The committer must not run"));
        };
        Outcome outcome = assertSameOutcome(scenario, true);
        assertTrue(outcome.failure.startsWith(UncheckedIOException.class.getName()), outcome.failure);
        assertTrue(outcome.failure.contains("SavedData staging readback differs"), outcome.failure);
        assertArrayEquals(new byte[] {5}, outcome.target);
        assertFalse(outcome.pendingExists);
    }

    @Test void aThrowingCommitterThrowsTheSameException() throws Exception {
        Scenario checked = (file, writer) -> {
            Files.write(file, new byte[] {6});
            writer.replace(file, () -> payload(2), (staged, target) -> { throw new IOException("injected before move"); });
        };
        Outcome before = assertSameOutcome(checked, true);
        assertArrayEquals(new byte[] {6}, before.target);
        assertFalse(before.pendingExists);

        Scenario unchecked = (file, writer) -> writer.replace(file, () -> payload(3),
                (staged, target) -> { throw new IllegalStateException("injected unchecked"); });
        Outcome runtime = assertSameOutcome(unchecked, true);
        assertTrue(runtime.failure.startsWith(IllegalStateException.class.getName()), runtime.failure);
        assertNull(runtime.target);

        Scenario after = (file, writer) -> writer.replace(file, () -> payload(4), (staged, target) -> {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            throw new IOException("injected after move");
        });
        Outcome moved = assertSameOutcome(after, true);
        assertNotNull(moved.target);

        Scenario identity = (file, writer) -> writer.replace(file.resolveSibling("other.dat"), () -> payload(5), plainMove());
        assertSameOutcome(identity, true);
        Scenario payloadFails = (file, writer) -> writer.replace(file,
                () -> { throw new IllegalArgumentException("payload"); }, plainMove());
        assertSameOutcome(payloadFails, true);
    }

    @Test void theOperationsRunInTheOldOrder() throws Exception {
        List<String> calls = new ArrayList<>();
        CheckedSavedDataFile.installFileOps(new Spy(calls));
        Path file = Files.createDirectories(root.resolve("order")).resolve(TYPE.fileName());
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1), (staged, target) -> {
            calls.add("committer");
            CheckedSavedDataFile.atomicMove(staged, target);
        });
        CheckedSavedDataPolicy.forceDirectory(file.getParent());
        assertEquals("open", calls.get(0));
        assertTrue(calls.contains("write"));
        List<String> afterWrites = calls.subList(calls.lastIndexOf("write") + 1, calls.size());
        assertEquals(List.of("force", "close", "readStaged", "committer", "move", "forceDirectory"), afterWrites);
        assertTrue(calls.subList(1, calls.lastIndexOf("write") + 1).stream().allMatch("write"::equals));

        calls.clear();
        assertThrows(UncheckedIOException.class, () -> CheckedSavedDataFile.replace(file, TYPE, () -> payload(2),
                (staged, target) -> { throw new IOException("injected"); }));
        assertEquals("delete", calls.get(calls.size() - 1));
        assertFalse(calls.contains("move"));
    }

    @Test void aFailedCloseIsSuppressedUnderAFailedForceAndTheCleanupStillRuns() throws Exception {
        List<String> calls = new ArrayList<>();
        CheckedSavedDataFile.installFileOps(new Spy(calls) {
            @Override public void force(FileChannel channel) throws IOException {
                super.force(channel);
                throw new IOException("force failed");
            }
            @Override public void close(FileChannel channel) throws IOException {
                super.close(channel);
                throw new IOException("close failed");
            }
            @Override public void delete(Path pending) throws IOException {
                super.delete(pending);
                throw new IOException("delete failed");
            }
        });
        Path file = Files.createDirectories(root.resolve("close")).resolve(TYPE.fileName());
        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
                () -> CheckedSavedDataFile.replace(file, TYPE, () -> payload(1)));
        assertEquals("force failed", thrown.getCause().getMessage());
        assertEquals(List.of("close failed", "delete failed"),
                Arrays.stream(thrown.getCause().getSuppressed()).map(Throwable::getMessage).toList());
        assertEquals(List.of("force", "close", "delete"), calls.subList(calls.size() - 3, calls.size()));
        assertFalse(Files.exists(file));
    }

    @Test void directForcesADirectoryAsBefore() throws Exception {
        Path directory = Files.createDirectories(root.resolve("force"));
        CheckedSavedDataPolicy.forceDirectory(directory);
        CheckedSavedDataPolicy.DirectoryForce.PLATFORM.force(directory);
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            assertThrows(IOException.class, () -> CheckedSavedDataPolicy.forceDirectory(directory.resolve("absent")));
        } else {
            CheckedSavedDataPolicy.forceDirectory(directory.resolve("absent"));
        }
    }

    // ---- The comparison ---------------------------------------------------------------------------------------------

    private interface Writer {
        void replace(Path file, Supplier<CompoundTag> payload, CheckedSavedDataFile.Committer committer);
    }

    private interface Scenario {
        void run(Path file, Writer writer) throws Exception;
    }

    private record Outcome(String failure, byte[] target, boolean pendingExists) {
    }

    private int scenarios;

    private Outcome assertSameOutcome(Scenario scenario, boolean expectFailure) throws Exception {
        Outcome legacy = run(scenario, (file, payload, committer) -> legacyReplace(file, TYPE, payload, committer));
        Outcome direct = run(scenario, (file, payload, committer) -> CheckedSavedDataFile.replace(file, TYPE, payload, committer));
        assertEquals(legacy.failure, direct.failure);
        assertArrayEquals(legacy.target, direct.target);
        assertEquals(legacy.pendingExists, direct.pendingExists);
        assertEquals(expectFailure, direct.failure != null);
        return direct;
    }

    private Outcome run(Scenario scenario, Writer writer) throws Exception {
        Path directory = Files.createDirectories(root.resolve("scenario-" + scenarios++));
        Path file = directory.resolve(TYPE.fileName());
        String failure = null;
        try {
            scenario.run(file, writer);
        } catch (RuntimeException thrown) {
            failure = describe(thrown).replace(directory.toString(), "<directory>");
        }
        return new Outcome(failure, Files.exists(file) ? Files.readAllBytes(file) : null, Files.exists(pending(file)));
    }

    private static String describe(Throwable thrown) {
        StringBuilder text = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            text.append(current.getClass().getName()).append(": ").append(current.getMessage());
            for (Throwable suppressed : current.getSuppressed()) {
                text.append(" [suppressed ").append(describe(suppressed)).append(']');
            }
            text.append(" | ");
        }
        return text.toString();
    }

    private static Path pending(Path file) {
        return file.resolveSibling(TYPE.fileName() + ".arce-pending");
    }

    private static CompoundTag payload(int value) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("value", value);
        payload.putString("text", "w369-" + value);
        payload.putByteArray("bytes", new byte[32 * 1_024]);
        return payload;
    }

    /** The default committer of the earlier code. */
    private static CheckedSavedDataFile.Committer plainMove() {
        return (staged, target) -> Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    /** {@code CheckedSavedDataFile.replace} as it was before the seam. */
    private static void legacyReplace(Path supplied, ManagedSavedDataType type, Supplier<CompoundTag> payload,
                                      CheckedSavedDataFile.Committer committer) {
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

    /** Calls {@link CheckedFileOps#DIRECT} and notes each operation. */
    static class Spy implements CheckedFileOps {
        private final List<String> calls;

        Spy(List<String> calls) { this.calls = calls; }

        @Override public FileChannel open(Path pending) throws IOException { calls.add("open"); return DIRECT.open(pending); }
        @Override public int write(FileChannel channel, ByteBuffer buffer) throws IOException {
            calls.add("write"); return DIRECT.write(channel, buffer);
        }
        @Override public void force(FileChannel channel) throws IOException { calls.add("force"); DIRECT.force(channel); }
        @Override public void close(FileChannel channel) throws IOException { calls.add("close"); DIRECT.close(channel); }
        @Override public CompoundTag readStaged(Path pending, ManagedSavedDataType type) {
            calls.add("readStaged"); return DIRECT.readStaged(pending, type);
        }
        @Override public void move(Path staged, Path target) throws IOException { calls.add("move"); DIRECT.move(staged, target); }
        @Override public void forceDirectory(Path directory) throws IOException {
            calls.add("forceDirectory"); DIRECT.forceDirectory(directory);
        }
        @Override public void delete(Path pending) throws IOException { calls.add("delete"); DIRECT.delete(pending); }
    }
}

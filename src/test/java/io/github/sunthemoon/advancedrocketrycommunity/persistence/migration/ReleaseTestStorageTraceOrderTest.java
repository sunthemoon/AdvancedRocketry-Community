package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W369R: the trace's lines are in the order of the file-system effects across writer threads, and the stored-byte
 * bound counts a blob once. Each ordering test holds one thread inside its delegated operation, after the effect
 * and before the line could be written, and starts a second operation on another thread.
 */
class ReleaseTestStorageTraceOrderTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.BAY_RECEIPTS;
    @TempDir Path root;
    private Path data;
    private Path target;
    private Path trace;
    private final List<String> markers = Collections.synchronizedList(new ArrayList<>());
    private final List<String> effects = Collections.synchronizedList(new ArrayList<>());

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @BeforeEach void layout() throws IOException {
        data = Files.createDirectories(root.resolve("world").resolve("data"));
        target = data.resolve(TYPE.fileName());
        trace = root.resolve("trace");
    }

    @Test void aDirectoryForceThatFinishedFirstIsRecordedBeforeAConcurrentMove() throws Exception {
        Path staged = Files.write(pending(), new byte[] {1, 2, 3});
        var recorder = recorder("forceDirectory");
        race(() -> recorder.forceDirectory(data), () -> recorder.move(staged, target));
        assertEquals(List.of("forceDirectory", "move"), effects);
        assertEquals(effects, operations());
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(target));
        assertTrue(recorder.valid());
        assertTrue(markers.isEmpty(), markers.toString());
    }

    @Test void aMoveThatFinishedFirstIsRecordedBeforeAConcurrentDirectoryForce() throws Exception {
        Path staged = Files.write(pending(), new byte[] {4, 5});
        var recorder = recorder("move");
        race(() -> recorder.move(staged, target), () -> recorder.forceDirectory(data));
        assertEquals(List.of("move", "forceDirectory"), effects);
        assertEquals(effects, operations());
        assertTrue(recorder.valid());
        assertTrue(markers.isEmpty(), markers.toString());
    }

    @Test void theOldTargetOfAnOpenIsCopiedBeforeAConcurrentMoveReplacesIt() throws Exception {
        byte[] old = {9, 9, 9, 9};
        Files.write(target, old);
        Path other = Files.write(data.resolve("other.arce-pending"), new byte[] {7});
        var recorder = recorder("open");
        AtomicReference<FileChannel> channel = new AtomicReference<>();
        race(() -> channel.set(recorder.open(pending())), () -> recorder.move(other, target));
        recorder.close(channel.get());
        assertEquals(List.of("open", "move"), effects);
        List<String> lines = Files.readAllLines(trace.resolve("ops.jsonl"));
        assertEquals(List.of("open", "move", "close"), lines.stream().map(line -> field(line, "op")).toList());
        assertEquals(sha256(old), field(lines.get(0), "pre"));
        assertArrayEquals(old, Files.readAllBytes(trace.resolve("pre").resolve("0")));
        assertArrayEquals(new byte[] {7}, Files.readAllBytes(target));
        assertTrue(markers.isEmpty(), markers.toString());
    }

    @Test void aBlobAlreadyStoredFitsAnExhaustedBudget() throws Exception {
        var recorder = ReleaseTestStorageTrace.Recorder.create(trace.toString(), root.resolve("world"), CheckedFileOps.DIRECT,
                ReleaseTestStorageTrace.MAX_OPERATIONS, 4, markers::add);
        assertNotNull(recorder, markers.toString());
        FileChannel channel = recorder.open(pending());
        byte[] bytes = {1, 2, 3, 4};
        assertEquals(4, recorder.write(channel, ByteBuffer.wrap(bytes)));
        assertEquals(4, recorder.storedBytes());
        // The budget is now exactly used. The same bytes add nothing to the store and are still recorded.
        ByteBuffer again = ByteBuffer.wrap(bytes);
        assertEquals(4, recorder.write(channel, again));
        assertFalse(again.hasRemaining());
        assertEquals(4, recorder.storedBytes());
        assertTrue(recorder.valid());
        assertTrue(markers.isEmpty(), markers.toString());
        List<String> lines = Files.readAllLines(trace.resolve("ops.jsonl"));
        assertEquals(List.of("open", "write", "write"), lines.stream().map(line -> field(line, "op")).toList());
        assertEquals(field(lines.get(1), "sha256"), field(lines.get(2), "sha256"));
        assertEquals(sha256(bytes), field(lines.get(2), "sha256"));
        assertEquals("4", field(lines.get(2), "offset"));
        assertEquals("4", field(lines.get(2), "length"));
        try (var blobs = Files.list(trace.resolve("blobs"))) { assertEquals(1, blobs.count()); }

        // One new byte is over the bound: the write still succeeds, and the recording stops with the marker.
        assertEquals(1, recorder.write(channel, ByteBuffer.wrap(new byte[] {5})));
        recorder.close(channel);
        assertEquals(9, Files.size(pending()));
        assertFalse(recorder.valid());
        assertEquals(4, recorder.storedBytes());
        assertEquals(List.of(ReleaseTestStorageTrace.INVALID + " reason=blob-bound operations=3"), markers);
        assertEquals(3, Files.readAllLines(trace.resolve("ops.jsonl")).size());
    }

    // ---- The race ---------------------------------------------------------------------------------------------------

    private interface Step {
        void run() throws Exception;
    }

    private final CountDownLatch held = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    /**
     * Runs {@code first}, whose delegate stops after its effect, then starts {@code second} and gives it time to
     * finish if nothing serializes it. The first operation is released when the second is blocked or has ended.
     */
    private void race(Step first, Step second) throws Exception {
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        Thread one = thread("w369-first", first, failures);
        one.start();
        assertTrue(held.await(10, TimeUnit.SECONDS), "The first operation never reached its effect");
        Thread two = thread("w369-second", second, failures);
        two.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (two.isAlive() && two.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        release.countDown();
        one.join(10_000);
        two.join(10_000);
        assertFalse(one.isAlive() || two.isAlive(), "An operation did not end");
        assertTrue(failures.isEmpty(), failures.toString());
    }

    private static Thread thread(String name, Step step, List<Throwable> failures) {
        Thread thread = new Thread(() -> {
            try { step.run(); } catch (Throwable failure) { failures.add(failure); }
        }, name);
        thread.setDaemon(true);
        return thread;
    }

    /** A recorder over a delegate that notes each effect and holds the first {@code heldOperation} after its effect. */
    private ReleaseTestStorageTrace.Recorder recorder(String heldOperation) {
        CheckedFileOps delegate = new CheckedFileOps() {
            private void effect(String operation) {
                effects.add(operation);
                if (operation.equals(heldOperation) && held.getCount() > 0) {
                    held.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
            }
            @Override public FileChannel open(Path pending) throws IOException {
                FileChannel channel = DIRECT.open(pending); effect("open"); return channel;
            }
            @Override public int write(FileChannel channel, ByteBuffer buffer) throws IOException { return DIRECT.write(channel, buffer); }
            @Override public void force(FileChannel channel) throws IOException { DIRECT.force(channel); }
            @Override public void close(FileChannel channel) throws IOException { DIRECT.close(channel); }
            @Override public CompoundTag readStaged(Path pending, ManagedSavedDataType type) { return DIRECT.readStaged(pending, type); }
            @Override public void move(Path staged, Path moved) throws IOException { DIRECT.move(staged, moved); effect("move"); }
            @Override public void forceDirectory(Path directory) throws IOException {
                DIRECT.forceDirectory(directory); effect("forceDirectory");
            }
            @Override public void delete(Path pending) throws IOException { DIRECT.delete(pending); }
        };
        var recorder = ReleaseTestStorageTrace.Recorder.create(trace.toString(), root.resolve("world"), delegate,
                ReleaseTestStorageTrace.MAX_OPERATIONS, ReleaseTestStorageTrace.MAX_BLOB_BYTES, markers::add);
        assertNotNull(recorder, markers.toString());
        return recorder;
    }

    private Path pending() {
        return data.resolve(TYPE.fileName() + ".arce-pending");
    }

    private List<String> operations() throws IOException {
        return Files.readAllLines(trace.resolve("ops.jsonl")).stream().map(line -> field(line, "op")).toList();
    }

    private static String field(String line, String name) {
        Matcher matcher = Pattern.compile("[{,]\"" + name + "\":(\"([^\"]*)\"|[^,}]*)").matcher(line);
        assertTrue(matcher.find(), name + " in " + line);
        return matcher.group(2) != null ? matcher.group(2) : matcher.group(1);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W369: the recorder's lines, blobs, old-target copies, bounds and refusal marker; it never changes a write. */
class ReleaseTestStorageTraceTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.BAY_RECEIPTS;
    @TempDir Path root;
    private Path world;
    private Path file;
    private Path trace;
    private final List<String> markers = new ArrayList<>();

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @BeforeEach void layout() throws IOException {
        world = root.resolve("world");
        file = Files.createDirectories(world.resolve("data")).resolve(TYPE.fileName());
        trace = root.resolve("trace");
    }

    @AfterEach void restore() { CheckedSavedDataFile.installFileOps(null); }

    @Test void aWriteIsRecordedOperationByOperation() throws Exception {
        var recorder = install(ReleaseTestStorageTrace.MAX_OPERATIONS, ReleaseTestStorageTrace.MAX_BLOB_BYTES);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1));
        CheckedSavedDataPolicy.forceDirectory(file.getParent());
        byte[] first = Files.readAllBytes(file);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(2));
        byte[] second = Files.readAllBytes(file);
        recorder.close();
        CheckedSavedDataFile.installFileOps(null);
        assertSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());

        List<String> lines = Files.readAllLines(trace.resolve("ops.jsonl"), StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            assertEquals(Integer.toString(index), field(lines.get(index), "seq"));
            assertEquals(Thread.currentThread().getName(), field(lines.get(index), "thread"));
            assertEquals("ok", field(lines.get(index), "result"));
        }
        List<String> operations = lines.stream().map(line -> field(line, "op")).toList();
        int firstWrites = (int) operations.subList(0, operations.indexOf("force")).stream().filter("write"::equals).count();
        assertTrue(firstWrites >= 1);
        List<String> expected = new ArrayList<>(List.of("open"));
        for (int count = 0; count < firstWrites; count++) { expected.add("write"); }
        expected.addAll(List.of("force", "close", "readStaged", "move", "forceDirectory"));
        assertEquals(expected, operations.subList(0, expected.size()));
        assertEquals("open", operations.get(expected.size()));
        assertEquals("move", operations.get(operations.size() - 1));
        assertEquals(lines.size(), recorder.operations());
        assertTrue(recorder.valid());
        assertTrue(markers.isEmpty(), markers.toString());

        String open = lines.get(0);
        assertEquals("BAY_RECEIPTS", field(open, "store"));
        assertEquals("data/" + TYPE.fileName() + ".arce-pending", field(open, "path"));
        assertEquals("data/" + TYPE.fileName(), field(open, "target"));
        assertEquals("absent", field(open, "pre"));
        assertTrue(Files.exists(trace.resolve("pre").resolve("0.absent")));
        String move = lines.get(operations.indexOf("move"));
        assertEquals("data/" + TYPE.fileName() + ".arce-pending", field(move, "path"));
        assertEquals("data/" + TYPE.fileName(), field(move, "target"));
        assertEquals("null", field(lines.get(operations.indexOf("forceDirectory")), "store"));
        assertEquals("data", field(lines.get(operations.indexOf("forceDirectory")), "path"));

        // The write blobs rebuild each staged file at its offsets, and the second open kept the first content.
        assertArrayEquals(first, rebuild(lines.subList(0, expected.size())));
        assertArrayEquals(second, rebuild(lines.subList(expected.size(), lines.size())));
        String secondOpen = lines.get(expected.size());
        assertEquals(sha256(first), field(secondOpen, "pre"));
        assertEquals(Integer.toString(first.length), field(secondOpen, "preLength"));
        assertArrayEquals(first, Files.readAllBytes(trace.resolve("pre").resolve(field(secondOpen, "seq"))));
        long blobBytes = 0;
        try (var blobs = Files.list(trace.resolve("blobs"))) {
            for (Path blob : blobs.toList()) {
                assertEquals(blob.getFileName().toString(), sha256(Files.readAllBytes(blob)));
                blobBytes += Files.size(blob);
            }
        }
        assertEquals(blobBytes + first.length, recorder.storedBytes());
    }

    @Test void equalBytesAreStoredOnce() throws Exception {
        var recorder = install(ReleaseTestStorageTrace.MAX_OPERATIONS, ReleaseTestStorageTrace.MAX_BLOB_BYTES);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1));
        long afterFirst = recorder.storedBytes();
        long size = Files.size(file);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1));
        // The second write adds only the copy of the old target; its own bytes are already stored.
        assertEquals(afterFirst + size, recorder.storedBytes());
        try (var blobs = Files.list(trace.resolve("blobs"))) {
            assertEquals(Files.readAllLines(trace.resolve("ops.jsonl")).stream()
                    .filter(line -> "write".equals(field(line, "op"))).map(line -> field(line, "sha256")).distinct().count(),
                    blobs.count());
        }
        assertTrue(markers.isEmpty(), markers.toString());
    }

    @Test void failuresAreRecordedAndRethrownUnchanged() throws Exception {
        install(ReleaseTestStorageTrace.MAX_OPERATIONS, ReleaseTestStorageTrace.MAX_BLOB_BYTES);
        IOException injected = new IOException("injected before move");
        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
                () -> CheckedSavedDataFile.replace(file, TYPE, () -> payload(1), (staged, target) -> { throw injected; }));
        assertSame(injected, thrown.getCause());
        assertFalse(Files.exists(file));
        IOException missing = assertThrows(IOException.class, () -> CheckedSavedDataFile.atomicMove(
                file.resolveSibling("absent.arce-pending"), file.resolveSibling("absent")));
        List<String> lines = Files.readAllLines(trace.resolve("ops.jsonl"));
        String delete = lines.get(lines.size() - 2);
        assertEquals("delete", field(delete, "op"));
        assertEquals("ok", field(delete, "result"));
        String move = lines.get(lines.size() - 1);
        assertEquals("move", field(move, "op"));
        assertEquals("UNKNOWN", field(move, "store"));
        assertEquals(missing.getClass().getName(), field(move, "result"));
        assertTrue(markers.isEmpty(), markers.toString());
    }

    @Test void theOperationBoundStopsTheRecordingOnce() throws Exception {
        var recorder = install(3, ReleaseTestStorageTrace.MAX_BLOB_BYTES);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1));
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(2));
        assertEquals(payload(2), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertEquals(3, Files.readAllLines(trace.resolve("ops.jsonl")).size());
        assertEquals(3, recorder.operations());
        assertFalse(recorder.valid());
        assertEquals(List.of(ReleaseTestStorageTrace.INVALID + " reason=operation-bound operations=3"), markers);
    }

    @Test void theBlobBoundStopsTheRecordingOnce() throws Exception {
        var recorder = install(ReleaseTestStorageTrace.MAX_OPERATIONS, 16);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1));
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(2));
        assertEquals(payload(2), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertEquals(List.of("open"), Files.readAllLines(trace.resolve("ops.jsonl")).stream()
                .map(line -> field(line, "op")).toList());
        assertEquals(0, recorder.storedBytes());
        assertEquals(List.of(ReleaseTestStorageTrace.INVALID + " reason=blob-bound operations=1"), markers);

        // The copy of an old target counts against the same bound.
        markers.clear();
        trace = root.resolve("trace-pre");
        var second = install(ReleaseTestStorageTrace.MAX_OPERATIONS, Files.size(file) - 1);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(3));
        assertEquals(payload(3), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertEquals(0, second.operations());
        assertEquals(List.of(ReleaseTestStorageTrace.INVALID + " reason=blob-bound operations=0"), markers);
    }

    @Test void aTraceThatCannotBeWrittenStopsTheRecordingAndTheWriteContinues() throws Exception {
        var recorder = install(ReleaseTestStorageTrace.MAX_OPERATIONS, ReleaseTestStorageTrace.MAX_BLOB_BYTES);
        // The blob directory becomes a file, so the first write's blob cannot be stored.
        Files.delete(trace.resolve("blobs"));
        Files.write(trace.resolve("blobs"), new byte[0]);
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1));
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(2));
        assertEquals(payload(2), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertFalse(Files.exists(file.resolveSibling(TYPE.fileName() + ".arce-pending")));
        assertFalse(recorder.valid());
        assertEquals(1, markers.size(), markers.toString());
        assertTrue(markers.get(0).startsWith(ReleaseTestStorageTrace.INVALID + " reason="), markers.get(0));
        assertTrue(markers.get(0).endsWith(" operations=1"), markers.get(0));
    }

    @Test void anUnusableDirectoryIsRefusedWithTheMarker() throws Exception {
        assertNull(open(world.resolve("trace").toString()));
        assertEquals(List.of(ReleaseTestStorageTrace.INVALID + " reason=directory-inside-world operations=0"), markers);
        assertFalse(Files.exists(world.resolve("trace")));

        markers.clear();
        Path used = Files.createDirectories(root.resolve("used"));
        Files.write(used.resolve("ops.jsonl"), new byte[0]);
        assertNull(open(used.toString()));
        assertEquals(List.of(ReleaseTestStorageTrace.INVALID + " reason=directory-not-empty operations=0"), markers);

        markers.clear();
        Path regular = Files.write(root.resolve("regular"), new byte[0]);
        assertNull(open(regular.toString()));
        assertEquals(1, markers.size());
        assertTrue(markers.get(0).startsWith(ReleaseTestStorageTrace.INVALID + " reason="), markers.get(0));
        assertSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
    }

    @Test void aClosedRecorderOnlyDelegates() throws Exception {
        var recorder = install(ReleaseTestStorageTrace.MAX_OPERATIONS, ReleaseTestStorageTrace.MAX_BLOB_BYTES);
        recorder.close();
        CheckedSavedDataFile.replace(file, TYPE, () -> payload(1));
        assertEquals(payload(1), CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
        assertEquals(0, Files.size(trace.resolve("ops.jsonl")));
        assertTrue(recorder.valid());
        assertTrue(markers.isEmpty());
    }

    private ReleaseTestStorageTrace.Recorder install(int maxOperations, long maxBytes) {
        var recorder = ReleaseTestStorageTrace.Recorder.open(trace.toString(), world, CheckedFileOps.DIRECT,
                maxOperations, maxBytes, markers::add);
        assertNotNull(recorder, markers.toString());
        CheckedSavedDataFile.installFileOps(recorder);
        return recorder;
    }

    private ReleaseTestStorageTrace.Recorder open(String directory) {
        return ReleaseTestStorageTrace.Recorder.open(directory, world, CheckedFileOps.DIRECT,
                ReleaseTestStorageTrace.MAX_OPERATIONS, ReleaseTestStorageTrace.MAX_BLOB_BYTES, markers::add);
    }

    private byte[] rebuild(List<String> lines) throws IOException {
        var output = new java.io.ByteArrayOutputStream();
        for (String line : lines) {
            if (!"write".equals(field(line, "op"))) { continue; }
            assertEquals(Integer.toString(output.size()), field(line, "offset"));
            byte[] blob = Files.readAllBytes(trace.resolve("blobs").resolve(field(line, "sha256")));
            assertEquals(Integer.toString(blob.length), field(line, "length"));
            output.write(blob);
        }
        return output.toByteArray();
    }

    /** The value of one top-level field of a recorder line; the test paths and names need no escapes. */
    private static String field(String line, String name) {
        assertTrue(line.startsWith("{") && line.endsWith("}"), line);
        Matcher matcher = Pattern.compile("[{,]\"" + name + "\":(\"([^\"]*)\"|[^,}]*)").matcher(line);
        assertTrue(matcher.find(), name + " in " + line);
        return matcher.group(2) != null ? matcher.group(2) : matcher.group(1);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static CompoundTag payload(int value) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("value", value);
        payload.putLongArray("longs", new long[] {value, 2L * value, 3L * value});
        return payload;
    }
}

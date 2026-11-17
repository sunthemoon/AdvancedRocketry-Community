package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ReleaseTestStorageFault.Fault;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ReleaseTestStorageFault.Spec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W379: each fault of the gated injector acts where W368-DESIGN section 4 says, only on the named store and only for
 * the given number of writes; the gate, the refusals and the restore of {@link CheckedFileOps#DIRECT} are pinned.
 */
class ReleaseTestStorageFaultTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.BAY_RECEIPTS;
    private static final ManagedSavedDataType OTHER = ManagedSavedDataType.RESOURCE_ESCROW;
    @TempDir Path root;
    private final List<String> lines = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private Path file;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @AfterEach void restore() {
        ReleaseTestStorageFault.restore();
        CheckedSavedDataFile.installFileOps(null);
        CheckedSavedDataPolicy.installReaderWrapper(null);
    }

    // ---- The gate, the property and the restore ---------------------------------------------------------------------

    @Test void nothingIsInstalledWithoutTheProperty() {
        assertNull(System.getProperty(ReleaseTestStorageFault.PROPERTY));
        assertSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
        assertFalse(CheckedSavedDataPolicy.readerWrapped());
        String spec = "BAY_RECEIPTS:WRITE_ERROR:1";
        assertNull(ReleaseTestStorageFault.requested(true, true, null));
        assertNull(ReleaseTestStorageFault.requested(true, true, ""));
        assertNull(ReleaseTestStorageFault.requested(false, true, spec));
        assertNull(ReleaseTestStorageFault.requested(true, false, spec));
        assertNull(ReleaseTestStorageFault.requested(false, false, spec));
        assertEquals(spec, ReleaseTestStorageFault.requested(true, true, spec));
        assertEquals(ReleaseTestStorageTrace.HOOKS, ReleaseTestStorageFault.HOOKS);
    }

    @Test void thePropertyIsParsedStrictly() {
        assertEquals(new Spec(TYPE, Fault.WRITE_ERROR, 1), ReleaseTestStorageFault.parse("BAY_RECEIPTS:WRITE_ERROR:1"));
        assertEquals(new Spec(ManagedSavedDataType.ROCKET_TRANSFERS, Fault.READ_CORRUPT, 16),
                ReleaseTestStorageFault.parse("ROCKET_TRANSFERS:READ_CORRUPT:16"));
        for (Fault fault : Fault.values()) {
            assertEquals(fault, ReleaseTestStorageFault.parse("SATELLITE_MISSIONS:" + fault + ":4").fault());
        }
        assertMalformed("form", "", "BAY_RECEIPTS", "BAY_RECEIPTS:WRITE_ERROR", "BAY_RECEIPTS:WRITE_ERROR:1:2");
        assertMalformed("store", "bay_receipts:WRITE_ERROR:1", "LEDGER:WRITE_ERROR:1", ":WRITE_ERROR:1");
        assertMalformed("fault", "BAY_RECEIPTS:write_error:1", "BAY_RECEIPTS:POWER_LOSS:1", "BAY_RECEIPTS::1");
        assertMalformed("count", "BAY_RECEIPTS:WRITE_ERROR:0", "BAY_RECEIPTS:WRITE_ERROR:-1", "BAY_RECEIPTS:WRITE_ERROR:17",
                "BAY_RECEIPTS:WRITE_ERROR:", "BAY_RECEIPTS:WRITE_ERROR:01", "BAY_RECEIPTS:WRITE_ERROR:+1",
                "BAY_RECEIPTS:WRITE_ERROR:x");
    }

    @Test void aMalformedPropertyInstallsNothing() {
        assertFalse(ReleaseTestStorageFault.install("BAY_RECEIPTS:POWER_LOSS:1", lines::add, errors::add));
        assertEquals(List.of(ReleaseTestStorageFault.INVALID + " reason=malformed-fault"), errors);
        assertTrue(lines.isEmpty());
        assertSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
        assertFalse(CheckedSavedDataPolicy.readerWrapped());
    }

    @Test void theInjectorNeverReplacesAnotherImplementation() {
        CheckedFileOps recorder = new Forwarding();
        CheckedSavedDataFile.installFileOps(recorder);
        assertFalse(ReleaseTestStorageFault.install("BAY_RECEIPTS:WRITE_ERROR:1", lines::add, errors::add));
        assertEquals(List.of(ReleaseTestStorageFault.INVALID + " reason=file-operations-already-replaced"), errors);
        assertSame(recorder, CheckedSavedDataFile.fileOps());
        assertFalse(CheckedSavedDataPolicy.readerWrapped());
        // A restore without an installed injector leaves the other implementation in place.
        ReleaseTestStorageFault.restore();
        assertSame(recorder, CheckedSavedDataFile.fileOps());
    }

    @Test void theRestorePutsDirectAndTheUnwrappedReadersBack() throws Exception {
        install(Fault.WRITE_ERROR, 4);
        assertEquals(ReleaseTestStorageFault.MARKER + " installed store=BAY_RECEIPTS fault=WRITE_ERROR count=4", lines.get(0));
        assertNotSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
        assertTrue(CheckedSavedDataPolicy.readerWrapped());
        assertThrows(UncheckedIOException.class, () -> write(1));

        ReleaseTestStorageFault.restore();
        assertSame(CheckedFileOps.DIRECT, CheckedSavedDataFile.fileOps());
        assertFalse(CheckedSavedDataPolicy.readerWrapped());
        assertEquals(ReleaseTestStorageFault.MARKER + " closed store=BAY_RECEIPTS fault=WRITE_ERROR injected=1 attempts=1",
                lines.get(lines.size() - 1));
        // Three faults were still unused; none acts after the restore.
        write(2);
        assertEquals(payload(2), read());
        int reported = lines.size();
        ReleaseTestStorageFault.restore();
        assertEquals(reported, lines.size());
    }

    @Test void aSecondInstallationIsRefusedWhileOneIsInstalled() {
        install(Fault.WRITE_ERROR, 1);
        CheckedFileOps first = CheckedSavedDataFile.fileOps();
        assertFalse(ReleaseTestStorageFault.install("BAY_RECEIPTS:MOVE_ERROR:1", lines::add, errors::add));
        assertSame(first, CheckedSavedDataFile.fileOps());
        assertEquals(1, errors.size());
    }

    // ---- The faults -------------------------------------------------------------------------------------------------

    @Test void aWriteErrorFailsBeforeTheCommitterAndLeavesTheTargetAndNoStagingFile() throws Exception {
        byte[] old = seed();
        install(Fault.WRITE_ERROR, 1);
        IOException failure = failed(() -> write(1, true));
        assertEquals("ARCE injected fault: write error", failure.getMessage());
        assertArrayEquals(old, Files.readAllBytes(file));
        assertFalse(Files.exists(pending()));
        assertEquals(List.of("injected operation=write attempt=1 remaining=0",
                "attempt attempt=1 result=failed moved=false faulted=true bytes=0"), events());
        // The fault is lifted: the next write commits.
        write(2);
        assertEquals(payload(2), read());
        assertTrue(events().get(2).startsWith("attempt attempt=2 result=moved moved=true faulted=false bytes="));
    }

    @Test void aFullDiskStoresHalfTheBytesAndNeverReachesTheTarget() throws Exception {
        byte[] old = seed();
        long full = encodedLength(payload(1));
        install(Fault.DISK_FULL, 1);
        Path staging = pending();
        long[] stagedAtCleanup = {-1};
        CheckedSavedDataFile.installFileOps(new Forwarding(CheckedSavedDataFile.fileOps()) {
            @Override public void delete(Path pending) throws IOException {
                stagedAtCleanup[0] = Files.size(staging);
                super.delete(pending);
            }
        });
        IOException failure = failed(() -> write(1, true));
        assertTrue(failure.getMessage().contains("not enough space"), failure.getMessage());
        assertEquals(full / 2, stagedAtCleanup[0]);
        assertArrayEquals(old, Files.readAllBytes(file));
        assertFalse(Files.exists(staging));
        assertEquals("attempt attempt=1 result=failed moved=false faulted=true bytes=" + full / 2, events().get(1));
    }

    @Test void aFullDiskAtTheOpenFailsBeforeAnyStagingFileExists() throws Exception {
        byte[] old = seed();
        install(Fault.DISK_FULL_OPEN, 1);
        IOException failure = failed(() -> write(1, true));
        assertTrue(failure.getMessage().contains("not enough space"), failure.getMessage());
        assertArrayEquals(old, Files.readAllBytes(file));
        assertFalse(Files.exists(pending()));
        assertEquals(List.of("injected operation=open attempt=1 remaining=0",
                "attempt attempt=1 result=open-failed moved=false faulted=true bytes=0"), events());
        write(2);
        assertEquals(payload(2), read());
    }

    @Test void aFailedFlushFailsBeforeTheCommitter() throws Exception {
        byte[] old = seed();
        install(Fault.FLUSH_ERROR, 1);
        assertEquals("ARCE injected fault: flush error", failed(() -> write(1, true)).getMessage());
        assertArrayEquals(old, Files.readAllBytes(file));
        assertFalse(Files.exists(pending()));
        assertEquals("injected operation=force attempt=1 remaining=0", events().get(0));
        assertTrue(events().get(1).startsWith("attempt attempt=1 result=failed moved=false faulted=true bytes="));
    }

    @Test void aFailedCloseReleasesTheHandleAndFailsBeforeTheCommitter() throws Exception {
        byte[] old = seed();
        install(Fault.CLOSE_ERROR, 1);
        assertEquals("ARCE injected fault: close error", failed(() -> write(1, true)).getMessage());
        assertArrayEquals(old, Files.readAllBytes(file));
        // The staging file could be deleted, so its handle was released.
        assertFalse(Files.exists(pending()));
        assertEquals("injected operation=close attempt=1 remaining=0", events().get(0));
        write(2);
        assertEquals(payload(2), read());
    }

    @Test void aFailedMoveLeavesTheTargetOld() throws Exception {
        byte[] old = seed();
        install(Fault.MOVE_ERROR, 1);
        boolean[] entered = {false};
        IOException failure = failed(() -> CheckedSavedDataFile.replace(file, TYPE, () -> payload(1), (staged, target) -> {
            entered[0] = true;
            CheckedSavedDataFile.atomicMove(staged, target);
        }));
        assertEquals("ARCE injected fault: move error", failure.getMessage());
        assertTrue(entered[0]);
        assertArrayEquals(old, Files.readAllBytes(file));
        assertFalse(Files.exists(pending()));
        assertEquals("injected operation=move attempt=1 remaining=0", events().get(0));
        assertTrue(events().get(1).startsWith("attempt attempt=1 result=failed moved=false faulted=true bytes="));
    }

    @Test void aDeniedMoveIsAnAccessDeniedException() throws Exception {
        byte[] old = seed();
        install(Fault.MOVE_DENIED, 1);
        assertInstanceOf(AccessDeniedException.class, failed(() -> write(1)));
        assertArrayEquals(old, Files.readAllBytes(file));
        assertFalse(Files.exists(pending()));
    }

    @Test void aMoveThatFailsAfterTheRenameLeavesTheCandidateInTheTarget() throws Exception {
        seed();
        install(Fault.MOVE_AFTER, 1);
        assertEquals("ARCE injected fault: move error after the rename", failed(() -> write(1)).getMessage());
        assertEquals(payload(1), read());
        assertFalse(Files.exists(pending()));
        assertEquals("injected operation=move attempt=1 remaining=0", events().get(0));
        assertTrue(events().get(1).startsWith("attempt attempt=1 result=failed moved=true faulted=true bytes="));
        // The readback is not faulted by this kind: the writer can resolve COMMITTED.
        assertEquals(Optional.of(payload(1)), CheckedSavedDataPolicy.atomic(TYPE).read(file));
    }

    @Test void aLostWriteIsCaughtByTheStagingReadback() throws Exception {
        byte[] old = seed();
        long full = encodedLength(payload(1));
        install(Fault.LOST_WRITE, 1);
        RuntimeException failure = assertThrows(RuntimeException.class, () -> write(1, true));
        assertFalse(String.valueOf(failure.getMessage()).contains("ARCE injected"), "The write itself reported success");
        assertArrayEquals(old, Files.readAllBytes(file));
        assertFalse(Files.exists(pending()));
        // The candidate's bytes are counted although none was stored, and no move followed the lost write.
        assertEquals(List.of("injected operation=write attempt=1 remaining=0",
                "attempt attempt=1 result=failed moved=false faulted=true bytes=" + full), events());
        assertTrue(lines.stream().noneMatch(line -> line.contains(" unseen ")));
    }

    @Test void aMoveAfterALostWriteIsReportedAsUnseen() throws Exception {
        install(Fault.LOST_WRITE, 1);
        // A readback that accepts the empty staging file stands for a session that does not see the loss.
        CheckedSavedDataFile.installFileOps(new Forwarding(CheckedSavedDataFile.fileOps()) {
            @Override public CompoundTag readStaged(Path pending, ManagedSavedDataType type) {
                CompoundTag outer = new CompoundTag();
                outer.put("data", payload(1));
                net.minecraft.nbt.NbtUtils.addCurrentDataVersion(outer);
                return outer;
            }
        });
        write(1);
        assertEquals(0, Files.size(file));
        assertEquals("unseen operation=move attempt=1", events().get(1));
    }

    @Test void aFailedCleanupKeepsTheOriginalFailureAndTheNextWriteReplacesTheLeftover() throws Exception {
        byte[] old = seed();
        install(Fault.DELETE_ERROR, 1);
        IOException failure = failed(() -> write(1, true));
        assertEquals("ARCE injected fault: write error", failure.getMessage());
        assertEquals("ARCE injected fault: staging cleanup error", failure.getSuppressed()[0].getMessage());
        assertArrayEquals(old, Files.readAllBytes(file));
        assertTrue(Files.exists(pending()));
        assertEquals(List.of("injected operation=write attempt=1 remaining=0",
                "attempt attempt=1 result=failed moved=false faulted=true bytes=0",
                "injected operation=delete attempt=1 remaining=0"), events());
        write(2);
        assertEquals(payload(2), read());
        assertFalse(Files.exists(pending()));
    }

    @Test void anUnreadableReadbackFollowsTheFaultedMoveOnce() throws Exception {
        seed();
        install(Fault.READ_ERROR, 1);
        CheckedSavedDataPolicy policy = CheckedSavedDataPolicy.atomic(TYPE);
        // No read is faulted before the move was.
        assertTrue(policy.read(file).isPresent());
        assertThrows(UncheckedIOException.class, () -> policy.replace(file, () -> payload(1)));
        // Another store's readback is untouched and does not use up the fault.
        Path other = Files.createDirectories(root.resolve("other")).resolve(OTHER.fileName());
        assertEquals(Optional.empty(), CheckedSavedDataPolicy.atomic(OTHER).read(other));
        UncheckedIOException unreadable = assertThrows(UncheckedIOException.class, () -> policy.read(file));
        assertEquals("ARCE injected fault: unreadable readback", unreadable.getCause().getMessage());
        assertEquals("injected operation=read attempt=0 remaining=0", events().get(2));
        // Only once: the retry of the same candidate can then be confirmed.
        assertEquals(Optional.of(payload(1)), policy.read(file));
        policy.replace(file, () -> payload(1));
        List<String> attempts = attempts();
        assertEquals(2, attempts.size());
        assertEquals(digest(attempts.get(0)), digest(attempts.get(1)));
    }

    @Test void aCorruptedReadbackReturnsADifferentRoot() throws Exception {
        seed();
        install(Fault.READ_CORRUPT, 1);
        CheckedSavedDataPolicy policy = new CheckedSavedDataPolicy(TYPE, CheckedSavedDataFile::atomicMove,
                (path, type) -> CheckedSavedDataFile.readPayload(path, type));
        assertThrows(UncheckedIOException.class, () -> policy.replace(file, () -> payload(1)));
        CompoundTag corrupted = policy.read(file).orElseThrow();
        assertNotEquals(payload(1), corrupted);
        assertTrue(corrupted.getBoolean("arce_injected_read_corruption"));
        assertEquals(payload(1), read());
        assertEquals(Optional.of(payload(1)), policy.read(file));
    }

    // ---- Scope and count --------------------------------------------------------------------------------------------

    @Test void onlyTheNamedStoreIsFaulted() throws Exception {
        install(Fault.WRITE_ERROR, 4);
        Path other = Files.createDirectories(root.resolve("other")).resolve(OTHER.fileName());
        for (int value = 1; value <= 3; value++) {
            int written = value;
            CheckedSavedDataFile.replace(other, OTHER, () -> payload(written));
            CheckedSavedDataPolicy.forceDirectory(other.getParent());
        }
        assertEquals(payload(3), CheckedSavedDataFile.readPayload(other, OTHER).orElseThrow());
        assertEquals(1, lines.size(), "Only the installation line: " + lines);
    }

    @Test void theFaultLastsForItsCountOfWritesAndIsThenLifted() throws Exception {
        byte[] old = seed();
        install(Fault.FLUSH_ERROR, 4);
        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(UncheckedIOException.class, () -> write(9));
            assertArrayEquals(old, Files.readAllBytes(file));
        }
        write(9);
        assertEquals(payload(9), read());
        List<String> injected = events().stream().filter(line -> line.startsWith("injected ")).toList();
        assertEquals(List.of("injected operation=force attempt=1 remaining=3", "injected operation=force attempt=2 remaining=2",
                "injected operation=force attempt=3 remaining=1", "injected operation=force attempt=4 remaining=0"), injected);
        // Five attempts with the same candidate report the same bytes.
        List<String> attempts = attempts();
        assertEquals(5, attempts.size());
        assertEquals(1, attempts.stream().map(ReleaseTestStorageFaultTest::digest).distinct().count());
        assertTrue(attempts.get(4).contains(" attempt=5 result=moved moved=true faulted=false"), attempts.get(4));
    }

    @Test void aWriteWithoutAFaultLeavesTheSameBytesAsDirect() throws Exception {
        Path direct = Files.createDirectories(root.resolve("direct")).resolve(TYPE.fileName());
        CheckedSavedDataFile.replace(direct, TYPE, () -> payload(5));
        install(Fault.MOVE_ERROR, 1);
        assertThrows(UncheckedIOException.class, () -> write(4));
        write(5);
        assertArrayEquals(Files.readAllBytes(direct), Files.readAllBytes(file));
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------------

    private void install(Fault fault, int count) {
        assertTrue(ReleaseTestStorageFault.install(TYPE + ":" + fault + ":" + count, lines::add, errors::add), errors.toString());
        assertTrue(errors.isEmpty());
    }

    private Path target() throws IOException {
        if (file == null) {
            file = Files.createDirectories(root.resolve("data")).resolve(TYPE.fileName());
        }
        return file;
    }

    private Path pending() throws IOException {
        return target().resolveSibling(TYPE.fileName() + ".arce-pending");
    }

    /** An older generation in the target, written before the injector is installed. */
    private byte[] seed() throws IOException {
        CheckedSavedDataFile.replace(target(), TYPE, () -> payload(0));
        return Files.readAllBytes(file);
    }

    private void write(int value) throws IOException {
        write(value, false);
    }

    private void write(int value, boolean committerMustNotRun) throws IOException {
        CheckedSavedDataFile.replace(target(), TYPE, () -> payload(value), (staged, moved) -> {
            assertFalse(committerMustNotRun, "The committer must not run");
            CheckedSavedDataFile.atomicMove(staged, moved);
        });
    }

    private CompoundTag read() throws IOException {
        return CheckedSavedDataFile.readPayload(target(), TYPE).orElseThrow();
    }

    private interface Action {
        void run() throws IOException;
    }

    private static IOException failed(Action action) {
        return assertThrows(UncheckedIOException.class, action::run).getCause();
    }

    private long encodedLength(CompoundTag payload) throws IOException {
        Path scratch = Files.createDirectories(root.resolve("length-" + System.nanoTime())).resolve(TYPE.fileName());
        CheckedFileOps before = CheckedSavedDataFile.fileOps();
        CheckedSavedDataFile.installFileOps(null);
        try {
            CheckedSavedDataFile.replace(scratch, TYPE, () -> payload);
        } finally {
            CheckedSavedDataFile.installFileOps(before == CheckedFileOps.DIRECT ? null : before);
        }
        return Files.size(scratch);
    }

    /** The reported lines after the installation, without the marker, the store, the fault and the digest. */
    private List<String> events() {
        return lines.stream().skip(1)
                .map(line -> line.replace(ReleaseTestStorageFault.MARKER + " ", "")
                        .replaceAll(" store=\\S+", "").replaceAll(" fault=\\S+", "").replaceAll(" sha256=\\S+", ""))
                .toList();
    }

    /** The raw attempt lines, with their digests. */
    private List<String> attempts() {
        return lines.stream().filter(line -> line.contains(" attempt store=")).toList();
    }

    private static String digest(String line) {
        int at = line.indexOf(" sha256=");
        assertTrue(at >= 0, line);
        return line.substring(at + 8);
    }

    private static void assertMalformed(String reason, String... properties) {
        for (String property : properties) {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> ReleaseTestStorageFault.parse(property), property);
            assertEquals(reason, refused.getMessage(), property);
        }
    }

    private static CompoundTag payload(int value) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("value", value);
        payload.putString("text", "w379-" + value);
        payload.putByteArray("bytes", new byte[32 * 1_024]);
        return payload;
    }

    /** A pass-through implementation, for the refusal test and for observing an injector from outside. */
    private static class Forwarding implements CheckedFileOps {
        private final CheckedFileOps next;

        Forwarding() {
            this(CheckedFileOps.DIRECT);
        }

        Forwarding(CheckedFileOps next) {
            this.next = next;
        }

        @Override public java.nio.channels.FileChannel open(Path pending) throws IOException { return next.open(pending); }
        @Override public int write(java.nio.channels.FileChannel channel, java.nio.ByteBuffer buffer) throws IOException {
            return next.write(channel, buffer);
        }
        @Override public void force(java.nio.channels.FileChannel channel) throws IOException { next.force(channel); }
        @Override public void close(java.nio.channels.FileChannel channel) throws IOException { next.close(channel); }
        @Override public CompoundTag readStaged(Path pending, ManagedSavedDataType type) { return next.readStaged(pending, type); }
        @Override public void move(Path staged, Path target) throws IOException { next.move(staged, target); }
        @Override public void forceDirectory(Path directory) throws IOException { next.forceDirectory(directory); }
        @Override public void delete(Path pending) throws IOException { next.delete(pending); }
    }
}

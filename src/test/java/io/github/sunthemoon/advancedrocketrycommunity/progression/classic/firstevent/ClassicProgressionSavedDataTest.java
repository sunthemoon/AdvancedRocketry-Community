package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W44 A0: the classic progression root, its per-kind proof and its absent-versus-unreadable rule (sections 3.1-3.6). */
class ClassicProgressionSavedDataTest {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.CLASSIC_PROGRESSION;
    private static final CheckedSavedDataPolicy.DirectoryForce NO_FORCE = directory -> { };
    @TempDir Path root;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aProvenAbsentRootValidatesAbsentAndConfirmsOnlyThroughTheBarrier() {
        var data = ClassicProgressionSavedData.provenAbsent();
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, data.presence());
        for (FirstEventKind kind : FirstEventKind.values()) { assertTrue(data.validatedAbsent(kind)); }
        var event = warp(1, List.of(id(4)));
        assertTrue(data.insert(event));
        assertFalse(data.insert(event), "an equal receipt is a no-op");
        assertThrows(IllegalStateException.class, () -> data.insert(warp(2, List.of())), "receipts never change online");
        assertTrue(data.receiptPending(FirstEventKind.FIRST_STATION_WARP));
        assertTrue(data.confirmedReceipt(FirstEventKind.FIRST_STATION_WARP).isEmpty());
        assertTrue(data.validatedAbsent(FirstEventKind.FIRST_MOON_LANDING));
        assertTrue(data.barrier(file(), CheckedSavedDataFile::atomicMove, NO_FORCE).confirmed());
        assertEquals(Optional.of(event), data.confirmedReceipt(FirstEventKind.FIRST_STATION_WARP));
        assertFalse(data.receiptPending(FirstEventKind.FIRST_STATION_WARP));

        CompoundTag payload = CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow();
        assertEquals(ClassicProgressionSavedData.FORMAT_EPOCH, TYPE.formatEpoch());
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT, SavedDataSchemaMigrator.migrate(TYPE, payload).status());
        var loaded = ClassicProgressionSavedData.load(payload);
        assertEquals(ManagedRootPresence.LOADED, loaded.presence());
        assertEquals(Optional.of(event), loaded.confirmedReceipt(FirstEventKind.FIRST_STATION_WARP),
                "a strictly loaded receipt initializes its own proof");
        assertTrue(loaded.validatedAbsent(FirstEventKind.FIRST_MOON_LANDING), "occurred 0 is validated absent");
        assertFalse(loaded.isDirty());
        assertEquals(payload, loaded.save(new CompoundTag()));
    }

    @Test void flushOrForceFailureLeavesTheReceiptPendingAndAnotherKindConfirmed() throws Exception {
        var data = ClassicProgressionSavedData.provenAbsent();
        var warp = warp(1, List.of(id(4)));
        data.insert(warp);
        assertTrue(data.barrier(file(), CheckedSavedDataFile::atomicMove, NO_FORCE).confirmed());
        var moon = moon(2, List.of(id(5)));
        data.insert(moon);
        byte[] before = Files.readAllBytes(file());
        var flush = data.barrier(file(), (staged, target) -> {
            throw new AtomicMoveNotSupportedException(staged.toString(), target.toString(), "injected");
        }, NO_FORCE);
        assertEquals(ClassicProgressionSavedData.Barrier.Stage.FLUSH, flush.stage());
        assertArrayEquals(before, Files.readAllBytes(file()));
        assertTrue(data.receiptPending(FirstEventKind.FIRST_MOON_LANDING));
        assertEquals(Optional.of(warp), data.confirmedReceipt(FirstEventKind.FIRST_STATION_WARP));
        var force = data.barrier(file(), CheckedSavedDataFile::atomicMove, directory -> {
            throw new IOException("injected directory force failure");
        });
        assertEquals(ClassicProgressionSavedData.Barrier.Stage.FORCE, force.stage());
        assertTrue(data.isDirty(), "a force failure keeps the root eligible for a retry");
        assertTrue(data.receiptPending(FirstEventKind.FIRST_MOON_LANDING), "the moved file is not yet proof");
        assertEquals(Optional.of(warp), data.confirmedReceipt(FirstEventKind.FIRST_STATION_WARP));
        // An ordinary save writes the receipt but never confirms it.
        data.save(file().toFile());
        assertFalse(data.isDirty());
        assertTrue(data.receiptPending(FirstEventKind.FIRST_MOON_LANDING));
        assertTrue(data.barrier(file(), CheckedSavedDataFile::atomicMove, NO_FORCE).confirmed());
        assertEquals(Optional.of(moon), data.confirmedReceipt(FirstEventKind.FIRST_MOON_LANDING));
        assertFalse(Files.exists(pending()));
    }

    @Test void strictDecodeBlocksAndPreservesEveryDeviation() {
        var data = ClassicProgressionSavedData.provenAbsent();
        data.insert(moon(1, List.of(id(3))));
        CompoundTag valid = data.save(new CompoundTag());
        List<java.util.function.Consumer<CompoundTag>> mutations = List.of(
                tag -> tag.putInt("schema_version", 2),
                tag -> tag.putString("format_epoch", "v1.8.0-other"),
                tag -> tag.putInt("extra", 1),
                tag -> tag.remove("first_station_warp"),
                tag -> tag.getCompound("first_station_warp").putString("source", "stations"),
                tag -> tag.getCompound("first_moon_landing").putByte("occurred", (byte) 2),
                tag -> tag.getCompound("first_moon_landing").putString("source", "stations"),
                tag -> tag.getCompound("first_moon_landing").getCompound("event").putString("fingerprint", "0".repeat(64)),
                tag -> tag.put("first_station_warp", tag.getCompound("first_moon_landing").copy()));
        for (var mutation : mutations) {
            CompoundTag invalid = valid.copy();
            mutation.accept(invalid);
            var blocked = ClassicProgressionSavedData.load(invalid);
            assertEquals(ManagedRootPresence.BLOCKED, blocked.presence());
            assertFalse(blocked.operational());
            assertFalse(blocked.validatedAbsent(FirstEventKind.FIRST_STATION_WARP), "blocked is never absent");
            assertEquals(invalid, blocked.save(new CompoundTag()));
            assertFalse(blocked.isDirty());
            assertThrows(IllegalStateException.class, () -> blocked.insert(warp(9, List.of())));
        }
    }

    @Test void theMaximumRootFitsItsBound() {
        var data = ClassicProgressionSavedData.provenAbsent();
        data.insert(warp(1, participants(4_096, 10)));
        data.insert(moon(2, participants(16, 9_000)));
        CompoundTag encoded = data.save(new CompoundTag());
        assertNotNull(io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt.measure(encoded,
                ClassicProgressionSavedData.MAX_ROOT_BYTES, ClassicProgressionSavedData.MAX_DEPTH,
                ClassicProgressionSavedData.MAX_NODES));
        assertTrue(ClassicProgressionSavedData.load(encoded).operational());
    }

    /** A0 case a: a real storage, a present file whose load throws, a failing stat and a missing file. */
    @Test void aRealStorageTellsAnUnreadableRootFromAnAbsentOne() throws Exception {
        var seeded = ClassicProgressionSavedData.provenAbsent();
        seeded.insert(warp(1, List.of(id(7))));
        seeded.flush(file());
        byte[] original = Files.readAllBytes(file());

        var storage = TestDataStorage.over(root);
        ClassicProgressionSavedData unreadable = ManagedRootSupplier.obtain(storage, file(), TYPE, tag -> {
            throw new IllegalStateException("injected runtime read failure");
        }, ClassicProgressionSavedData::created, CheckedSavedDataFile::presence);
        assertUnreadable(unreadable);
        storage.save();
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertFalse(Files.exists(pending()));
        assertSame(unreadable, storage.computeIfAbsent(ClassicProgressionSavedData::load,
                ClassicProgressionSavedData::provenAbsent, TYPE.dataName()), "never re-read within the session");

        Path other = Files.createDirectory(root.resolve("stat"));
        var statFailed = ManagedRootSupplier.obtain(TestDataStorage.over(other), other.resolve(TYPE.fileName()), TYPE,
                ClassicProgressionSavedData::load, ClassicProgressionSavedData::created,
                path -> CheckedSavedDataFile.Presence.UNKNOWN);
        assertUnreadable(statFailed);
        assertFalse(Files.exists(other.resolve(TYPE.fileName())));

        Path empty = Files.createDirectory(root.resolve("absent"));
        var absent = ManagedRootSupplier.obtain(TestDataStorage.over(empty), empty.resolve(TYPE.fileName()), TYPE,
                ClassicProgressionSavedData::load, ClassicProgressionSavedData::created, CheckedSavedDataFile::presence);
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, absent.presence());
        assertTrue(absent.validatedAbsent(FirstEventKind.FIRST_MOON_LANDING));
    }

    /** A0 case b: a file that appears before the first write of a proven-absent root is never replaced. */
    @Test void aFileThatAppearsBeforeTheFirstWriteIsKeptAndTheRootBecomesUnreadable() throws Exception {
        var data = ClassicProgressionSavedData.provenAbsent();
        data.insert(moon(1, List.of(id(2))));
        Files.writeString(file(), "appeared after the proof");
        var barrier = data.barrier(file(), CheckedSavedDataFile::atomicMove, NO_FORCE);
        assertEquals(ClassicProgressionSavedData.Barrier.Stage.FLUSH, barrier.stage());
        assertEquals("appeared after the proof", Files.readString(file()));
        assertFalse(Files.exists(pending()));
        assertUnreadable(data);

        // After the session's first known commit the file is its own, and no further re-check applies.
        Path owned = Files.createDirectory(root.resolve("owned")).resolve(TYPE.fileName());
        var own = ClassicProgressionSavedData.provenAbsent();
        own.insert(warp(1, List.of()));
        assertTrue(own.barrier(owned, CheckedSavedDataFile::atomicMove, NO_FORCE).confirmed());
        own.insert(moon(2, List.of()));
        assertTrue(own.barrier(owned, CheckedSavedDataFile::atomicMove, NO_FORCE).confirmed());
    }

    private void assertUnreadable(ClassicProgressionSavedData data) throws Exception {
        assertEquals(ManagedRootPresence.UNREADABLE, data.presence());
        assertFalse(data.operational());
        assertFalse(data.isDirty());
        for (FirstEventKind kind : FirstEventKind.values()) {
            assertFalse(data.validatedAbsent(kind), "unreadable is never absent");
            assertTrue(data.receipt(kind).isEmpty());
        }
        assertThrows(IllegalStateException.class, () -> data.insert(warp(9, List.of())));
        assertEquals(ClassicProgressionSavedData.Barrier.Stage.UNAVAILABLE,
                data.barrier(file(), CheckedSavedDataFile::atomicMove, NO_FORCE).stage());
        assertThrows(IllegalStateException.class, () -> data.flush(file()));
        assertThrows(IllegalStateException.class, () -> data.save(new CompoundTag()));
        data.setDirty();
        assertFalse(data.isDirty(), "an unwritable root is never dirty");
        assertFalse(Files.exists(pending()));
    }

    private Path file() { return root.resolve(TYPE.fileName()); }

    private Path pending() { return root.resolve(TYPE.fileName() + ".arce-pending"); }
}

package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W44 A0, section 3.6: only a no-such-file stat proves absence; the writable gate stages nothing. */
class ManagedRootPresenceTest {
    @TempDir Path root;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyNoSuchFileProvesAbsence() throws Exception {
        Path file = root.resolve("advancedrocketrycommunity_classic_progression.dat");
        assertEquals(CheckedSavedDataFile.Presence.ABSENT, CheckedSavedDataFile.presence(file));
        Files.writeString(file, "x");
        assertEquals(CheckedSavedDataFile.Presence.PRESENT, CheckedSavedDataFile.presence(file));
        Path directory = Files.createDirectory(root.resolve("directory.dat"));
        assertEquals(CheckedSavedDataFile.Presence.PRESENT, CheckedSavedDataFile.presence(directory),
                "any entry, not only a regular file, is present");
        assertEquals(CheckedSavedDataFile.Presence.UNKNOWN, CheckedSavedDataFile.presence(file, path -> {
            throw new IOException("injected sharing violation");
        }));
        assertEquals(CheckedSavedDataFile.Presence.UNKNOWN, CheckedSavedDataFile.presence(file, path -> {
            throw new SecurityException("injected denial");
        }));
        assertEquals(ManagedRootPresence.PROVEN_ABSENT, ManagedRootSupplier.classify(root.resolve("none.dat"),
                ManagedSavedDataType.STATIONS, CheckedSavedDataFile::presence));
        assertEquals(ManagedRootPresence.UNREADABLE, ManagedRootSupplier.classify(file,
                ManagedSavedDataType.STATIONS, CheckedSavedDataFile::presence));
        assertEquals(ManagedRootPresence.UNREADABLE, ManagedRootSupplier.classify(root.resolve("none.dat"),
                ManagedSavedDataType.STATIONS, path -> CheckedSavedDataFile.Presence.UNKNOWN));
        assertTrue(ManagedRootPresence.LOADED.operational());
        assertTrue(ManagedRootPresence.PROVEN_ABSENT.operational());
        assertFalse(ManagedRootPresence.UNREADABLE.operational());
        assertFalse(ManagedRootPresence.BLOCKED.operational());
    }

    @Test void anUnwritableAtomicRootIsNeverStagedAndBeforeCommitCanRefuseTheMove() throws Exception {
        var type = ManagedSavedDataType.CELESTIAL;
        Path file = root.resolve(type.fileName());
        Files.writeString(file, "intact");
        boolean[] writable = {false};
        var data = new AtomicSavedData(type) {
            @Override public CompoundTag save(CompoundTag tag) { return CelestialSavedData.create().save(tag); }
            @Override protected boolean writable() { return writable[0]; }
            @Override protected void beforeCommit(Path target) throws IOException {
                if (Files.exists(target) && Files.readString(target).equals("intact")) {
                    throw new IOException("injected appeared entry");
                }
            }
        };
        data.setDirty();
        data.save(file.toFile());
        assertThrows(IllegalStateException.class, () -> data.flush(file));
        assertEquals("intact", Files.readString(file));
        assertFalse(Files.exists(root.resolve(type.fileName() + ".arce-pending")));
        writable[0] = true;
        assertThrows(java.io.UncheckedIOException.class, () -> data.flush(file));
        assertEquals("intact", Files.readString(file), "beforeCommit refused before the atomic move");
        assertFalse(Files.exists(root.resolve(type.fileName() + ".arce-pending")));
        Files.delete(file);
        data.flush(file);
        assertFalse(data.isDirty());
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicSavedDataTest {
    @TempDir Path root;
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.CELESTIAL;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void checkedAndOrdinarySavesKeepTheWrapperAndUseAtomicReplacement() throws Exception {
        var data = CelestialSavedData.create();
        Path file = root.resolve(TYPE.fileName());
        data.flush(file); // A clean new object must still acknowledge an explicit barrier.
        assertFalse(data.isDirty());
        var outer = BoundedSavedDataIo.read(file, TYPE);
        assertEquals(SharedConstants.getCurrentVersion().getDataVersion().getVersion(), outer.getInt("DataVersion"));
        assertEquals(data.save(new CompoundTag()), outer.getCompound("data"));
        data.discover(ModIdentity.id("mars"), 19);
        data.save(file.toFile());
        assertFalse(data.isDirty());
        assertEquals(data.save(new CompoundTag()), BoundedSavedDataIo.read(file, TYPE).getCompound("data"));
        assertFalse(Files.exists(pending()));
    }

    @Test void failedMovePreservesAuthorityAndDirtyStateWithoutNonAtomicFallback() throws Exception {
        var data = CelestialSavedData.create();
        Path file = root.resolve(TYPE.fileName()); data.flush(file);
        byte[] original = Files.readAllBytes(file);
        data.discover(ModIdentity.id("mars"), 21);
        assertThrows(UncheckedIOException.class, () -> ((AtomicSavedData) data).flush(file, (from, to) -> {
            assertEquals(data.save(new CompoundTag()), BoundedSavedDataIo.read(from, TYPE).getCompound("data"));
            throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "injected");
        }));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertTrue(data.isDirty());
        assertFalse(Files.exists(pending()));
        data.save(file.toFile());
        assertFalse(data.isDirty());
        assertEquals(data.save(new CompoundTag()), BoundedSavedDataIo.read(file, TYPE).getCompound("data"));
    }

    @Test void ordinaryFailureRetainsDirtyStateAndExplicitFailurePropagates() throws Exception {
        var data = CelestialSavedData.create();
        data.discover(ModIdentity.id("venus"), 23);
        Path file = root.resolve(TYPE.fileName());
        Files.createDirectory(pending());
        assertDoesNotThrow(() -> data.save(file.toFile()));
        assertTrue(data.isDirty());
        assertFalse(Files.exists(file));
        assertThrows(UncheckedIOException.class, () -> data.flush(file));
        assertTrue(Files.isDirectory(pending()));
        Files.delete(pending()); // Only this test's empty temporary obstacle.
        data.save(file.toFile());
        assertFalse(data.isDirty());
        assertEquals(data.save(new CompoundTag()), BoundedSavedDataIo.read(file, TYPE).getCompound("data"));
    }

    @Test void orphanStagingBytesNeverBecomeAnAuthority() throws Exception {
        var data = CelestialSavedData.create();
        data.recordVisit(ModIdentity.id("earth"), 17);
        Path file = root.resolve(TYPE.fileName()); data.flush(file);
        Files.writeString(pending(), "interrupted partial staging data");
        var restored = CelestialSavedData.load(BoundedSavedDataIo.read(file, TYPE).getCompound("data"));
        restored.flush(file);
        assertEquals(data.entries(), restored.entries());
        assertFalse(Files.exists(pending()));
    }

    @Test void wrongIdentityAndNonRegularAuthorityAreNeverOverwritten() throws Exception {
        var data = CelestialSavedData.create();
        Path wrong = root.resolve("unrelated.dat"); Files.writeString(wrong, "keep");
        assertThrows(UncheckedIOException.class, () -> data.flush(wrong));
        assertEquals("keep", Files.readString(wrong));
        Path directory = root.resolve(TYPE.fileName()); Files.createDirectory(directory);
        assertThrows(UncheckedIOException.class, () -> data.flush(directory));
        assertTrue(Files.isDirectory(directory));
    }

    @Test void oversizedExpandedPayloadCannotReplaceAValidAuthority() throws Exception {
        var valid = CelestialSavedData.create(); Path file = root.resolve(TYPE.fileName()); valid.flush(file);
        byte[] original = Files.readAllBytes(file);
        var oversized = new AtomicSavedData(TYPE) {
            @Override public CompoundTag save(CompoundTag tag) {
                tag.putByteArray("too_large", new byte[(int) TYPE.maxCompressedBytes() + 1]);
                return tag;
            }
        };
        assertThrows(SavedDataMigrationException.class, () -> oversized.flush(file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertTrue(oversized.isDirty());
        assertFalse(Files.exists(pending()));
    }

    private Path pending() { return root.resolve(TYPE.fileName() + ".arce-pending"); }
}

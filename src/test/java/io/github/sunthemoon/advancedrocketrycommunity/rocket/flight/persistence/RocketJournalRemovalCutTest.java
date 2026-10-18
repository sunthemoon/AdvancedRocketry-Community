package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketJournalRemovalCutTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void selectorRequiresAnOrdinaryPreparedIdentityToDisappear() {
        var empty = root(null); var prepared = root("PREPARED");
        assertTrue(RocketJournalRemovalCut.ordinaryRemoval(empty, prepared));
        assertFalse(RocketJournalRemovalCut.ordinaryRemoval(prepared.copy(), prepared));
        assertFalse(RocketJournalRemovalCut.ordinaryRemoval(empty, null));
        assertFalse(RocketJournalRemovalCut.ordinaryRemoval(prepared, empty));
        assertFalse(RocketJournalRemovalCut.ordinaryRemoval(empty, root("COMMITTED")));
        prepared.getList("transfers", 10).getCompound(0).put("native_transfer", new CompoundTag());
        assertFalse(RocketJournalRemovalCut.ordinaryRemoval(empty, prepared));
    }

    @Test void pauseRunsAfterAtomicReplacementWithoutChangingEitherPayload() {
        var file = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var prepared = root("PREPARED"); var empty = root(null);
        CheckedSavedDataFile.replace(file, ManagedSavedDataType.ROCKET_TRANSFERS, () -> prepared);
        var calls = new AtomicInteger();
        var policy = RocketJournalRemovalCut.policy(() -> {
            assertEquals(empty, CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow());
            calls.incrementAndGet();
        });
        policy.replace(file, prepared::copy);
        assertEquals(0, calls.get());
        policy.replace(file, empty::copy);
        assertEquals(1, calls.get());
        assertEquals(empty, policy.read(file).orElseThrow());
        assertEquals(root("PREPARED"), prepared);
    }

    @Test void absentServerCannotEnableTheCutAndBoundsCannotGrow() {
        var property = System.getProperty(RocketJournalFailurePolicy.PROPERTY);
        var hooks = System.getProperty(EscrowTestEndpoints.HOOKS);
        try {
            System.setProperty(RocketJournalFailurePolicy.PROPERTY, RocketJournalRemovalCut.MODE);
            for (var gate : new String[] {"false", "true"}) {
                System.setProperty(EscrowTestEndpoints.HOOKS, gate);
                assertTrue(RocketJournalRemovalCut.checked(null).isEmpty());
                assertNotNull(RocketJournalFailurePolicy.checked(null));
            }
        } finally {
            restore(RocketJournalFailurePolicy.PROPERTY, property); restore(EscrowTestEndpoints.HOOKS, hooks);
        }
        assertThrows(IllegalArgumentException.class, () -> RocketJournalRemovalCut.awaitBounded(0));
        assertThrows(IllegalArgumentException.class, () -> RocketJournalRemovalCut.awaitBounded(20_000_000_001L));
        RocketJournalRemovalCut.awaitBounded(1_000_000);
    }

    private static void restore(String key, String value) {
        if (value == null) { System.clearProperty(key); } else { System.setProperty(key, value); }
    }

    private static CompoundTag root(String phase) {
        var root = new CompoundTag(); var records = new ListTag();
        if (phase != null) {
            var record = new CompoundTag(); record.putUUID("transfer_id", new UUID(0, 1));
            record.putString("phase", phase); records.add(record);
        }
        root.put("transfers", records); return root;
    }
}

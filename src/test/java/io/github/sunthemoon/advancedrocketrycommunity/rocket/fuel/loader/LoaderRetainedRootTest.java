package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoaderRetainedRootTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void absentValidAndWrittenStatesResetRetention() {
        var state = new LoaderRetainedRoot();
        state.load(new CompoundTag());
        assertEquals(LoaderRoot.Status.ABSENT, state.status);
        assertTrue(state.heldRoot().isEmpty());
        var root = LoaderRoot.placed(UUID.randomUUID(), UUID.randomUUID(), LoaderTank.EMPTY);
        state.write(root);
        assertEquals(root, state.root);
        assertEquals(root.encode(), saved(state));
        state.load(new CompoundTag());
        assertNull(state.root);
        assertNull(saved(state));
    }

    @Test void boundedUnsupportedRootsAreDetachedAndSavedExactly() {
        for (int schema : new int[] {1, 2}) {
            var raw = new CompoundTag(); raw.putInt("schema_version", schema); raw.putString("opaque", "kept");
            var state = loaded(raw);
            assertEquals(schema == 1 ? LoaderRoot.Status.INVALID : LoaderRoot.Status.FUTURE, state.status);
            assertEquals(raw, saved(state));
            assertNotSame(raw, state.heldRoot().orElseThrow());
            raw.putString("opaque", "changed");
            assertEquals("kept", ((CompoundTag) saved(state)).getString("opaque"));
        }
    }

    @Test void operationallyOversizedRootsAreRetainedByReference() {
        var raw = new CompoundTag(); raw.putInt("schema_version", 1); raw.putByteArray("opaque", new byte[40_000]);
        var state = loaded(raw);
        assertEquals(LoaderRoot.Status.INVALID, state.status);
        assertSame(raw, state.heldRoot().orElseThrow());
        assertSame(raw, saved(state));
    }

    private static LoaderRetainedRoot loaded(Tag raw) {
        var outer = new CompoundTag(); outer.put(LoaderRoot.KEY, raw);
        var state = new LoaderRetainedRoot(); state.load(outer); return state;
    }

    private static Tag saved(LoaderRetainedRoot state) {
        var outer = new CompoundTag(); state.save(outer); return outer.get(LoaderRoot.KEY);
    }
}

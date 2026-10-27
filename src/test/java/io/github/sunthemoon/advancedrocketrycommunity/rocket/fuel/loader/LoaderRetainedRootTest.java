package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.StringTag;
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
        assertFalse(state.unkeepable);
    }

    @Test void boundedUnsupportedRootsAreDetachedAndSavedExactly() {
        for (int schema : new int[] {1, 2}) {
            var raw = new CompoundTag(); raw.putInt("schema_version", schema); raw.putString("opaque", "kept");
            var state = loaded(raw);
            assertEquals(schema == 1 ? LoaderRoot.Status.INVALID : LoaderRoot.Status.FUTURE, state.status);
            assertEquals(raw, saved(state));
            assertFalse(state.unkeepable);
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
        assertFalse(state.unkeepable);
    }

    @Test void nativeWorkLimitNeverBecomesASaveDenial() {
        var raw = new CompoundTag(); raw.putByteArray("opaque", new byte[2_097_153]);
        var state = loaded(raw);
        assertFalse(state.unkeepable);
        assertSame(raw, saved(state));
    }

    @Test void observedNativeIncompatibilitiesRemainRawUntilReplacedByValidData() {
        var nan = new CompoundTag(); nan.put("opaque", FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001)));
        var deep = new CompoundTag(); var cursor = deep;
        for (int i = 0; i < 513; i++) { var child = new CompoundTag(); cursor.put("child", child); cursor = child; }
        for (Tag raw : new Tag[] {nan, deep, StringTag.valueOf("x".repeat(65_536))}) {
            var state = loaded(raw);
            assertTrue(state.unkeepable);
            if (raw == nan) {
                assertEquals(0x7fc00001, Float.floatToRawIntBits(((CompoundTag) saved(state)).getFloat("opaque")));
            } else { assertSame(raw, saved(state)); }
            state.write(LoaderRoot.placed(UUID.randomUUID(), UUID.randomUUID(), LoaderTank.EMPTY));
            assertFalse(state.unkeepable);
        }
    }

    private static LoaderRetainedRoot loaded(Tag raw) {
        var outer = new CompoundTag(); outer.put(LoaderRoot.KEY, raw);
        var state = new LoaderRetainedRoot(); state.load(outer); return state;
    }

    private static Tag saved(LoaderRetainedRoot state) {
        var outer = new CompoundTag(); state.save(outer); return outer.get(LoaderRoot.KEY);
    }
}

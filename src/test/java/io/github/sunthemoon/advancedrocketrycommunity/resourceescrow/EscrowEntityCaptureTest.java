package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Review W11b1 finding 4: entity capture bounds each entity once, with the old whole-list decisions. */
class EscrowEntityCaptureTest {
    private static final int NODES = 262_144;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    /** Item-like entity tag: 25 int fields, so 26 nodes. */
    private static CompoundTag item(int index) {
        CompoundTag tag = new CompoundTag();
        for (int field = 0; field < 25; field++) { tag.putInt(String.format("k%02d", field), index + field); }
        return tag;
    }
    @Test void tenThousandEntitiesAreBoundedInLinearTimeAndRefusedExactlyPastTheNodeLimit() {
        int fitting = (NODES - 1) / 26; // 10,082; the list itself is one node.
        var items = new ArrayList<CompoundTag>(); for (int i = 0; i <= fitting; i++) { items.add(item(i)); }
        var list = new EscrowChunkStore.EntityList();
        long started = System.nanoTime();
        for (int i = 0; i < fitting; i++) { list.add(items.get(i)); }
        long elapsed = System.nanoTime() - started;
        // The review measured 49.6 s for the old per-entity whole-list check at this size; one walk is milliseconds.
        assertTrue(elapsed < TimeUnit.SECONDS.toNanos(2), "Entity capture bound took " + TimeUnit.NANOSECONDS.toMillis(elapsed) + " ms");
        assertEquals(fitting, list.tag().size());
        assertTrue(BoundedNbt.fits(list.tag(), AnvilRecordReader.DECODED_BYTES, 64, NODES), "The old whole-list check disagrees");
        var overflow = list.tag().copy(); overflow.add(items.get(fitting));
        assertFalse(BoundedNbt.fits(overflow, AnvilRecordReader.DECODED_BYTES, 64, NODES));
        var refused = assertThrows(IllegalArgumentException.class, () -> list.add(items.get(fitting)));
        assertEquals("Entity capture limit exceeded", refused.getMessage());
        assertEquals(fitting, list.tag().size(), "A refused entity must not be appended or truncated");
        // The complete vanilla envelope still fits the single root check that validate() performs.
        CompoundTag root = NbtUtils.addCurrentDataVersion(new CompoundTag());
        root.putIntArray("Position", new int[] {0, 0}); root.put("Entities", list.tag());
        var size = BoundedNbt.measure(root, AnvilRecordReader.DECODED_BYTES, 64, NODES);
        assertNotNull(size); assertEquals(4 + 26L * fitting, size.nodes()); assertEquals(RocketNbtSize.uncompressedBytes(root), size.bytes());
    }
    @Test void theDecodedByteBoundIsExactAndMatchesTheWholeListCheck() {
        // Two entities with a 2,097,139-byte array reach exactly 4 MiB: 8 + 2 * (2,097,139 + 9) bytes.
        var list = new EscrowChunkStore.EntityList(); var reference = new ListTag();
        for (int i = 0; i < 2; i++) {
            CompoundTag entity = new CompoundTag(); entity.putByteArray("b", new byte[2_097_139]);
            list.add(entity); reference.add(entity);
        }
        var named = BoundedNbt.measure(reference, AnvilRecordReader.DECODED_BYTES, 64, NODES);
        assertNotNull(named); assertEquals(AnvilRecordReader.DECODED_BYTES, named.bytes());
        var extra = new CompoundTag(); reference.add(extra);
        assertFalse(BoundedNbt.fits(reference, AnvilRecordReader.DECODED_BYTES, 64, NODES));
        assertThrows(IllegalArgumentException.class, () -> list.add(extra));
        var deep = new EscrowChunkStore.EntityList(); CompoundTag nested = new CompoundTag(), cursor = nested;
        for (int depth = 1; depth < 62; depth++) { CompoundTag child = new CompoundTag(); cursor.put("c", child); cursor = child; }
        deep.add(nested); // 62 levels below the "Entities" list: the deepest entity the root check accepts.
        CompoundTag deeper = new CompoundTag(); deeper.put("c", nested);
        assertThrows(IllegalArgumentException.class, () -> new EscrowChunkStore.EntityList().add(deeper));
    }
}

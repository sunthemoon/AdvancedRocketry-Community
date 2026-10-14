package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A0 checks of the schema-1 arce_unlit_torch root at its field, ID and 2 KiB/depth 8/64-node limits. */
class UnlitTorchRootTest {
    private static final UnlitTorchRoot.Identity VANILLA_WALL = new UnlitTorchRoot.Identity(id("minecraft:torch"),
            id("minecraft:wall_torch"), id("minecraft:torch"));

    @Test
    void encodesExactlyFourFieldsAndRoundTrips() {
        CompoundTag root = UnlitTorchRoot.encode(VANILLA_WALL);
        assertEquals(Set.of("schema_version", "pair", "block", "item"), root.getAllKeys());
        assertEquals(Tag.TAG_INT, root.getTagType("schema_version"));
        assertEquals(1, root.getInt("schema_version"));
        assertEquals("minecraft:wall_torch", root.getString("block"));
        assertEquals(Optional.of(VANILLA_WALL), UnlitTorchRoot.decode(root));
        assertTrue(UnlitTorchRoot.bounded(root));
    }

    @Test
    void anythingButAnExactSchemaOneRootDecodesToNothing() {
        CompoundTag future = UnlitTorchRoot.encode(VANILLA_WALL);
        future.putInt("schema_version", 2);
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(future));

        CompoundTag extra = UnlitTorchRoot.encode(VANILLA_WALL);
        extra.putString("owner", "someone");
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(extra));

        CompoundTag missing = UnlitTorchRoot.encode(VANILLA_WALL);
        missing.remove("item");
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(missing));

        CompoundTag wrongType = UnlitTorchRoot.encode(VANILLA_WALL);
        wrongType.putByte("schema_version", (byte) 1);
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(wrongType));

        CompoundTag numericId = UnlitTorchRoot.encode(VANILLA_WALL);
        numericId.putInt("pair", 7);
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(numericId));

        for (String bad : new String[] {"torch", "minecraft:Torch", "minecraft:", "bad id:x"}) {
            CompoundTag invalid = UnlitTorchRoot.encode(VANILLA_WALL);
            invalid.putString("block", bad);
            assertEquals(Optional.empty(), UnlitTorchRoot.decode(invalid), bad);
        }
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(IntTag.valueOf(1)));
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(new ListTag()));
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(null));
    }

    @Test
    void idsAreLimitedTo128Utf8Bytes() {
        String longest = "example:" + "p".repeat(128 - "example:".length());
        UnlitTorchRoot.Identity identity = new UnlitTorchRoot.Identity(id(longest), id(longest), id(longest));
        assertEquals(Optional.of(identity), UnlitTorchRoot.decode(UnlitTorchRoot.encode(identity)));
        assertThrows(IllegalArgumentException.class,
                () -> new UnlitTorchRoot.Identity(id(longest + "p"), id("minecraft:torch"), id("minecraft:torch")));
        CompoundTag over = UnlitTorchRoot.encode(VANILLA_WALL);
        over.putString("item", longest + "p");
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(over));
    }

    @Test
    void boundIsTwoKibibytesDepthEightAndSixtyFourNodes() {
        // Native size of a named root compound holding one byte array "b": 3 + 1 + 3 + 1 + 4 + length.
        CompoundTag bytes = new CompoundTag();
        bytes.putByteArray("b", new byte[UnlitTorchRoot.MAX_BYTES - 12]);
        assertEquals(UnlitTorchRoot.MAX_BYTES, BoundedNbt.measure(bytes, Integer.MAX_VALUE, 64, 64).bytes());
        assertTrue(UnlitTorchRoot.bounded(bytes));
        bytes.putByteArray("b", new byte[UnlitTorchRoot.MAX_BYTES - 11]);
        assertFalse(UnlitTorchRoot.bounded(bytes));

        assertTrue(UnlitTorchRoot.bounded(nested(UnlitTorchRoot.MAX_DEPTH)));
        assertFalse(UnlitTorchRoot.bounded(nested(UnlitTorchRoot.MAX_DEPTH + 1)));

        assertTrue(UnlitTorchRoot.bounded(wide(UnlitTorchRoot.MAX_NODES - 1)));
        assertFalse(UnlitTorchRoot.bounded(wide(UnlitTorchRoot.MAX_NODES)));

        CompoundTag oversizedIdentity = UnlitTorchRoot.encode(VANILLA_WALL);
        oversizedIdentity.putByteArray("padding", new byte[UnlitTorchRoot.MAX_BYTES]);
        assertEquals(Optional.empty(), UnlitTorchRoot.decode(oversizedIdentity));
    }

    @Test
    void largestValidRootStaysFarInsideTheBound() {
        String longest = "example:" + "p".repeat(128 - "example:".length());
        UnlitTorchRoot.Identity identity = new UnlitTorchRoot.Identity(id(longest), id(longest), id(longest));
        BoundedNbt.Size size = BoundedNbt.measure(UnlitTorchRoot.encode(identity), UnlitTorchRoot.MAX_BYTES,
                UnlitTorchRoot.MAX_DEPTH, UnlitTorchRoot.MAX_NODES);
        assertTrue(size != null && size.bytes() < 512 && size.nodes() == 5, String.valueOf(size));
    }

    /** A chain of compounds: the root plus {@code depth - 1} nested children. */
    private static CompoundTag nested(int depth) {
        CompoundTag root = new CompoundTag();
        CompoundTag current = root;
        for (int level = 1; level < depth; level++) {
            CompoundTag child = new CompoundTag();
            current.put("c", child);
            current = child;
        }
        return root;
    }

    /** One root compound and {@code children} string children: {@code children + 1} nodes. */
    private static CompoundTag wide(int children) {
        CompoundTag root = new CompoundTag();
        for (int index = 0; index < children; index++) {
            root.put("k" + index, StringTag.valueOf("v"));
        }
        return root;
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}

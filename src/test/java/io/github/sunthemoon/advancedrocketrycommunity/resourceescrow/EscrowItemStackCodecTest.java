package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemStackCodecTest {
    @BeforeAll static void bootstrap() {
        MinecraftBootstrap.initialize();
        for (int length : List.of(127, 128, 129)) {
            String id = "w110:" + "i".repeat(length - 5);
            if (!BuiltInRegistries.ITEM.containsKey(new net.minecraft.resources.ResourceLocation(id))) {
                Registry.register(BuiltInRegistries.ITEM, id, new Item(new Item.Properties()));
            }
        }
    }
    static CompoundTag stack(int blob) {
        var saved = new ItemStack(Items.STONE, 64).save(new CompoundTag());
        if (blob >= 0) { var tag = new CompoundTag(); tag.putByteArray("blob", new byte[blob]); saved.put("tag", tag); }
        return saved;
    }
    static CompoundTag bytes(int size) {
        return stack(size - RocketNbtSize.uncompressedBytes(stack(0)));
    }
    static CompoundTag depth(int depth) {
        var saved = stack(-1); var tag = new CompoundTag(); saved.put("tag", tag);
        for (int i = 2; i < depth; i++) { var next = new CompoundTag(); tag.put("c", next); tag = next; }
        return saved;
    }
    static CompoundTag nodes(int nodes) {
        var saved = stack(-1); var tag = new CompoundTag(); var list = new ListTag();
        for (int i = 0; i < nodes - 5; i++) { list.add(ByteTag.valueOf((byte) i)); }
        tag.put("n", list); saved.put("tag", tag); return saved;
    }
    static void refused(EscrowItemStackCodec.Status status, Runnable work) {
        assertEquals(status, assertThrows(EscrowItemStackCodec.Refusal.class, work::run).status());
    }

    @Test void localByteDepthAndNodeBoundariesAreInclusiveAndNeverModifyInput() {
        for (int size : List.of(4_095, 4_096)) {
            var tag = bytes(size); assertEquals(size, RocketNbtSize.uncompressedBytes(tag));
            assertEquals(tag, EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(tag)));
        }
        var over = bytes(4_097); var copy = over.copy();
        refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE, () -> EscrowItemStackCodec.decode(over)); assertEquals(copy, over);
        for (int depth : List.of(15, 16)) { assertEquals(depth(depth), EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(depth(depth)))); }
        refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE, () -> EscrowItemStackCodec.decode(depth(17)));
        for (int count : List.of(511, 512)) {
            var tag = nodes(count); assertEquals(count, BoundedNbt.measure(tag, 4_096, 16, 512).nodes());
            assertEquals(tag, EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(tag)));
        }
        refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE, () -> EscrowItemStackCodec.decode(nodes(513)));
        var live = new ItemStack(Items.STONE); live.setTag(depth(17)); var original = live.getTag();
        refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE, () -> EscrowItemStackCodec.encode(live)); assertSame(original, live.getTag());
    }

    @Test void registeredIdsMustBeCanonicalAsciiNonAirAndAtMost128Characters() {
        for (int length : List.of(127, 128)) {
            var saved = stack(-1); saved.putString("id", "w110:" + "i".repeat(length - 5));
            assertEquals(saved, EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(saved)));
        }
        var longId = stack(-1); longId.putString("id", "w110:" + "i".repeat(124));
        refused(EscrowItemStackCodec.Status.CARGO_TOO_LARGE, () -> EscrowItemStackCodec.decode(longId));
        for (String id : List.of("minecraft:air", "w110:missing", "stone", "minecraft:STONE", "minecraft:\u00e9")) {
            var bad = stack(-1); bad.putString("id", id);
            refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(bad));
        }
    }

    @Test void exactNativeCountAndItemSpecificLimitsAreRequired() {
        for (var item : List.of(Items.STONE, Items.ENDER_PEARL, Items.DIAMOND_SWORD)) {
            int max = Math.min(64, new ItemStack(item).getMaxStackSize());
            for (int count : List.of(1, max)) {
                var saved = new ItemStack(item, count).save(new CompoundTag());
                assertEquals(saved, EscrowItemStackCodec.encode(EscrowItemStackCodec.decode(saved)));
            }
            for (int count : List.of(-1, 0, max + 1)) {
                var saved = new ItemStack(item).save(new CompoundTag()); saved.putByte("Count", (byte) count);
                refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(saved));
            }
        }
        var badType = stack(-1); badType.putInt("Count", 1);
        refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(badType));
        refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.encode(new ItemStack(Items.STONE, 257)));
        refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.encode(ItemStack.EMPTY));
    }

    @Test void capabilityKeysUnknownFieldsMissingFieldsAndLossyNativeInputsAreRefused() {
        var caps = stack(-1); caps.put("ForgeCaps", new CompoundTag());
        var nested = stack(-1); var tag = new CompoundTag(); tag.put("ForgeCaps", new CompoundTag()); nested.put("tag", tag);
        var list = stack(-1); var tags = new ListTag(); tags.add(tag); var wrapper = new CompoundTag(); wrapper.put("list", tags); list.put("tag", wrapper);
        for (var bad : List.of(caps, nested, list)) { refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(bad)); }
        for (String field : List.of("id", "Count")) {
            var bad = stack(-1); bad.remove(field); refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(bad));
        }
        var unknown = stack(-1); unknown.putByte("Slot", (byte) 0);
        refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(unknown));
        var badTag = stack(-1); badTag.putString("tag", "not a compound");
        refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(badTag));
        var sword = new ItemStack(Items.DIAMOND_SWORD).save(new CompoundTag()); sword.remove("tag"); var original = sword.copy();
        refused(EscrowItemStackCodec.Status.INVALID_DATA, () -> EscrowItemStackCodec.decode(sword)); assertEquals(original, sword);
    }

    @Test void mergeIdentityIncludesTypedTagsAndItemNotDisplayNameOrCount() {
        var a = stack(-1); var tag = new CompoundTag(); tag.putInt("x", 1); tag.putString("label", "same"); a.put("tag", tag);
        var b = a.copy(); b.putByte("Count", (byte) 1);
        assertTrue(EscrowItemStackCodec.sameItemAndTags(a, b));
        var reordered = new CompoundTag(); reordered.putString("label", "same"); reordered.putInt("x", 1); b.put("tag", reordered);
        assertTrue(EscrowItemStackCodec.sameItemAndTags(a, b));
        b.getCompound("tag").putByte("x", (byte) 1); assertFalse(EscrowItemStackCodec.sameItemAndTags(a, b));
        b = a.copy(); b.putString("id", "minecraft:dirt"); assertFalse(EscrowItemStackCodec.sameItemAndTags(a, b));
        var decoded = EscrowItemStackCodec.decode(a); decoded.getTag().putInt("x", 99); assertEquals(1, a.getCompound("tag").getInt("x"));
    }
}

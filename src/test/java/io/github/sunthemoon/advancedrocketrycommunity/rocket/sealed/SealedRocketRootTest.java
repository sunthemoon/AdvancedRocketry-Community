package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SealedRocketRootTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void nativeNormalizationsAreTheOnlyChangesAndTheSecondSaveIsStable() throws IOException {
        CompoundTag loaded = read(nativeFixture());
        CompoundTag original = loaded.getCompound(SealedRocketMember.ROOT);
        assertEquals(1, ((ListTag) original.get("empty")).getElementType());
        assertEquals(0x7fc00001, Float.floatToRawIntBits(original.getFloat("nan_float")));
        assertEquals(0x7ff8000000000001L, Double.doubleToRawLongBits(original.getDouble("nan_double")));
        var retained = SealedRocketRoot.load(loaded);
        assertTrue(retained.member().isEmpty());
        CompoundTag first = save(retained);
        byte[] firstBytes = write(first);
        CompoundTag firstNative = read(firstBytes);
        assertNativeEquivalent(loaded, firstNative);
        byte[] second = write(save(SealedRocketRoot.load(firstNative)));
        assertArrayEquals(firstBytes, second);
        assertNativeEquivalent(firstNative, read(second));
        // The writer's mutable ListTag normalization never touches either the input or the retained copy.
        assertEquals(1, ((ListTag) original.get("empty")).getElementType());
        assertEquals(1, ((ListTag) save(retained).getCompound(SealedRocketMember.ROOT).get("empty")).getElementType());
        assertEquals(0x7fc00001, Float.floatToRawIntBits(save(retained).getCompound(SealedRocketMember.ROOT).getFloat("nan_float")));
    }

    @Test void everyMalformedShapeKeepsItsTagOrAbsenceRatherThanSynthesizingAValidMember() throws IOException {
        var future = controls(); future.putInt("schema", 2);
        var unknown = member().encode(); unknown.putString("future", "retained");
        for (Tag tag : List.of(IntTag.valueOf(23), StringTag.valueOf("opaque"), new ListTag(), future, unknown)) {
            var outer = new CompoundTag(); outer.put(SealedRocketMember.ROOT, tag);
            var retained = SealedRocketRoot.load(read(write(outer)));
            assertTrue(retained.member().isEmpty());
            assertNativeEquivalent(outer, read(write(save(retained))));
        }
        var absent = SealedRocketRoot.load(new CompoundTag());
        var outer = member().encode(); outer.putString(SealedRocketMember.ROOT, "stale");
        absent.save(outer);
        assertFalse(outer.contains(SealedRocketMember.ROOT));
        assertTrue(save(SealedRocketRoot.absent()).isEmpty());
    }

    @Test void loadedRootsAboveTheStagingBoundAreStillSavedByTheNativeWriter() throws IOException {
        var body = controls(); body.putInt("schema", 2);
        byte[] bytes = new byte[SealedRocketMember.MAX_BYTES + 1];
        for (int i = 0; i < bytes.length; i++) { bytes[i] = (byte) (i * 31); }
        body.putByteArray("oversized", bytes);
        var outer = new CompoundTag(); outer.put(SealedRocketMember.ROOT, body);
        var retained = SealedRocketRoot.load(read(write(outer)));
        assertTrue(retained.member().isEmpty());
        assertArrayEquals(write(outer), write(save(retained)));
    }

    @Test void loadAndSaveBothDefendTheOwnedRootFromMutableAliases() {
        var body = controls(); var outer = new CompoundTag(); outer.put(SealedRocketMember.ROOT, body);
        var retained = SealedRocketRoot.load(outer);
        var expected = body.copy(); body.putInt("foreign", 99); body.getByteArray("bytes")[0] = 99;
        var saved = save(retained); assertEquals(expected, saved.get(SealedRocketMember.ROOT));
        saved.getCompound(SealedRocketMember.ROOT).putInt("foreign", 101);
        saved.getCompound(SealedRocketMember.ROOT).getByteArray("bytes")[0] = 101;
        assertEquals(expected, save(retained).get(SealedRocketMember.ROOT));
    }

    @Test void stagedMembersUseTheExistingCodecAndReloadWithoutReencoding() {
        var member = member(); var retained = SealedRocketRoot.stage(member);
        assertEquals(member, retained.member().orElseThrow());
        var saved = save(retained);
        assertEquals(member.encode(), saved.get(SealedRocketMember.ROOT));
        var reloaded = SealedRocketRoot.load(saved);
        assertEquals(member, reloaded.member().orElseThrow());
        assertEquals(saved, save(reloaded));
    }

    private static CompoundTag controls() {
        var tag = new CompoundTag();
        tag.putByte("byte", (byte) -127); tag.putShort("short", (short) -32767);
        tag.putInt("int", Integer.MIN_VALUE); tag.putLong("long", Long.MAX_VALUE);
        tag.putFloat("float", -0.0F); tag.putDouble("double", Double.NEGATIVE_INFINITY);
        tag.putString("text", "opaque\u0000\u7bb1");
        tag.putByteArray("bytes", new byte[] {-1, 0, 127});
        tag.putIntArray("ints", new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
        tag.putLongArray("longs", new long[] {Long.MIN_VALUE, 0, Long.MAX_VALUE});
        var list = new ListTag(); list.add(StringTag.valueOf("first")); list.add(StringTag.valueOf("last"));
        tag.put("list", list); var nested = new CompoundTag(); nested.putBoolean("boolean", true); tag.put("nested", nested);
        return tag;
    }

    private static byte[] nativeFixture() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeByte(Tag.TAG_COMPOUND); out.writeUTF("");
            out.writeByte(Tag.TAG_COMPOUND); out.writeUTF(SealedRocketMember.ROOT);
            out.writeByte(Tag.TAG_INT); out.writeUTF("schema"); out.writeInt(2);
            out.writeByte(Tag.TAG_FLOAT); out.writeUTF("nan_float"); out.writeInt(0x7fc00001);
            out.writeByte(Tag.TAG_DOUBLE); out.writeUTF("nan_double"); out.writeLong(0x7ff8000000000001L);
            out.writeByte(Tag.TAG_LIST); out.writeUTF("empty"); out.writeByte(Tag.TAG_BYTE); out.writeInt(0);
            out.writeByte(Tag.TAG_COMPOUND); out.writeUTF("controls"); controls().write(out);
            out.writeByte(Tag.TAG_END); out.writeByte(Tag.TAG_END);
        }
        return bytes.toByteArray();
    }

    private static void assertNativeEquivalent(Tag before, Tag after) {
        assertEquals(before.getId(), after.getId());
        if (before instanceof CompoundTag original) {
            var saved = (CompoundTag) after;
            assertEquals(original.getAllKeys(), saved.getAllKeys());
            for (String key : original.getAllKeys()) { assertNativeEquivalent(original.get(key), saved.get(key)); }
        } else if (before instanceof ListTag original) {
            var saved = (ListTag) after;
            assertEquals(original.size(), saved.size());
            assertEquals(original.isEmpty() ? Tag.TAG_END : original.getElementType(), saved.getElementType());
            for (int i = 0; i < original.size(); i++) { assertNativeEquivalent(original.get(i), saved.get(i)); }
        } else if (before instanceof FloatTag original) {
            assertEquals(Float.floatToIntBits(original.getAsFloat()), Float.floatToRawIntBits(((FloatTag) after).getAsFloat()));
        } else if (before instanceof DoubleTag original) {
            assertEquals(Double.doubleToLongBits(original.getAsDouble()), Double.doubleToRawLongBits(((DoubleTag) after).getAsDouble()));
        } else { assertEquals(before, after); }
    }
    private static CompoundTag save(SealedRocketRoot root) { var tag = new CompoundTag(); root.save(tag); return tag; }
    private static CompoundTag read(byte[] bytes) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) { return NbtIo.read(in); }
    }
    private static byte[] write(CompoundTag tag) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) { NbtIo.write(tag, out); }
        return bytes.toByteArray();
    }
    private static SealedRocketMember member() {
        return new SealedRocketMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "a".repeat(64),
                ResourceLocation.tryParse("minecraft:overworld"), new RocketPosition(1, 2, 3), 0,
                new RocketBlockState(ResourceLocation.tryParse("minecraft:stone"), Map.of()), null);
    }
}

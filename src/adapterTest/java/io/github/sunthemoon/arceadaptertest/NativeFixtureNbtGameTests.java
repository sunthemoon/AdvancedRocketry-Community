package io.github.sunthemoon.arceadaptertest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeFixtureNbtGameTests {
    private NativeFixtureNbtGameTests() { }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_bounds")
    public static void nativeSnapshotEnvelopeRejectsOversizedAndDeepTrees(GameTestHelper helper) {
        CompoundTag root = new CompoundTag();
        root.putInt("marker", 28);
        helper.assertTrue(NativeFixtureNbt.fits(root), "Bounded native marker refused");
        root.put("oversized", new ByteArrayTag(new byte[1_048_576]));
        helper.assertFalse(NativeFixtureNbt.fits(root), "Oversized native array admitted");
        root.remove("oversized");
        CompoundTag child = root;
        for (int index = 0; index < 32; index++) {
            CompoundTag next = new CompoundTag();
            child.put("child", next);
            child = next;
        }
        helper.assertFalse(NativeFixtureNbt.fits(root), "Depth 33 admitted");
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_bounds")
    public static void preflightDoesNotNormalizeAFormerlyTypedEmptyList(GameTestHelper helper) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(28));
        list.remove(0);
        CompoundTag root = new CompoundTag();
        root.put("typed_empty", list);
        int before = list.getElementType();
        helper.assertTrue(before == IntTag.TAG_INT, "Fixture did not retain its former native list type");
        helper.assertFalse(NativeFixtureNbt.fits(root), "Native serialization would normalize the typed empty list");
        helper.assertTrue(list.getElementType() == before, "Preflight mutated the native list type");
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_bounds")
    public static void exactByteNodeAndUtfBoundariesAreRetained(GameTestHelper helper) {
        helper.assertTrue(NativeFixtureNbt.fits(new ByteArrayTag(new byte[1_048_576 - 7])), "Exact byte ceiling refused");
        helper.assertFalse(NativeFixtureNbt.fits(new ByteArrayTag(new byte[1_048_576 - 6])), "Byte ceiling plus one admitted");
        ListTag nodes = new ListTag();
        for (int index = 0; index < 65_535; index++) { nodes.add(IntTag.valueOf(1)); }
        helper.assertTrue(NativeFixtureNbt.fits(nodes), "Exact node ceiling refused");
        nodes.add(IntTag.valueOf(1));
        helper.assertFalse(NativeFixtureNbt.fits(nodes), "Node ceiling plus one admitted");
        helper.assertTrue(NativeFixtureNbt.fits(StringTag.valueOf("\u0800".repeat(21_845))), "Exact native UTF ceiling refused");
        helper.assertFalse(NativeFixtureNbt.fits(StringTag.valueOf("\u0800".repeat(21_846))), "Overlong native UTF admitted");
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_bounds")
    public static void foreignNumericIdsAndNoncanonicalNanAreRejected(GameTestHelper helper) {
        CompoundTag numericImposter = new CompoundTag() {
            @Override public byte getId() { return Tag.TAG_INT; }
        };
        helper.assertFalse(NativeFixtureNbt.fits(numericImposter), "Foreign numeric writer admitted by its ID");
        helper.assertFalse(NativeFixtureNbt.fits(FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001))), "Raw float NaN admitted");
        helper.assertFalse(NativeFixtureNbt.fits(DoubleTag.valueOf(Double.longBitsToDouble(0x7ff8000000000001L))), "Raw double NaN admitted");
        helper.assertTrue(NativeFixtureNbt.fits(FloatTag.valueOf(Float.NaN))
                        && NativeFixtureNbt.fits(DoubleTag.valueOf(Double.NaN)), "Canonical NaN refused");
        helper.succeed();
    }
}

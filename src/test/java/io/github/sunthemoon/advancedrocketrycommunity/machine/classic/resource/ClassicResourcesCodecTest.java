package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.ArrayDeque;
import java.util.UUID;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicResourcesCodecTest {
    private static final UUID OWNER = new UUID(12, 34);
    private static final ClassicBankKey ITEM = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, -10, 64, 30);
    private static final ClassicBankKey FLUID = new ClassicBankKey(ClassicBankKind.FLUID_OUTPUT, 9, -64, -30);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void roundTripsNativeItemsFluidAndEveryNonnegativeLongRevision() {
        for (long revision : new long[]{0, 1, 4_294_967_296L, Long.MAX_VALUE}) {
            ClassicResources value = ClassicResources.restore(OWNER, revision, List.of(
                    ClassicResourceBank.items(ITEM, ClassicResourceBankTest.slots(
                            ClassicResourceBankTest.taggedItem(Items.STONE, 64, "input"))),
                    ClassicResourceBank.fluid(FLUID, ClassicResourceBankTest.taggedFluid(16_000, "output"))));
            CompoundTag root = ClassicResourcesCodec.encode(value);
            ClassicResourcesDecode decoded = ClassicResourcesCodec.decode(root, OWNER);
            assertEquals(ClassicResourcesDecode.Status.SUPPORTED, decoded.status());
            assertEquals(root, decoded.encodeForSave());
            assertEquals(revision, decoded.value().orElseThrow().revision());
            assertFalse(decoded.requiresSaveRefusal());
        }
        CompoundTag empty = ClassicResourcesCodec.encode(ClassicResources.empty(OWNER));
        assertEquals(empty, ClassicResourcesCodec.decode(empty, OWNER).encodeForSave());
    }

    @Test void encodeAndDecodeDetachEveryNativeTagInBothDirections() {
        ClassicResources resources = value();
        CompoundTag root = ClassicResourcesCodec.encode(resources);
        CompoundTag saved = root.copy();
        itemPayload(root).getCompound("tag").putString("batch", "encoded");
        fluidPayload(root).getCompound("Tag").putString("batch", "encoded");
        assertEquals(saved, ClassicResourcesCodec.encode(resources));
        ClassicResources decoded = ClassicResourcesCodec.decode(saved, OWNER).value().orElseThrow();
        CompoundTag before = ClassicResourcesCodec.encode(decoded);
        itemPayload(saved).getCompound("tag").putString("batch", "caller");
        fluidPayload(saved).getCompound("Tag").putString("batch", "caller");
        decoded.banks().get(0).item(0).getTag().putString("batch", "getter");
        decoded.banks().get(1).fluid().getTag().putString("batch", "getter");
        assertEquals(before, ClassicResourcesCodec.encode(decoded));
    }

    @Test void missingFutureInvalidAndOwnerConflictNeverBecomeEmptyResources() {
        ClassicResourcesDecode missing = ClassicResourcesCodec.decode(null, OWNER);
        assertEquals(ClassicResourcesDecode.Status.MISSING, missing.status());
        assertTrue(missing.requiresSaveRefusal()); assertTrue(missing.value().isEmpty());
        assertThrows(IllegalStateException.class, missing::encodeForSave);
        CompoundTag future = root(); future.putInt("schema_version", 2);
        assertRetained(future, ClassicResourcesDecode.Status.UNSUPPORTED_SCHEMA);
        CompoundTag wrongOwner = root(); wrongOwner.putUUID("machine_uuid", new UUID(99, 100));
        assertRetained(wrongOwner, ClassicResourcesDecode.Status.OWNER_CONFLICT);
        assertRetained(IntTag.valueOf(7), ClassicResourcesDecode.Status.INVALID_DATA);
    }

    @Test void boundedRefusalOwnsUnknownPayloadAndSaveCopyRatherThanCallerAliases() {
        CompoundTag future = root(); future.putInt("schema_version", 27);
        CompoundTag original = future.copy();
        ClassicResourcesDecode result = ClassicResourcesCodec.decode(future, OWNER);
        future.putInt("schema_version", 1);
        assertEquals(original, result.encodeForSave());
        ((CompoundTag) result.encodeForSave()).remove("schema_version");
        assertEquals(original, result.encodeForSave());
    }

    @Test void allRootFieldsRequireExactTypesAndNoExtraFields() {
        CompoundTag valid = root();
        for (String key : valid.getAllKeys()) {
            CompoundTag missing = valid.copy(); missing.remove(key); assertInvalid(missing);
            CompoundTag wrong = valid.copy(); wrong.put(key, StringTag.valueOf("retain")); assertInvalid(wrong);
        }
        CompoundTag extra = valid.copy(); extra.putInt("extra", 1); assertInvalid(extra);
        CompoundTag badRevision = valid.copy(); badRevision.putLong("revision", -1); assertInvalid(badRevision);
        CompoundTag narrowRevision = valid.copy(); narrowRevision.putInt("revision", 1); assertInvalid(narrowRevision);
        CompoundTag badUuid = valid.copy(); badUuid.putIntArray("machine_uuid", new int[]{1, 2, 3}); assertInvalid(badUuid);
        CompoundTag wrongList = valid.copy(); ListTag strings = new ListTag(); strings.add(StringTag.valueOf("x"));
        wrongList.put("banks", strings); assertInvalid(wrongList);
    }

    @Test void bankFieldsCanonicalKeyCoordinatesKindsAndSlotShapeAreStrict() {
        CompoundTag valid = root();
        CompoundTag first = banks(valid).getCompound(0);
        for (String key : first.getAllKeys()) {
            CompoundTag missing = valid.copy(); banks(missing).getCompound(0).remove(key); assertInvalid(missing);
            CompoundTag wrong = valid.copy(); banks(wrong).getCompound(0).put(key, ByteTag.valueOf((byte) 1)); assertInvalid(wrong);
        }
        for (String channel : new String[]{"item_input.-010.64.30", "item_input.+1.64.30", "item_output.-10.64.30"}) {
            CompoundTag bad = valid.copy(); banks(bad).getCompound(0).putString("channel", channel); assertInvalid(bad);
        }
        CompoundTag wrongPosition = valid.copy(); banks(wrongPosition).getCompound(0).putInt("x", 4); assertInvalid(wrongPosition);
        CompoundTag wrongKind = valid.copy(); banks(wrongKind).getCompound(0).putString("kind", "fluid_input"); assertInvalid(wrongKind);
        CompoundTag extra = valid.copy(); banks(extra).getCompound(0).putInt("extra", 1); assertInvalid(extra);
        CompoundTag missingSlot = valid.copy(); ((ListTag) banks(missingSlot).getCompound(0).get("items")).remove(3); assertInvalid(missingSlot);
        CompoundTag wrongSlots = valid.copy(); ListTag slots = new ListTag();
        for (int i = 0; i < 4; i++) { slots.add(StringTag.valueOf("not an Item")); }
        banks(wrongSlots).getCompound(0).put("items", slots); assertInvalid(wrongSlots);
        CompoundTag extraFluid = valid.copy(); banks(extraFluid).getCompound(1).putInt("extra", 1); assertInvalid(extraFluid);
        CompoundTag wrongFluid = valid.copy(); banks(wrongFluid).getCompound(1).putString("fluid", "not a Fluid"); assertInvalid(wrongFluid);
    }

    @Test void duplicateOr65BankRootsRefuseWithoutMergingOrTruncating() {
        CompoundTag duplicate = root(); banks(duplicate).add(banks(duplicate).getCompound(0).copy()); assertInvalid(duplicate);
        CompoundTag tooMany = ClassicResourcesCodec.encode(ClassicResources.empty(OWNER));
        ListTag banks = banks(tooMany);
        for (int i = 0; i < 65; i++) { banks.add(ClassicResourcesTest.bank(i).frame()); }
        assertTrue(ClassicResourcesCodec.bounded(tooMany)); assertInvalid(tooMany);
    }

    @Test void itemIdentityCountsCapabilityPayloadAndNativeTypesRefuseLossyNormalization() {
        CompoundTag valid = root();
        for (String id : new String[]{"missing:item", "minecraft:air", "stone", "UPPER:bad", "x:" + "a".repeat(127)}) {
            CompoundTag bad = valid.copy(); itemPayload(bad).putString("id", id); assertInvalid(bad);
        }
        for (int count : new int[]{0, -1, 65, 127}) {
            CompoundTag bad = valid.copy(); itemPayload(bad).putByte("Count", (byte) count); assertInvalid(bad);
        }
        CompoundTag narrow = valid.copy(); itemPayload(narrow).putString("id", "minecraft:ender_pearl");
        itemPayload(narrow).putByte("Count", (byte) 17); assertInvalid(narrow);
        for (String field : new String[]{"ForgeCaps", "other"}) {
            CompoundTag bad = valid.copy(); itemPayload(bad).put(field, new CompoundTag()); assertInvalid(bad);
        }
        CompoundTag wrongTag = valid.copy(); itemPayload(wrongTag).putString("tag", "bad"); assertInvalid(wrongTag);
        CompoundTag wrongCount = valid.copy(); itemPayload(wrongCount).putInt("Count", 1); assertInvalid(wrongCount);
    }

    @Test void fluidIdentityAmountsAndNativeTypesRefuseLossyNormalization() {
        CompoundTag valid = root();
        for (String id : new String[]{"missing:fluid", "minecraft:empty", "water", "UPPER:bad", "x:" + "a".repeat(127)}) {
            CompoundTag bad = valid.copy(); fluidPayload(bad).putString("FluidName", id); assertInvalid(bad);
        }
        for (int amount : new int[]{Integer.MIN_VALUE, -1, 0, 16_001, Integer.MAX_VALUE}) {
            CompoundTag bad = valid.copy(); fluidPayload(bad).putInt("Amount", amount); assertInvalid(bad);
        }
        CompoundTag extra = valid.copy(); fluidPayload(extra).put("ForgeCaps", new CompoundTag()); assertInvalid(extra);
        CompoundTag wrongTag = valid.copy(); fluidPayload(wrongTag).putString("Tag", "bad"); assertInvalid(wrongTag);
        CompoundTag wrongAmount = valid.copy(); fluidPayload(wrongAmount).putLong("Amount", 1000); assertInvalid(wrongAmount);
    }

    @Test void exact32768ByteBoundaryAcceptsAndOneByteExcessRetainsWithoutCopy() throws IOException {
        CompoundTag valid = root();
        CompoundTag metadata = fluidPayload(valid).getCompound("Tag");
        metadata.putByteArray("bytes", new byte[0]);
        int bytes = ClassicResourcesCodec.MAX_BYTES - bytes(valid);
        metadata.putByteArray("bytes", new byte[bytes]);
        assertEquals(ClassicResourcesCodec.MAX_BYTES, bytes(valid));
        assertEquals(ClassicResourcesDecode.Status.SUPPORTED, ClassicResourcesCodec.decode(valid, OWNER).status());
        metadata.putByteArray("bytes", new byte[bytes + 1]);
        assertEquals(ClassicResourcesCodec.MAX_BYTES + 1, bytes(valid));
        ClassicResourcesDecode oversized = ClassicResourcesCodec.decode(valid, OWNER);
        assertEquals(ClassicResourcesDecode.Status.UNBOUNDED, oversized.status());
        assertTrue(oversized.retainsUnboundedIdentity(valid)); assertTrue(oversized.value().isEmpty());
        assertTrue(oversized.requiresSaveRefusal()); assertThrows(IllegalStateException.class, oversized::encodeForSave);
        assertEquals(bytes + 1, metadata.getByteArray("bytes").length);
    }

    @Test void excessiveDepthNodesAndBytesPrecedeRecursiveCopies() throws IOException {
        CompoundTag huge = new ClassicResourceBankTest.NoCopyTag();
        huge.putByteArray("bytes", new byte[ClassicResourcesCodec.MAX_BYTES]);
        ClassicResourcesDecode result = ClassicResourcesCodec.decode(huge, OWNER);
        assertTrue(result.retainsUnboundedIdentity(huge));
        CompoundTag deep = new ClassicResourceBankTest.NoCopyTag(); CompoundTag cursor = deep;
        for (int depth = 0; depth < ClassicResourcesCodec.MAX_DEPTH; depth++) {
            CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child;
        }
        assertEquals(ClassicResourcesDecode.Status.UNBOUNDED, ClassicResourcesCodec.decode(deep, OWNER).status());
        CompoundTag wide = new ClassicResourceBankTest.NoCopyTag(); ListTag nodes = new ListTag();
        for (int i = 0; i < ClassicResourcesCodec.MAX_NODES; i++) { nodes.add(ByteTag.valueOf((byte) 1)); }
        wide.put("nodes", nodes); assertTrue(bytes(wide) < ClassicResourcesCodec.MAX_BYTES);
        assertEquals(ClassicResourcesDecode.Status.UNBOUNDED, ClassicResourcesCodec.decode(wide, OWNER).status());
    }

    @Test void boundedOrdinaryNestedItemAndFluidMetadataRemainVerbatim() {
        CompoundTag valid = root();
        for (CompoundTag metadata : List.of(itemPayload(valid).getCompound("tag"), fluidPayload(valid).getCompound("Tag"))) {
            metadata.putIntArray("coordinates", new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
            metadata.putLongArray("history", new long[]{Long.MIN_VALUE, 17, Long.MAX_VALUE});
            CompoundTag nested = new CompoundTag(); nested.putString("unicode", "\u6c27\u6c14");
            ListTag list = new ListTag(); list.add(LongTag.valueOf(9)); list.add(LongTag.valueOf(12));
            nested.put("values", list); metadata.put("nested", nested);
        }
        assertEquals(valid, ClassicResourcesCodec.decode(valid, OWNER).encodeForSave());
    }

    @Test void aggregateDepth16IsInclusiveAnd17Refuses() {
        CompoundTag valid = root();
        CompoundTag cursor = fluidPayload(valid).getCompound("Tag");
        for (int i = 0; i < 10; i++) { CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child; }
        cursor.putInt("leaf", 1);
        assertTrue(ClassicResourcesCodec.bounded(valid));
        assertEquals(ClassicResourcesDecode.Status.SUPPORTED, ClassicResourcesCodec.decode(valid, OWNER).status());
        CompoundTag child = new CompoundTag(); child.putInt("leaf", 1); cursor.put("extra", child);
        assertFalse(ClassicResourcesCodec.bounded(valid));
        assertEquals(ClassicResourcesDecode.Status.UNBOUNDED, ClassicResourcesCodec.decode(valid, OWNER).status());
    }

    @Test void aggregate8192NodeBoundaryIsInclusiveWithoutExceedingBytes() throws IOException {
        CompoundTag valid = root(); ListTag entries = new ListTag();
        itemPayload(valid).getCompound("tag").put("entries", entries);
        int available = ClassicResourcesCodec.MAX_NODES - nodes(valid);
        for (int i = 0; i < available; i++) { entries.add(ByteTag.valueOf((byte) 1)); }
        assertEquals(ClassicResourcesCodec.MAX_NODES, nodes(valid));
        assertTrue(bytes(valid) < ClassicResourcesCodec.MAX_BYTES);
        assertEquals(ClassicResourcesDecode.Status.SUPPORTED, ClassicResourcesCodec.decode(valid, OWNER).status());
        entries.add(ByteTag.valueOf((byte) 1));
        assertEquals(ClassicResourcesCodec.MAX_NODES + 1, nodes(valid));
        assertEquals(ClassicResourcesDecode.Status.UNBOUNDED, ClassicResourcesCodec.decode(valid, OWNER).status());
    }

    private static void assertInvalid(Tag raw) { assertRetained(raw, ClassicResourcesDecode.Status.INVALID_DATA); }
    private static void assertRetained(Tag raw, ClassicResourcesDecode.Status expected) {
        ClassicResourcesDecode result = ClassicResourcesCodec.decode(raw, OWNER);
        assertEquals(expected, result.status()); assertTrue(result.value().isEmpty());
        assertFalse(result.requiresSaveRefusal()); assertEquals(raw, result.encodeForSave());
    }
    static ClassicResources value() {
        return ClassicResources.empty(OWNER).replace(List.of(
                ClassicResourceBank.items(ITEM, ClassicResourceBankTest.slots(ClassicResourceBankTest.taggedItem(Items.STONE, 5, "item"))),
                ClassicResourceBank.fluid(FLUID, ClassicResourceBankTest.taggedFluid(1000, "fluid"))), false);
    }
    static CompoundTag root() { return ClassicResourcesCodec.encode(value()); }
    static ListTag banks(CompoundTag root) { return (ListTag) root.get("banks"); }
    static CompoundTag itemPayload(CompoundTag root) { return ((ListTag) banks(root).getCompound(0).get("items")).getCompound(0); }
    static CompoundTag fluidPayload(CompoundTag root) { return banks(root).getCompound(1).getCompound("fluid"); }
    private static int bytes(Tag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); root.write(new DataOutputStream(bytes));
        return bytes.size() + 3;
    }
    private static int nodes(Tag root) {
        ArrayDeque<Tag> pending = new ArrayDeque<>(); pending.push(root); int count = 0;
        while (!pending.isEmpty()) {
            Tag tag = pending.pop(); count++;
            if (tag instanceof CompoundTag compound) {
                for (String key : compound.getAllKeys()) { pending.push(compound.get(key)); }
            } else if (tag instanceof ListTag list) { for (Tag child : list) { pending.push(child); } }
        }
        return count;
    }
}

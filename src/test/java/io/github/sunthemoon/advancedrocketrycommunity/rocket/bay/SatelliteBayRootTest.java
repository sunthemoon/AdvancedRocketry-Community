package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A0: the schema-1 bay root at every bound, just inside and just outside (W27A-DESIGN section 6). */
class SatelliteBayRootTest {
    private static final UUID OWNER = UUID.fromString("0f6c1d2e-3a4b-4c5d-8e9f-a0b1c2d3e4f5");
    private static final UUID OP = UUID.fromString("11111111-2222-4333-8444-555555555555");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static CompoundTag item() {
        CompoundTag pkg = new CompoundTag();
        pkg.putString("id", "advancedrocketrycommunity:satellite_package");
        pkg.putByte("Count", (byte) 1);
        pkg.put("tag", new CompoundTag());
        return pkg;
    }

    private static CompoundTag root(CompoundTag pkg) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 1);
        root.putUUID("owner", OWNER);
        if (pkg != null) {
            root.put("package", pkg);
        }
        return root;
    }

    private static long bytes(Tag tag) {
        BoundedNbt.Size size = BoundedNbt.measure(tag, Integer.MAX_VALUE, 1_024, Integer.MAX_VALUE);
        assertNotNull(size);
        return size.bytes();
    }

    private static int nodes(Tag tag) {
        return BoundedNbt.measure(tag, Integer.MAX_VALUE, 1_024, Integer.MAX_VALUE).nodes();
    }

    /** A package of exactly {@code target} native bytes, padded through one byte array in its item tag. */
    private static CompoundTag packageOfBytes(int target) {
        CompoundTag pkg = item();
        pkg.getCompound("tag").putByteArray("pad", new byte[0]);
        int padding = (int) (target - bytes(pkg));
        pkg.getCompound("tag").putByteArray("pad", new byte[padding]);
        assertEquals(target, bytes(pkg));
        return pkg;
    }

    /** A package whose deepest node sits at {@code depth}, the package itself being depth 1. */
    private static CompoundTag packageOfDepth(int depth) {
        CompoundTag pkg = item();
        CompoundTag cursor = pkg.getCompound("tag");
        for (int level = 3; level <= depth; level++) {
            CompoundTag next = new CompoundTag();
            cursor.put("n", next);
            cursor = next;
        }
        assertTrue(BoundedNbt.fits(pkg, 4_096, depth, 256));
        assertFalse(BoundedNbt.fits(pkg, 4_096, depth - 1, 256));
        return pkg;
    }

    /** A package of exactly {@code count} nodes: the item, its id, Count and tag, then byte leaves. */
    private static CompoundTag packageOfNodes(int count) {
        CompoundTag pkg = item();
        for (int leaf = 0; leaf < count - 4; leaf++) {
            pkg.getCompound("tag").putByte("b" + leaf, (byte) 0);
        }
        assertEquals(count, nodes(pkg));
        return pkg;
    }

    private static SatelliteBayRoot.Status status(CompoundTag root) {
        return SatelliteBayRoot.decode(root).status();
    }

    private static CompoundTag edited(Consumer<CompoundTag> edit) {
        CompoundTag root = root(item());
        edit.accept(root);
        return root;
    }

    @Test
    void theThreeValidShapesDecodeAndReEncodeEqually() {
        CompoundTag empty = root(null);
        CompoundTag loaded = root(item());
        CompoundTag consumed = root(null);
        consumed.putUUID("consumed_op", OP);
        for (CompoundTag root : new CompoundTag[] {empty, loaded, consumed}) {
            SatelliteBayRoot.Decoded decoded = SatelliteBayRoot.decode(root);
            assertTrue(decoded.valid(), root.toString());
            assertEquals(root, SatelliteBayRoot.encode(decoded.contents().orElseThrow()));
        }
        assertEquals(OWNER, SatelliteBayRoot.decode(loaded).contents().orElseThrow().owner());
        assertEquals(item(), SatelliteBayRoot.decode(loaded).contents().orElseThrow().packageTag().orElseThrow());
        assertEquals(OP, SatelliteBayRoot.decode(consumed).contents().orElseThrow().consumed().orElseThrow());
    }

    @Test
    void thePackageByteBoundIsTheNativeItemBound() {
        assertEquals(SatelliteBayRoot.Status.VALID, status(root(packageOfBytes(4_096))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(root(packageOfBytes(4_097))));
    }

    @Test
    void thePackageDepthBoundIsSixteen() {
        assertEquals(SatelliteBayRoot.Status.VALID, status(root(packageOfDepth(16))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(root(packageOfDepth(17))));
    }

    @Test
    void thePackageNodeBoundIs256() {
        assertEquals(SatelliteBayRoot.Status.VALID, status(root(packageOfNodes(256))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(root(packageOfNodes(257))));
    }

    @Test
    void thePackageIsExactlyOneItem() {
        for (byte count : new byte[] {0, 2, 64, -1}) {
            CompoundTag pkg = item();
            pkg.putByte("Count", count);
            assertEquals(SatelliteBayRoot.Status.INVALID, status(root(pkg)), "Count " + count);
        }
        CompoundTag widened = item();
        widened.putInt("Count", 1);
        assertEquals(SatelliteBayRoot.Status.INVALID, status(root(widened)));
        CompoundTag missing = item();
        missing.remove("Count");
        assertEquals(SatelliteBayRoot.Status.INVALID, status(root(missing)));
        CompoundTag noId = item();
        noId.remove("id");
        assertEquals(SatelliteBayRoot.Status.INVALID, status(root(noId)));
        CompoundTag numericId = item();
        numericId.putInt("id", 3);
        assertEquals(SatelliteBayRoot.Status.INVALID, status(root(numericId)));
        // Other native item keys stay raw: the package decoder, not the root codec, judges them.
        CompoundTag extra = item();
        extra.putString("future_item_field", "kept");
        assertEquals(SatelliteBayRoot.Status.VALID, status(root(extra)));
    }

    @Test
    void theFieldSetIsClosedAndTyped() {
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.putString("extra", "x"))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.putUUID("consumed_op", OP))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.remove("owner"))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.putString("owner", OWNER.toString()))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.putIntArray("owner", new int[] {1, 2, 3}))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.putString("package", "item"))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.remove("schema_version"))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.put("schema_version", LongTag.valueOf(1)))));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(edited(root -> root.putInt("schema_version", 0))));
        assertEquals(SatelliteBayRoot.Status.FUTURE, status(edited(root -> root.putInt("schema_version", 2))));
        CompoundTag consumedWrongType = root(null);
        consumedWrongType.putString("consumed_op", OP.toString());
        assertEquals(SatelliteBayRoot.Status.INVALID, status(consumedWrongType));
        assertEquals(SatelliteBayRoot.Status.INVALID, SatelliteBayRoot.decode(StringTag.valueOf("bay")).status());
        assertEquals(SatelliteBayRoot.Status.INVALID, SatelliteBayRoot.decode(new ListTag()).status());
        // A future root with unknown fields, or beyond the schema-1 bound, is still future.
        assertEquals(SatelliteBayRoot.Status.FUTURE, status(edited(root -> {
            root.putInt("schema_version", 9);
            root.putString("future_field", "kept");
            root.putByteArray("future_payload", new byte[8_192]);
        })));
    }

    @Test
    void theRootBoundCoversTheLargestValidPackage() {
        CompoundTag largest = root(packageOfBytes(4_096));
        assertEquals(4_156, bytes(largest));
        assertTrue(SatelliteBayRoot.bounded(largest));
        CompoundTag deepest = root(packageOfDepth(16));
        assertTrue(BoundedNbt.fits(deepest, 4_352, 17, 272));
        CompoundTag widest = root(packageOfNodes(256));
        assertEquals(259, nodes(widest));
        assertTrue(SatelliteBayRoot.bounded(widest));
        // Past the root's own bound the codec refuses before reading any field.
        CompoundTag oversized = root(null);
        oversized.putByteArray("pad", new byte[4_352]);
        assertFalse(SatelliteBayRoot.bounded(oversized));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(oversized));
    }

    @Test
    void encodingRefusesWhatDecodingRefuses() {
        assertThrows(IllegalArgumentException.class, () -> new SatelliteBayRoot.Contents(OWNER, item(), OP));
        assertThrows(IllegalArgumentException.class, () -> new SatelliteBayRoot.Contents(OWNER, packageOfBytes(4_097), null));
        CompoundTag two = item();
        two.putByte("Count", (byte) 2);
        assertThrows(IllegalArgumentException.class, () -> new SatelliteBayRoot.Contents(OWNER, two, null));
        CompoundTag source = item();
        SatelliteBayRoot.Contents contents = new SatelliteBayRoot.Contents(OWNER, source, null);
        source.putString("id", "minecraft:stone");
        assertEquals(item(), contents.packageTag().orElseThrow(), "Contents must own a detached package copy");
    }

    @Test
    void nativeKeepabilityAcceptsLosslessRootsAndRefusesWhatTheChunkWriterWouldChange() throws IOException {
        CompoundTag quarantined = root(item());
        quarantined.putString("unknown", "kept");
        quarantined.putByteArray("big", new byte[8_192]);
        assertEquals(SatelliteBayRoot.Status.INVALID, status(quarantined));
        assertTrue(SatelliteBayRoot.nativelyKeepable(quarantined), "A serializable quarantined root is kept");

        CompoundTag deep = new CompoundTag();
        CompoundTag cursor = deep;
        for (int level = 2; level <= 510; level++) {
            CompoundTag next = new CompoundTag();
            cursor.put("n", next);
            cursor = next;
        }
        assertTrue(SatelliteBayRoot.nativelyKeepable(deep));
        cursor.put("n", new CompoundTag());
        assertFalse(SatelliteBayRoot.nativelyKeepable(deep), "Depth 511 under the chunk root is unreadable");

        CompoundTag nan = root(null);
        nan.put("x", FloatTag.valueOf(Float.intBitsToFloat(0x7fc00001)));
        assertFalse(SatelliteBayRoot.nativelyKeepable(nan), "A non-canonical NaN changes on write");

        // Only a native read keeps an element type on an empty list; removing the last element resets it.
        ListTag typedEmpty = ListTag.TYPE.load(new DataInputStream(new ByteArrayInputStream(
                new byte[] {Tag.TAG_INT, 0, 0, 0, 0})), 0, NbtAccounter.UNLIMITED);
        assertTrue(typedEmpty.isEmpty() && typedEmpty.getElementType() == Tag.TAG_INT);
        CompoundTag list = root(null);
        list.put("x", typedEmpty);
        assertFalse(SatelliteBayRoot.nativelyKeepable(list), "A typed empty list is written as TAG_End");

        CompoundTag text = root(null);
        text.putString("x", "a".repeat(65_535));
        assertTrue(SatelliteBayRoot.nativelyKeepable(text));
        text.putString("x", "a".repeat(65_536));
        assertFalse(SatelliteBayRoot.nativelyKeepable(text), "writeUTF replaces an overlong string");

        nan.put("x", DoubleTag.valueOf(Double.longBitsToDouble(0x7ff8000000000001L)));
        assertFalse(SatelliteBayRoot.nativelyKeepable(nan), "A non-canonical double NaN changes on write");
        CompoundTag overlongKey = new CompoundTag();
        overlongKey.putByte("a".repeat(65_536), (byte) 0);
        assertFalse(SatelliteBayRoot.nativelyKeepable(overlongKey), "writeUTF cannot write an overlong key");
        text.putString("x", "\u0800".repeat(21_845));
        assertTrue(SatelliteBayRoot.nativelyKeepable(text), "The string bound counts modified UTF bytes");
        text.putString("x", "\u0800".repeat(21_846));
        assertFalse(SatelliteBayRoot.nativelyKeepable(text));
        assertTrue(SatelliteBayRoot.nativelyKeepable(ByteTag.ZERO), "A wrong-typed root is kept when lossless");
    }

    @Test
    void theValidationByteBudgetIsNotATerrainSerializationLimit() throws IOException {
        CompoundTag huge = new CompoundTag();
        huge.putByteArray("x", new byte[2_097_152 - 3 - 1 - 1 - 2 - 1 - 4]);
        assertEquals(2_097_152, bytes(huge));
        assertEquals(SatelliteBayNativeCheck.Result.LOSSLESS, SatelliteBayNativeCheck.inspect(huge));
        assertTrue(SatelliteBayRoot.nativelyKeepable(huge));
        assertTerrainRoundTrip(huge);
        huge.putByteArray("x", new byte[2_097_152 - 3 - 1 - 1 - 2 - 1 - 4 + 1]);
        assertEquals(2_097_153, bytes(huge));
        assertEquals(SatelliteBayNativeCheck.Result.WORK_LIMIT, SatelliteBayNativeCheck.inspect(huge));
        assertTrue(SatelliteBayRoot.nativelyKeepable(huge), "Work exhaustion is not evidence of native data loss");
        assertTerrainRoundTrip(huge);
    }

    @Test
    void reviewOversizedQuarantinedAndFutureRootsRoundTripWithoutSaveDenial() throws IOException {
        for (int schema : new int[] {1, 2}) {
            CompoundTag oversized = new CompoundTag();
            oversized.putInt("schema_version", schema);
            oversized.putUUID("owner", new UUID(1, 2));
            oversized.putByteArray("future_payload", new byte[2_097_153]);
            assertEquals(2_097_227, bytes(oversized), "W27B-R1 review fixture");
            assertEquals(schema == 1 ? SatelliteBayRoot.Status.INVALID : SatelliteBayRoot.Status.FUTURE,
                    status(oversized));
            assertFalse(SatelliteBayRoot.bounded(oversized), "Operational limits remain unchanged");
            assertEquals(SatelliteBayNativeCheck.Result.WORK_LIMIT, SatelliteBayNativeCheck.inspect(oversized));
            assertTrue(SatelliteBayRoot.nativelyKeepable(oversized), "Keep the raw root without save denial");
            assertTerrainRoundTrip(oversized);
        }
    }

    @Test
    void theValidationNodeBudgetKeepsUnexaminedRootsWithoutSaveDenial() throws IOException {
        ListTag wide = new ListTag();
        for (int index = 0; index < SatelliteBayNativeCheck.MAX_NODES; index++) {
            wide.add(ByteTag.ZERO);
        }
        CompoundTag oversized = root(null);
        oversized.put("wide", wide);
        assertEquals(SatelliteBayNativeCheck.Result.WORK_LIMIT, SatelliteBayNativeCheck.inspect(oversized));
        assertEquals(SatelliteBayRoot.Status.INVALID, status(oversized));
        assertTrue(SatelliteBayRoot.nativelyKeepable(oversized));
        assertTerrainRoundTrip(oversized);
    }

    private static void assertTerrainRoundTrip(CompoundTag root) throws IOException {
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "advancedrocketrycommunity:rocket_satellite_bay");
        entity.putInt("x", 0);
        entity.putInt("y", 0);
        entity.putInt("z", 0);
        entity.put(SatelliteBayRoot.KEY, root);
        ListTag entities = new ListTag();
        entities.add(entity);
        CompoundTag chunk = new CompoundTag();
        chunk.put("block_entities", entities);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NbtIo.write(chunk, new DataOutputStream(output));
        // RegionFileStorage.read uses this overload, with NbtAccounter.UNLIMITED.
        CompoundTag reloaded = NbtIo.read(new DataInputStream(new ByteArrayInputStream(output.toByteArray())));
        assertEquals(chunk, reloaded);
        assertEquals(root, reloaded.getList("block_entities", Tag.TAG_COMPOUND).getCompound(0)
                .get(SatelliteBayRoot.KEY));
        assertTrue(SatelliteBayRoot.nativelyKeepable(root));
        assertTrue(SatelliteBayRoot.nativelyKeepable(reloaded.getList("block_entities", Tag.TAG_COMPOUND)
                .getCompound(0).get(SatelliteBayRoot.KEY)));
    }
}

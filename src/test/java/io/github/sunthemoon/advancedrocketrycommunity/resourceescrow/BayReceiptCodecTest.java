package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W27c1 A0: the receipt codec at its real maximum encoding, every bound from both sides, and each invariant. */
final class BayReceiptCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static BoundedNbt.Size size(Tag tag) { return BoundedNbt.measure(tag, Integer.MAX_VALUE, 64, Integer.MAX_VALUE); }
    private static String refusal(Runnable decode) {
        return assertThrows(IllegalArgumentException.class, decode::run).getMessage();
    }
    private static CompoundTag rootTag(List<CompoundTag> receipts, long issued) {
        CompoundTag root = BayReceiptCodec.encode(new BayReceiptRoot(STORE, issued, 1, List.of()));
        ListTag list = new ListTag(); list.addAll(receipts); root.put("receipts", list); return root;
    }
    private static BayReceipt with(BayReceipt base, UUID op, UUID rocket, RocketPosition bay, UUID satellite) {
        return new BayReceipt(op, base.seq(), rocket, bay, satellite, base.owner(), base.definition(), base.kind(), base.orbit(),
                base.packageSig(), base.transfer(), base.srcBefore(), base.srcAfter(), base.dstBefore(), base.dstAfter(),
                base.phase(), base.reason(), base.preexisting(), base.released(), base.registryEpoch(), base.createdTick(),
                base.updatedTick(), base.attempts());
    }

    @Test void aRealFullFieldReceiptAtItsMaximumMeasures938BytesAgainstTheOneKibibyteBound() {
        CompoundTag tag = BayReceiptCodec.receipt(maximal(1L));
        assertEquals(24, tag.size(), "all 24 frozen fields, optional ones included");
        var measured = size(tag);
        assertEquals(938, measured.bytes(), "W27A section 2's field arithmetic, now from the real encoding");
        assertTrue(measured.bytes() <= BayReceiptCodec.RECEIPT_BYTES);
        assertEquals(25, measured.nodes()); assertTrue(measured.nodes() <= BayReceiptCodec.RECEIPT_NODES);
        assertEquals(maximal(1L), BayReceiptCodec.receipt(tag));
        assertEquals(tag, BayReceiptCodec.receipt(BayReceiptCodec.receipt(tag)), "decode and encode never normalize");
    }

    @Test void theFullRootOf4096MaximalReceiptsFitsTheSectionAndRootWithItsListFraming() {
        BayReceiptRoot full = root(maximalList(BayReceiptRoot.RECEIPTS));
        CompoundTag tag = BayReceiptCodec.encode(full);
        var root = size(tag); var section = size(tag.get("receipts"));
        // Each element loses the 3-byte named header inside the list; the list adds a 5-byte type and length.
        assertEquals(3L + 5L + 4_096L * (938 - 3), section.bytes());
        assertTrue(section.bytes() <= BayReceiptCodec.SECTION_BYTES && root.bytes() <= BayReceiptCodec.ROOT_BYTES);
        assertEquals(section.bytes() + 153, root.bytes(), "root framing: header, end, five scalar fields and the list key");
        assertEquals(1 + 5 + 1 + 4_096 * 25, root.nodes()); assertTrue(root.nodes() <= BayReceiptCodec.ROOT_NODES);
        assertEquals(full, BayReceiptCodec.decode(tag));
        String digest = BayReceiptCodec.digest(tag);
        assertTrue(root.bytes() > 262_144, "beyond W11b1's canonical limit, so the streaming digest is needed");
        assertEquals(digest, BayReceiptCodec.digest(BayReceiptCodec.encode(BayReceiptCodec.decode(tag))));
        var changed = new ArrayList<>(full.receipts()); changed.set(9, changed.get(9).withPhase(BayReceipt.Phase.REPAIR, "BAY_CONFLICT", 7));
        assertNotEquals(digest, BayReceiptCodec.digest(BayReceiptCodec.encode(root(changed))));
    }

    @Test void receiptBytesDepthAndNodesJustInsideAndJustOutside() {
        CompoundTag base = BayReceiptCodec.receipt(maximal(1L));
        long padding = BayReceiptCodec.RECEIPT_BYTES - size(base).bytes() - 10; // type, "pad" and the array length
        for (long extra = 0; extra <= 1; extra++) {
            CompoundTag padded = base.copy(); padded.putByteArray("pad", new byte[(int) (padding + extra)]);
            assertEquals(BayReceiptCodec.RECEIPT_BYTES + extra, size(padded).bytes());
            assertEquals(extra == 0 ? "Unknown escrow field" : "Bay receipt envelope exceeded", refusal(() -> BayReceiptCodec.receipt(padded)));
        }
        CompoundTag depth3 = base.copy(); CompoundTag inner = new CompoundTag(); inner.putInt("v", 1); depth3.put("pad", inner);
        assertEquals("Unknown escrow field", refusal(() -> BayReceiptCodec.receipt(depth3)));
        CompoundTag depth4 = base.copy(); CompoundTag outer = new CompoundTag(); outer.put("deeper", inner.copy()); depth4.put("pad", outer);
        assertEquals("Bay receipt envelope exceeded", refusal(() -> BayReceiptCodec.receipt(depth4)));
        for (int extra = 0; extra <= 1; extra++) {
            CompoundTag listed = base.copy(); ListTag bytes = new ListTag();
            int count = BayReceiptCodec.RECEIPT_NODES - size(base).nodes() - 1 + extra;
            for (int i = 0; i < count; i++) { bytes.add(ByteTag.valueOf((byte) i)); }
            listed.put("pad", bytes);
            assertEquals(BayReceiptCodec.RECEIPT_NODES + extra, size(listed).nodes());
            assertEquals(extra == 0 ? "Unknown escrow field" : "Bay receipt envelope exceeded", refusal(() -> BayReceiptCodec.receipt(listed)));
        }
    }

    @Test void sectionBytesJustInsideAndJustOutside() {
        for (int extra = 0; extra <= 1; extra++) {
            var elements = new ArrayList<CompoundTag>();
            for (int i = 0; i < 4_108; i++) { CompoundTag big = new CompoundTag(); big.putByteArray("x", new byte[1_012]); elements.add(big); }
            CompoundTag last = new CompoundTag(); last.putByteArray("x", new byte[19 + extra]); elements.add(last);
            assertEquals(BayReceiptCodec.RECEIPT_BYTES, size(elements.get(0)).bytes(), "every element stays inside its own bound");
            CompoundTag tag = rootTag(elements, 1);
            assertEquals(BayReceiptCodec.SECTION_BYTES + extra, size(tag.get("receipts")).bytes());
            assertTrue(size(tag).bytes() <= BayReceiptCodec.ROOT_BYTES, "only the section bound is under test");
            assertEquals(extra == 0 ? "Invalid escrow list" : "Bay receipt section envelope exceeded", refusal(() -> BayReceiptCodec.decode(tag)));
        }
    }

    @Test void rootBytesDepthNodesAndReceiptCountJustInsideAndJustOutside() {
        CompoundTag empty = rootTag(List.of(), 0);
        long padding = BayReceiptCodec.ROOT_BYTES - size(empty).bytes() - 10;
        for (int extra = 0; extra <= 1; extra++) {
            CompoundTag padded = empty.copy(); padded.putByteArray("pad", new byte[(int) padding + extra]);
            assertEquals(BayReceiptCodec.ROOT_BYTES + extra, size(padded).bytes());
            assertEquals(extra == 0 ? "Unknown escrow field" : "Bay receipt root envelope exceeded", refusal(() -> BayReceiptCodec.decode(padded)));
        }
        CompoundTag level3 = new CompoundTag(); level3.put("v", IntTag.valueOf(1));
        CompoundTag level2 = new CompoundTag(); level2.put("c", level3);
        CompoundTag depth4 = empty.copy(); depth4.put("pad", level2);
        assertEquals("Unknown escrow field", refusal(() -> BayReceiptCodec.decode(depth4)));
        CompoundTag level1 = new CompoundTag(); level1.put("c", level2.copy());
        CompoundTag depth5 = empty.copy(); depth5.put("pad", level1);
        assertEquals("Bay receipt root envelope exceeded", refusal(() -> BayReceiptCodec.decode(depth5)));
        for (int extra = 0; extra <= 1; extra++) {
            CompoundTag listed = empty.copy(); ListTag bytes = new ListTag();
            int count = BayReceiptCodec.ROOT_NODES - size(empty).nodes() - 1 + extra;
            for (int i = 0; i < count; i++) { bytes.add(ByteTag.ZERO); }
            listed.put("pad", bytes);
            assertEquals(BayReceiptCodec.ROOT_NODES + extra, size(listed).nodes());
            assertEquals(extra == 0 ? "Unknown escrow field" : "Bay receipt root envelope exceeded", refusal(() -> BayReceiptCodec.decode(listed)));
        }
        var encoded = new ArrayList<CompoundTag>();
        for (long i = 1; i <= BayReceiptRoot.RECEIPTS + 1; i++) { encoded.add(BayReceiptCodec.receipt(receipt(i, id(9), id(10_000 + i), BayReceipt.Phase.REJECTED))); }
        assertEquals(BayReceiptRoot.RECEIPTS, BayReceiptCodec.decode(rootTag(encoded.subList(0, BayReceiptRoot.RECEIPTS), 5_000)).receipts().size());
        assertEquals("Invalid escrow list", refusal(() -> BayReceiptCodec.decode(rootTag(encoded, 5_000))));
        var tooMany = new ArrayList<BayReceipt>();
        for (long i = 1; i <= BayReceiptRoot.RECEIPTS + 1; i++) { tooMany.add(receipt(i, id(9), id(10_000 + i), BayReceipt.Phase.REJECTED)); }
        assertEquals("Too many bay receipts", refusal(() -> root(tooMany)));
    }

    @Test void eachCodecInvariantRefusesWithoutNormalizing() {
        BayReceipt a = receipt(1, id(9), id(20), BayReceipt.Phase.INTENT);
        BayReceipt b = receipt(2, id(9), id(21), BayReceipt.Phase.REJECTED);
        var cases = List.of(
                List.of(a, with(b, a.op(), b.rocket(), b.bay(), b.satellite())),
                List.of(a, with(b, b.op(), a.rocket(), a.bay(), b.satellite())),
                List.of(a, with(b, b.op(), b.rocket(), b.bay(), a.satellite())));
        var messages = List.of("Duplicate bay receipt operation", "Duplicate bay receipt key", "Second bay receipt for one satellite");
        for (int i = 0; i < cases.size(); i++) {
            var receipts = cases.get(i);
            assertEquals(messages.get(i), refusal(() -> BayReceiptCodec.encode(root(receipts))));
            var tags = receipts.stream().map(BayReceiptCodec::receipt).toList();
            assertEquals(messages.get(i), refusal(() -> BayReceiptCodec.decode(rootTag(tags, 2))));
        }
        // The same rocket in another bay, or another rocket in the same bay position, is a different key.
        BayReceiptCodec.decode(BayReceiptCodec.encode(root(List.of(a, with(b, b.op(), a.rocket(), b.bay(), b.satellite())))));
        BayReceiptCodec.decode(BayReceiptCodec.encode(root(List.of(a, with(b, b.op(), b.rocket(), a.bay(), b.satellite())))));
        assertEquals("Bay receipt sequence was never issued", refusal(() -> BayReceiptCodec.decode(rootTag(List.of(BayReceiptCodec.receipt(b)), 1))));
        CompoundTag registered = BayReceiptCodec.receipt(receipt(3, id(9), id(22), BayReceipt.Phase.REGISTERED));
        for (String hash : List.of("src_after", "dst_after")) {
            CompoundTag missing = registered.copy(); missing.remove(hash);
            assertEquals("A REGISTERED bay receipt requires all four hashes", refusal(() -> BayReceiptCodec.receipt(missing)));
        }
        CompoundTag intent = BayReceiptCodec.receipt(a);
        assertFalse(intent.contains("src_after") || intent.contains("dst_after"), "absent optional hashes are not written");
        var mutations = new ArrayList<Consumer<CompoundTag>>(List.of(
                tag -> tag.putInt("schema_version", 2), tag -> tag.putString("op", a.op().toString()),
                tag -> tag.putIntArray("bay", new int[] {1, 2}), tag -> tag.putByteArray("package_sig", new byte[31]),
                tag -> tag.putByte("preexisting", (byte) 2), tag -> tag.putInt("released", 0),
                tag -> tag.putString("kind", "DATA"), tag -> tag.putString("kind", "data_v2"),
                tag -> tag.putString("definition", "Advanced:Bad"), tag -> tag.putString("orbit", id("minecraft", 129)),
                tag -> tag.putString("reason", "R".repeat(41)), tag -> tag.putString("reason", "interrupted"),
                tag -> tag.putInt("attempts", 1_001), tag -> tag.putInt("attempts", -1), tag -> tag.putLong("seq", 0),
                tag -> tag.putString("phase", "RETIRED"), tag -> tag.putString("extra", "x"), tag -> tag.remove("orbit"),
                tag -> tag.putInt("created_tick", 1), tag -> tag.putLong("registry_epoch", -1),
                tag -> tag.putByteArray("src_before", new byte[33])));
        for (var mutation : mutations) {
            CompoundTag broken = intent.copy(); mutation.accept(broken);
            assertThrows(IllegalArgumentException.class, () -> BayReceiptCodec.receipt(broken), broken::toString);
        }
        CompoundTag good = BayReceiptCodec.encode(root(List.of(a)));
        var rootMutations = List.<Consumer<CompoundTag>>of(tag -> tag.putInt("schema_version", 2),
                tag -> tag.putString("format_epoch", "v1.8.0-resource-escrow"), tag -> tag.putString("store_id", "x"),
                tag -> tag.putLong("issued_through", -1), tag -> tag.putString("extra", "x"), tag -> tag.remove("writer_generation"),
                tag -> tag.putInt("issued_through", 1));
        for (var mutation : rootMutations) {
            CompoundTag broken = good.copy(); mutation.accept(broken);
            assertThrows(IllegalArgumentException.class, () -> BayReceiptCodec.decode(broken), broken::toString);
        }
    }

    @Test void theManagedTypeIsSchemaOneWithItsOwnEpochAndNoLegacySchema() {
        var type = ManagedSavedDataType.BAY_RECEIPTS;
        assertEquals("data/advancedrocketrycommunity_bay_receipts.dat", "data/" + type.fileName());
        assertEquals(1, type.currentSchemaVersion()); assertEquals("v1.8.0-bay-receipts", type.formatEpoch());
        assertFalse(type.hasLegacySchema()); assertFalse(type.supportsSchema(0)); assertFalse(type.supportsSchema(2));
        assertEquals(BayReceiptCodec.ROOT_BYTES, type.maxUncompressedBytes()); assertEquals("v1.8.0", type.introducedIn());
    }
}

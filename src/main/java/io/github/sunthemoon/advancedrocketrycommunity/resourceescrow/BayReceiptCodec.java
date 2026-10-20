package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.ArrayList;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The schema-1 {@code BAY_RECEIPTS} root (W27A-DESIGN section 2). Bounds are checked before any copy or recursive
 * decode; unknown fields, wrong types and broken invariants refuse. There is no legacy reader and no normalization.
 */
public final class BayReceiptCodec {
    public static final int SCHEMA = 1;
    public static final String EPOCH = "v1.8.0-bay-receipts";
    /** One receipt, measured as a named compound including its header and end. */
    public static final int RECEIPT_BYTES = 1_024, RECEIPT_DEPTH = 3, RECEIPT_NODES = 40;
    /** The receipts list, measured as a named root, and the whole root with its 1 KiB of framing. */
    public static final int SECTION_BYTES = 4_194_304, ROOT_BYTES = 4_195_328, ROOT_DEPTH = 4, ROOT_NODES = 131_072;
    private static final String[] ROOT_FIELDS = {"schema_version", "format_epoch", "store_id", "issued_through",
            "writer_generation", "receipts"};
    private static final String[] RECEIPT_FIELDS = {"schema_version", "op", "seq", "rocket", "bay", "satellite", "owner",
            "definition", "kind", "orbit", "package_sig", "transfer", "src_before", "src_after", "dst_before", "dst_after",
            "phase", "reason", "preexisting", "released", "registry_epoch", "created_tick", "updated_tick", "attempts"};
    private BayReceiptCodec() { }

    public static CompoundTag encode(BayReceiptRoot root) {
        root.validate();
        CompoundTag tag = new CompoundTag(); tag.putInt("schema_version", SCHEMA); tag.putString("format_epoch", EPOCH);
        tag.putUUID("store_id", root.storeId()); tag.putLong("issued_through", root.issuedThrough());
        tag.putLong("writer_generation", root.writerGeneration());
        ListTag receipts = new ListTag(); root.receipts().forEach(receipt -> receipts.add(receipt(receipt)));
        tag.put("receipts", receipts);
        bounds(tag); return tag;
    }

    public static BayReceiptRoot decode(CompoundTag tag) {
        bounds(tag);
        EscrowTags.fields(tag, ROOT_FIELDS);
        EscrowTags.require(EscrowTags.integer(tag, "schema_version") == SCHEMA
                && EPOCH.equals(EscrowTags.string(tag, "format_epoch")), "Unsupported bay receipt root");
        var receipts = new ArrayList<BayReceipt>();
        for (Tag raw : EscrowTags.list(tag, "receipts", BayReceiptRoot.RECEIPTS)) { receipts.add(receipt((CompoundTag) raw)); }
        return new BayReceiptRoot(EscrowTags.uuid(tag, "store_id"), EscrowTags.number(tag, "issued_through"),
                EscrowTags.number(tag, "writer_generation"), receipts).validate();
    }

    /** The root, section and per-receipt envelopes, without copying or decoding. */
    static void bounds(CompoundTag root) {
        EscrowTags.require(BoundedNbt.fits(root, ROOT_BYTES, ROOT_DEPTH, ROOT_NODES), "Bay receipt root envelope exceeded");
        if (root.get("receipts") instanceof ListTag receipts) {
            EscrowTags.require(BoundedNbt.fits(receipts, SECTION_BYTES, ROOT_DEPTH - 1, ROOT_NODES),
                    "Bay receipt section envelope exceeded");
            for (Tag receipt : receipts) { receiptBounds(receipt); }
        }
    }

    static void receiptBounds(Tag receipt) {
        EscrowTags.require(BoundedNbt.fits(receipt, RECEIPT_BYTES, RECEIPT_DEPTH, RECEIPT_NODES), "Bay receipt envelope exceeded");
    }

    /** The shared streaming canonical digest of an encoded root (N7), with the receipt root's depth of 4. */
    public static String digest(CompoundTag root) {
        bounds(root); return CanonicalNbtDigest.of(root, ROOT_BYTES, ROOT_DEPTH);
    }

    public static CompoundTag receipt(BayReceipt receipt) {
        CompoundTag tag = new CompoundTag(); tag.putInt("schema_version", SCHEMA);
        tag.putUUID("op", receipt.op()); tag.putLong("seq", receipt.seq());
        tag.putUUID("rocket", receipt.rocket());
        tag.putIntArray("bay", new int[] {receipt.bay().x(), receipt.bay().y(), receipt.bay().z()});
        tag.putUUID("satellite", receipt.satellite()); tag.putUUID("owner", receipt.owner());
        tag.putString("definition", receipt.definition()); tag.putString("kind", receipt.kind().id());
        tag.putString("orbit", receipt.orbit()); tag.putByteArray("package_sig", hash(receipt.packageSig()));
        tag.putUUID("transfer", receipt.transfer());
        tag.putByteArray("src_before", hash(receipt.srcBefore()));
        if (receipt.srcAfter() != null) { tag.putByteArray("src_after", hash(receipt.srcAfter())); }
        tag.putByteArray("dst_before", hash(receipt.dstBefore()));
        if (receipt.dstAfter() != null) { tag.putByteArray("dst_after", hash(receipt.dstAfter())); }
        tag.putString("phase", receipt.phase().name()); tag.putString("reason", receipt.reason());
        tag.putByte("preexisting", (byte) (receipt.preexisting() ? 1 : 0));
        tag.putByte("released", (byte) (receipt.released() ? 1 : 0));
        tag.putLong("registry_epoch", receipt.registryEpoch()); tag.putLong("created_tick", receipt.createdTick());
        tag.putLong("updated_tick", receipt.updatedTick()); tag.putInt("attempts", receipt.attempts());
        receiptBounds(tag); return tag;
    }

    public static BayReceipt receipt(CompoundTag tag) {
        receiptBounds(tag);
        EscrowTags.fields(tag, RECEIPT_FIELDS);
        EscrowTags.require(EscrowTags.integer(tag, "schema_version") == SCHEMA, "Unsupported bay receipt schema");
        EscrowTags.type(tag, "bay", Tag.TAG_INT_ARRAY);
        int[] bay = tag.getIntArray("bay");
        EscrowTags.require(bay.length == 3, "Bay receipt position needs three coordinates");
        String kind = EscrowTags.string(tag, "kind");
        return new BayReceipt(EscrowTags.uuid(tag, "op"), EscrowTags.number(tag, "seq"), EscrowTags.uuid(tag, "rocket"),
                new RocketPosition(bay[0], bay[1], bay[2]), EscrowTags.uuid(tag, "satellite"), EscrowTags.uuid(tag, "owner"),
                EscrowTags.string(tag, "definition"),
                SatelliteKind.parse(kind).orElseThrow(() -> new IllegalArgumentException("Unknown bay receipt kind")),
                EscrowTags.string(tag, "orbit"), hash(tag, "package_sig"), EscrowTags.uuid(tag, "transfer"),
                hash(tag, "src_before"), tag.contains("src_after") ? hash(tag, "src_after") : null,
                hash(tag, "dst_before"), tag.contains("dst_after") ? hash(tag, "dst_after") : null,
                EscrowTags.enumeration(BayReceipt.Phase.class, tag, "phase"), EscrowTags.string(tag, "reason"),
                flag(tag, "preexisting"), flag(tag, "released"), EscrowTags.number(tag, "registry_epoch"),
                EscrowTags.number(tag, "created_tick"), EscrowTags.number(tag, "updated_tick"), EscrowTags.integer(tag, "attempts"));
    }

    private static byte[] hash(String hex) { return HexFormat.of().parseHex(hex); }
    private static String hash(CompoundTag tag, String key) {
        EscrowTags.type(tag, key, Tag.TAG_BYTE_ARRAY);
        byte[] bytes = tag.getByteArray(key);
        EscrowTags.require(bytes.length == 32, "Bay receipt hash needs 32 bytes: " + key);
        return HexFormat.of().formatHex(bytes);
    }
    private static boolean flag(CompoundTag tag, String key) {
        EscrowTags.type(tag, key, Tag.TAG_BYTE);
        byte value = tag.getByte(key);
        EscrowTags.require(value == 0 || value == 1, "Bay receipt flag must be 0 or 1: " + key);
        return value == 1;
    }
}

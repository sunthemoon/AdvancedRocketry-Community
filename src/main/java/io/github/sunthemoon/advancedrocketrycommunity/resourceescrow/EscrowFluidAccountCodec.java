package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CanonicalNbtDigest;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Baseline;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Frozen;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.FrozenEntry;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Key;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Knowledge;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Result;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Row;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Scope;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.Status;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * W357 section 4.1: account row schema 1 in its closed short-key form. Saved rows use {@link #SAVED}; the larger
 * measurement envelope is not a persistence allowance. Row encode/decode refusals are bounded {@link Result}s.
 *
 * <pre>
 * v  int        schema, 1
 * op int[4]     operation UUID
 * b  compound   baseline:  s byte scope (0 fuel, 1 cargo); e list of entries
 *                 entry:   i string fluid ID; d byte[32] tag digest; s, t long initial source and destination amounts;
 *                          x, y long planned source and destination deltas
 * f  compound   frozen settlement, optional: k byte knowledge (1 KNOWN, 2 UNKNOWN_BASELINE, 3 UNKNOWN_ENDPOINT);
 *               g int[4] group owner; m int[4n] member UUIDs, only in the group owner's row; e list of entries
 *                 entry:   i, d as above; m byte mask (1 source observed, 2 destination observed, 4 adjustment known);
 *                          s, t long observed amounts and a long signed adjustment, each present exactly when its mask
 *                          bit is set; o int[4] adjustment owner
 * </pre>
 */
public final class EscrowFluidAccountCodec {
    /** The bounds of one row. {@code members} bounds the member list of a group owner's row. */
    public record Limits(int bytes, int depth, int nodes, int members) { }
    /** The most the design allows: the 4,096 bytes first proposed, depth 8 and 64 members (section 5). */
    public static final Limits MAXIMUM = new Limits(4_096, 8, 65, EscrowFluidAccount.MAX_MEMBERS);
    /** R443: saved rows, including frozen members, never exceed these bounds. */
    public static final Limits SAVED = new Limits(2_048, 5, 65, 14);

    /** Ordered row-bearing members only, committing their complete frozen choices. */
    public static String commitment(List<Row> rows) {
        EscrowTags.require(!rows.isEmpty() && rows.stream().allMatch(row -> row.frozen() != null), "Empty or unfrozen commitment");
        var encoded = encodeRows(rows, SAVED); EscrowTags.require(encoded.ok(), "Invalid account commitment");
        var pairs = new ListTag();
        for (var row : rows) {
            var pair = new CompoundTag(); pair.putUUID("op", row.operation());
            pair.putByteArray("digest", HexFormat.of().parseHex(digest(row, SAVED).value())); pairs.add(pair);
        }
        var root = new CompoundTag(); root.put("rows", pairs);
        return CanonicalNbtDigest.of(root, EscrowLimits.ROOT_BYTES, 8);
    }
    /** At most 128 rows: 64 live operations and 64 permanent receipts, an O3 overlap counted once. */
    public static final int MAX_ROWS = 128;
    private static final int MASK_SOURCE = 1, MASK_DESTINATION = 2, MASK_ADJUSTMENT = 4;

    private EscrowFluidAccountCodec() { }

    /**
     * The key of a native fluid stack: its registry ID and the digest of its amount-free envelope. The amount is not
     * part of the key, and a stack without a Tag differs from one with an empty Tag. This is registry independent: it
     * neither looks the Fluid up nor permits materializing it.
     */
    public static Result<Key> key(CompoundTag nativeFluid) {
        if (nativeFluid == null || !BoundedNbt.fits(nativeFluid, EscrowLimits.FLUID_BYTES, 12, 384)) { return Result.refuse(Status.TOO_LARGE); }
        if (!Set.of("FluidName", "Amount", "Tag").containsAll(nativeFluid.getAllKeys())) { return Result.refuse(Status.UNKNOWN_FIELD); }
        if (!nativeFluid.contains("FluidName", Tag.TAG_STRING)
                || nativeFluid.contains("Tag") && !nativeFluid.contains("Tag", Tag.TAG_COMPOUND)) { return Result.refuse(Status.INVALID_FIELD); }
        String fluid = nativeFluid.getString("FluidName");
        if (!canonicalId(fluid)) { return Result.refuse(Status.INVALID_KEY); }
        return Key.derive(fluid, nativeFluid.contains("Tag")
                ? CanonicalNbtDigest.of(nativeFluid.getCompound("Tag"), EscrowLimits.FLUID_BYTES, 12) : null);
    }

    public static Result<CompoundTag> encode(Row row, Limits limits) {
        Status status = row.status();
        if (status != Status.OK) { return Result.refuse(status); }
        if (row.frozen() != null && row.frozen().members().size() > limits.members()) { return Result.refuse(Status.CARDINALITY); }
        var tag = new CompoundTag(); tag.putInt("v", EscrowFluidAccount.SCHEMA); tag.putUUID("op", row.operation());
        tag.put("b", baseline(row.baseline()));
        if (row.frozen() != null) { tag.put("f", frozen(row.frozen())); }
        return BoundedNbt.fits(tag, limits.bytes(), limits.depth(), limits.nodes()) ? Result.ok(tag) : Result.refuse(Status.TOO_LARGE);
    }

    public static Result<Row> decode(CompoundTag tag, Limits limits) {
        if (tag == null || !BoundedNbt.fits(tag, limits.bytes(), limits.depth(), limits.nodes())) { return Result.refuse(Status.TOO_LARGE); }
        if (!closed(tag, "v", "op", "b", "f")) { return Result.refuse(Status.UNKNOWN_FIELD); }
        if (!tag.contains("v", Tag.TAG_INT) || !uuid(tag, "op") || !tag.contains("b", Tag.TAG_COMPOUND)
                || tag.contains("f") && !tag.contains("f", Tag.TAG_COMPOUND)) { return Result.refuse(Status.INVALID_FIELD); }
        if (tag.getInt("v") != EscrowFluidAccount.SCHEMA) { return Result.refuse(Status.UNSUPPORTED_SCHEMA); }
        var baseline = baseline(tag.getCompound("b"));
        if (!baseline.ok()) { return Result.refuse(baseline.status()); }
        Frozen frozen = null;
        if (tag.contains("f")) {
            var decoded = frozen(tag.getCompound("f"), baseline.value().scope(), limits);
            if (!decoded.ok()) { return Result.refuse(decoded.status()); }
            frozen = decoded.value();
        }
        var row = new Row(tag.getUUID("op"), baseline.value(), frozen);
        if (row.status() != Status.OK) { return Result.refuse(row.status()); }
        // One saved form per row: a list or array that could be written differently is refused.
        var again = encode(row, limits);
        return !again.ok() ? Result.refuse(again.status()) : again.value().equals(tag) ? Result.ok(row) : Result.refuse(Status.NONCANONICAL);
    }

    /** The {@code fluid_accounts} list: at most 128 rows, each operation at most once. */
    public static Result<ListTag> encodeRows(List<Row> rows, Limits limits) {
        if (rows.size() > MAX_ROWS) { return Result.refuse(Status.CARDINALITY); }
        var list = new ListTag(); var ids = new HashSet<UUID>();
        for (Row row : rows) {
            if (!ids.add(row.operation())) { return Result.refuse(Status.DUPLICATE_ROW); }
            var encoded = encode(row, limits);
            if (!encoded.ok()) { return Result.refuse(encoded.status()); }
            list.add(encoded.value());
        }
        return Result.ok(list);
    }

    public static Result<List<Row>> decodeRows(ListTag list, Limits limits) {
        if (list == null || list.size() > MAX_ROWS) { return Result.refuse(Status.CARDINALITY); }
        if (list.getElementType() != (list.isEmpty() ? Tag.TAG_END : Tag.TAG_COMPOUND)) { return Result.refuse(Status.INVALID_FIELD); }
        var rows = new ArrayList<Row>(); var ids = new HashSet<UUID>();
        for (Tag raw : list) {
            var row = decode((CompoundTag) raw, limits);
            if (!row.ok()) { return Result.refuse(row.status()); }
            if (!ids.add(row.value().operation())) { return Result.refuse(Status.DUPLICATE_ROW); }
            rows.add(row.value());
        }
        return Result.ok(List.copyOf(rows));
    }

    /** The canonical digest of a complete row. A receipt's {@code accounting_digest} selects its final row with it. */
    public static Result<String> digest(Row row, Limits limits) {
        var encoded = encode(row, limits);
        return encoded.ok() ? Result.ok(CanonicalNbtDigest.of(encoded.value(), limits.bytes(), limits.depth())) : Result.refuse(encoded.status());
    }

    /** The canonical digest of the baseline subrecord alone: an operation's {@code accounting_baseline}. */
    public static Result<String> baselineDigest(Baseline baseline) {
        Status status = baseline.status();
        return status != Status.OK ? Result.refuse(status)
                : Result.ok(CanonicalNbtDigest.of(baseline(baseline), MAXIMUM.bytes(), MAXIMUM.depth()));
    }

    private static CompoundTag baseline(Baseline baseline) {
        var tag = new CompoundTag(); tag.putByte("s", (byte) baseline.scope().ordinal());
        var entries = new ListTag();
        for (Entry entry : baseline.entries()) {
            var item = entryKey(entry.key()); item.putLong("s", entry.source()); item.putLong("t", entry.destination());
            item.putLong("x", entry.sourceDelta()); item.putLong("y", entry.destinationDelta()); entries.add(item);
        }
        tag.put("e", entries); return tag;
    }

    private static Result<Baseline> baseline(CompoundTag tag) {
        if (!closed(tag, "s", "e")) { return Result.refuse(Status.UNKNOWN_FIELD); }
        if (!tag.contains("s", Tag.TAG_BYTE) || !tag.contains("e", Tag.TAG_LIST)) { return Result.refuse(Status.INVALID_FIELD); }
        byte scope = tag.getByte("s");
        if (scope < 0 || scope >= Scope.values().length) { return Result.refuse(Status.INVALID_FIELD); }
        var list = (ListTag) tag.get("e");
        if (list.isEmpty() || list.size() > Scope.values()[scope].baselineKeys()) { return Result.refuse(Status.CARDINALITY); }
        if (list.getElementType() != Tag.TAG_COMPOUND) { return Result.refuse(Status.INVALID_FIELD); }
        var entries = new ArrayList<Entry>();
        for (Tag raw : list) {
            var item = (CompoundTag) raw;
            if (!closed(item, "i", "d", "s", "t", "x", "y")) { return Result.refuse(Status.UNKNOWN_FIELD); }
            var key = entryKey(item);
            if (!key.ok()) { return Result.refuse(key.status()); }
            for (String field : List.of("s", "t", "x", "y")) {
                if (!item.contains(field, Tag.TAG_LONG)) { return Result.refuse(Status.INVALID_FIELD); }
            }
            entries.add(new Entry(key.value(), item.getLong("s"), item.getLong("t"), item.getLong("x"), item.getLong("y")));
        }
        return Result.ok(new Baseline(Scope.values()[scope], entries));
    }

    private static CompoundTag frozen(Frozen frozen) {
        var tag = new CompoundTag(); tag.putByte("k", (byte) frozen.knowledge().ordinal()); tag.putUUID("g", frozen.groupOwner());
        if (!frozen.members().isEmpty()) {
            int[] members = new int[frozen.members().size() * 4];
            for (int index = 0; index < frozen.members().size(); index++) {
                System.arraycopy(UUIDUtil.uuidToIntArray(frozen.members().get(index)), 0, members, index * 4, 4);
            }
            tag.putIntArray("m", members);
        }
        var entries = new ListTag();
        for (FrozenEntry entry : frozen.entries()) {
            var item = entryKey(entry.key());
            item.putByte("m", (byte) ((entry.source().isPresent() ? MASK_SOURCE : 0) | (entry.destination().isPresent() ? MASK_DESTINATION : 0)
                    | (entry.adjustment().isPresent() ? MASK_ADJUSTMENT : 0)));
            entry.source().ifPresent(value -> item.putLong("s", value)); entry.destination().ifPresent(value -> item.putLong("t", value));
            entry.adjustment().ifPresent(value -> item.putLong("a", value)); item.putUUID("o", entry.owner()); entries.add(item);
        }
        tag.put("e", entries); return tag;
    }

    private static Result<Frozen> frozen(CompoundTag tag, Scope scope, Limits limits) {
        if (!closed(tag, "k", "g", "m", "e")) { return Result.refuse(Status.UNKNOWN_FIELD); }
        if (!tag.contains("k", Tag.TAG_BYTE) || !uuid(tag, "g") || !tag.contains("e", Tag.TAG_LIST)
                || tag.contains("m") && !tag.contains("m", Tag.TAG_INT_ARRAY)) { return Result.refuse(Status.INVALID_FIELD); }
        byte code = tag.getByte("k");
        if (code != Knowledge.KNOWN.ordinal() && code != Knowledge.UNKNOWN_BASELINE.ordinal() && code != Knowledge.UNKNOWN_ENDPOINT.ordinal()) {
            return Result.refuse(Status.INVALID_KNOWLEDGE);
        }
        var members = new ArrayList<UUID>();
        if (tag.contains("m")) {
            int[] raw = tag.getIntArray("m");
            if (raw.length == 0 || raw.length % 4 != 0) { return Result.refuse(Status.INVALID_MEMBERS); }
            if (raw.length / 4 > limits.members()) { return Result.refuse(Status.CARDINALITY); }
            for (int index = 0; index < raw.length; index += 4) {
                members.add(UUIDUtil.uuidFromIntArray(new int[] {raw[index], raw[index + 1], raw[index + 2], raw[index + 3]}));
            }
        }
        var list = (ListTag) tag.get("e");
        if (list.isEmpty() || list.size() > scope.unionKeys()) { return Result.refuse(Status.CARDINALITY); }
        if (list.getElementType() != Tag.TAG_COMPOUND) { return Result.refuse(Status.INVALID_FIELD); }
        var entries = new ArrayList<FrozenEntry>();
        for (Tag raw : list) {
            var item = (CompoundTag) raw;
            if (!closed(item, "i", "d", "m", "s", "t", "a", "o")) { return Result.refuse(Status.UNKNOWN_FIELD); }
            var key = entryKey(item);
            if (!key.ok()) { return Result.refuse(key.status()); }
            if (!item.contains("m", Tag.TAG_BYTE) || !uuid(item, "o")) { return Result.refuse(Status.INVALID_FIELD); }
            byte mask = item.getByte("m");
            if (mask < 0 || mask > (MASK_SOURCE | MASK_DESTINATION | MASK_ADJUSTMENT)) { return Result.refuse(Status.INVALID_MASK); }
            var values = new OptionalLong[3]; String[] fields = {"s", "t", "a"};
            for (int bit = 0; bit < 3; bit++) {
                boolean expected = (mask & 1 << bit) != 0;
                // A field is present exactly when its mask bit is set; an absent amount has no number at all.
                if (item.contains(fields[bit]) != expected) { return Result.refuse(Status.INVALID_MASK); }
                if (expected && !item.contains(fields[bit], Tag.TAG_LONG)) { return Result.refuse(Status.INVALID_FIELD); }
                values[bit] = expected ? OptionalLong.of(item.getLong(fields[bit])) : OptionalLong.empty();
            }
            entries.add(new FrozenEntry(key.value(), values[0], values[1], values[2], item.getUUID("o")));
        }
        return Result.ok(new Frozen(Knowledge.values()[code], tag.getUUID("g"), members, entries));
    }

    private static CompoundTag entryKey(Key key) {
        var item = new CompoundTag(); item.putString("i", key.fluid());
        item.putByteArray("d", HexFormat.of().parseHex(key.tagDigest())); return item;
    }

    private static Result<Key> entryKey(CompoundTag item) {
        if (!item.contains("i", Tag.TAG_STRING) || !item.contains("d", Tag.TAG_BYTE_ARRAY)) { return Result.refuse(Status.INVALID_FIELD); }
        if (item.getByteArray("d").length != 32) { return Result.refuse(Status.MALFORMED_DIGEST); }
        var key = new Key(item.getString("i"), HexFormat.of().formatHex(item.getByteArray("d")));
        return !canonicalId(key.fluid()) ? Result.refuse(Status.INVALID_KEY) : key.status() == Status.OK ? Result.ok(key) : Result.refuse(key.status());
    }

    /** The same rule as {@code EscrowTags.id}: at most 128 printable ASCII characters in canonical registry form. */
    private static boolean canonicalId(String value) {
        if (value.length() > 128 || !value.chars().allMatch(character -> character > 32 && character < 127)) { return false; }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && id.toString().equals(value);
    }

    private static boolean closed(CompoundTag tag, String... fields) { return Set.of(fields).containsAll(tag.getAllKeys()); }
    private static boolean uuid(CompoundTag tag, String key) { return tag.contains(key, Tag.TAG_INT_ARRAY) && tag.hasUUID(key); }
}

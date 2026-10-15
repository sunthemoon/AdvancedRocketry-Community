package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLimits;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * The loader's schema-1 {@code resource_escrow} section, in the layout the rocket and the test endpoints use: at most
 * one SOURCE marker, in 256 bytes, depth 8 and 128 nodes. Absence of a ledger is canonical only before the first fill.
 */
public record LoaderEscrowSection(UUID ledger, long epoch, List<EscrowPrefix.Marker> markers) {
    public static final String KEY = "resource_escrow";
    public static final int MAX_BYTES = EscrowLimits.BE_MARKER_BYTES;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 128;
    public static final int MAX_MARKERS = 1;
    /** The loader is only ever the source of a propulsion fill; there is no propulsion unloader. */
    public static final String ROLE = "SOURCE";

    public LoaderEscrowSection {
        markers = List.copyOf(markers);
        LoaderNbt.require(epoch >= 0 && markers.size() <= MAX_MARKERS, "Invalid loader escrow counters");
        LoaderNbt.require(ledger != null || epoch == 0 && markers.isEmpty(), "Only a pristine loader may be unbound");
        for (EscrowPrefix.Marker marker : markers) {
            LoaderNbt.require(marker.role().equals(ROLE) && marker.epochAfter() == epoch, "Invalid loader marker");
        }
    }

    public static LoaderEscrowSection neutral() { return new LoaderEscrowSection(null, 0, List.of()); }

    /** A marker means escrow holds this device; it refuses removal, extraction and rebinding. */
    public boolean locked() { return !markers.isEmpty(); }

    /** The endpoint state that escrow classifies; only a ledger-bound section has one. */
    public EscrowPrefix.State state(String signature) {
        LoaderNbt.require(ledger != null, "A pristine loader has no escrow state");
        return new EscrowPrefix.State(ledger, epoch, signature, markers);
    }

    public static boolean bounded(Tag tag) {
        return LoaderNbt.bounded(tag, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(LoaderNbt.SCHEMA, 1);
        if (ledger != null) { tag.putUUID("ledger_id", ledger); }
        tag.putLong("resource_epoch", epoch);
        ListTag list = new ListTag();
        for (EscrowPrefix.Marker marker : markers) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("op_id", marker.operation());
            entry.putLong("seq", marker.sequence());
            entry.putString("role", marker.role());
            entry.putLong("epoch_after", marker.epochAfter());
            list.add(entry);
        }
        tag.put("markers", list);
        LoaderNbt.require(bounded(tag), "Loader escrow section exceeds its envelope");
        return tag;
    }

    public static LoaderEscrowSection decode(Tag raw) {
        return LoaderNbt.decoding("loader escrow section", () -> {
            CompoundTag tag = LoaderNbt.schemaOne(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES, "Loader escrow section");
            LoaderNbt.fields(tag, LoaderNbt.SCHEMA, "ledger_id", "resource_epoch", "markers");
            long epoch = LoaderNbt.number(tag, "resource_epoch");
            LoaderNbt.require(tag.contains("markers", Tag.TAG_LIST), "Missing or invalid loader field: markers");
            ListTag list = (ListTag) tag.get("markers");
            LoaderNbt.require(list.size() <= MAX_MARKERS && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND),
                    "Invalid loader marker list");
            var markers = new ArrayList<EscrowPrefix.Marker>();
            for (Tag value : list) {
                CompoundTag entry = (CompoundTag) value;
                LoaderNbt.fields(entry, "op_id", "seq", "role", "epoch_after");
                markers.add(new EscrowPrefix.Marker(LoaderNbt.uuid(entry, "op_id"), LoaderNbt.number(entry, "seq"),
                        LoaderNbt.string(entry, "role"), LoaderNbt.number(entry, "epoch_after")));
            }
            return new LoaderEscrowSection(LoaderNbt.optionalUuid(tag, "ledger_id"), epoch, markers);
        });
    }
}

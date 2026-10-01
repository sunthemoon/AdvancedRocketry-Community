package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * What a persisted endpoint root shows of its transit section (ADR-054 section 2): the outbox seqs, incoming keys and
 * receipt keys a chunk-save or chunk-load tag holds. It reads leniently, because it only observes: a malformed part
 * shows nothing, and the strict decode happens when the block entity loads.
 */
public final class TransitTags {
    /** The state key of the transit section in an endpoint's device root. */
    public static final String SECTION = "transit";
    private static final int MAX_ITEMS = 256;

    private TransitTags() {
    }

    public record Shown(Set<Long> outbox, Set<TransitKey> incoming, Set<TransitKey> receipts) {
        public static final Shown NOTHING = new Shown(Set.of(), Set.of(), Set.of());

        /** Registration freeze (review R3-H1): an unregistered copy carrying any of these never registers. */
        public boolean holdsContents() {
            return !outbox.isEmpty() || !incoming.isEmpty() || !receipts.isEmpty();
        }
    }

    public static Shown scan(CompoundTag deviceRoot) {
        if (!(deviceRoot.get(SECTION) instanceof CompoundTag section)) {
            return Shown.NOTHING;
        }
        Set<Long> outbox = new HashSet<>();
        for (CompoundTag entry : compounds(section, "outbox")) {
            if (entry.contains("seq", Tag.TAG_LONG)) {
                outbox.add(entry.getLong("seq"));
            }
        }
        return new Shown(Set.copyOf(outbox), keys(compounds(section, "incoming")), keys(compounds(section,
                "receipts")));
    }

    private static Iterable<CompoundTag> compounds(CompoundTag section, String name) {
        Set<CompoundTag> found = new HashSet<>();
        if (section.get(name) instanceof ListTag list && list.getElementType() == Tag.TAG_COMPOUND
                && list.size() <= MAX_ITEMS) {
            for (int i = 0; i < list.size(); i++) {
                found.add(list.getCompound(i));
            }
        }
        return found;
    }

    private static Set<TransitKey> keys(Iterable<CompoundTag> entries) {
        Set<TransitKey> keys = new HashSet<>();
        for (CompoundTag entry : entries) {
            if (entry.contains("source", Tag.TAG_INT_ARRAY) && entry.getIntArray("source").length == 4
                    && entry.contains("seq", Tag.TAG_LONG) && entry.getLong("seq") >= 1L) {
                UUID source = NbtUtils.loadUUID(entry.get("source"));
                keys.add(new TransitKey(source, entry.getLong("seq")));
            }
        }
        return Set.copyOf(keys);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Native full-slot census. A snapshot ID remains available in U even when D and its logical ID are absent. */
final class SealedEntityImages {
    private record Layer(ListTag images, boolean nested) { }
    private record Named(CompoundTag image, boolean nested) { }
    private SealedEntityImages() { }
    static boolean present(CompoundTag root, UUID logical, UUID snapshot, UUID entity, CompoundTag frozen) {
        var named = named(root, logical, snapshot, entity);
        return named != null && named.size() == 1 && frozen.hasUUID("UUID") && frozen.getUUID("UUID").equals(entity)
                && !named.get(0).nested && RocketRetirementProof.matches(named.get(0).image, frozen);
    }
    /** One common exact projection plus the bounded observed UUID set, not a hash-only copy election. */
    static boolean presentCopies(CompoundTag root, UUID logical, UUID snapshot, UUID expected,
            CompoundTag frozen, List<UUID> copies) {
        if (copies.isEmpty() || copies.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                || !frozen.hasUUID("UUID") || !copies.contains(frozen.getUUID("UUID")) || frozen.contains("Passengers")) { return false; }
        var remaining = new java.util.HashSet<>(copies);
        if (remaining.size() != copies.size()) { return false; }
        var named = named(root, logical, snapshot, expected);
        if (named == null || named.size() != copies.size()) { return false; }
        for (var candidate : named) {
            var image = candidate.image;
            if (candidate.nested || !image.hasUUID("UUID") || !remaining.remove(image.getUUID("UUID"))
                    || !image.getString("id").equals(frozen.getString("id")) || image.contains("Passengers")
                    || !Objects.equals(image.get("Pos"), frozen.get("Pos"))
                    || !image.contains(RocketRetirementProof.DATA, Tag.TAG_COMPOUND)
                    || !Objects.equals(image.get(RocketRetirementProof.DATA), frozen.get(RocketRetirementProof.DATA))) { return false; }
        }
        return remaining.isEmpty();
    }
    static boolean absent(CompoundTag root, UUID logical, UUID snapshot, UUID entity) {
        var named = named(root, logical, snapshot, entity); return named != null && named.isEmpty();
    }
    private static List<Named> named(CompoundTag root, UUID logical, UUID snapshot, UUID entity) {
        if (!BoundedNbt.fits(root, AnvilRecordReader.DECODED_BYTES, 64, RocketRetirementProof.MAX_ENTITY_TAGS)) { return null; }
        var initial = list(root, "Entities"); if (initial == null) { return null; }
        var pending = new ArrayDeque<Layer>(); pending.push(new Layer(initial, false)); var found = new ArrayList<Named>(); int count = 0;
        while (!pending.isEmpty()) {
            var layer = pending.pop();
            for (Tag raw : layer.images) {
                if (++count > RocketRetirementProof.MAX_ENTITY_TAGS || !(raw instanceof CompoundTag image)) { return null; }
                boolean matches = image.hasUUID("UUID") && image.getUUID("UUID").equals(entity);
                if (image.getString("id").equals(ModIdentity.MOD_ID + ":rocket") || image.contains(RocketRetirementProof.DATA)) {
                    if (!image.contains(RocketRetirementProof.DATA, Tag.TAG_COMPOUND)) { return null; }
                    var data = image.getCompound(RocketRetirementProof.DATA);
                    if (!data.hasUUID("assembly_transaction_id") || !data.contains("snapshot", Tag.TAG_COMPOUND)) { return null; }
                    // RocketSnapshotNbtCodec has always persisted this identity as canonical UUID text.
                    // It is not the native UUID array used by the entity and the independent carrier schema.
                    UUID capturedSnapshot = snapshotIdentity(data.getCompound("snapshot"));
                    if (capturedSnapshot == null) { return null; }
                    matches |= capturedSnapshot.equals(snapshot)
                            || logical != null && RocketRetirementProof.names(image, logical, entity);
                }
                if (matches) { found.add(new Named(image, layer.nested)); }
                if (image.contains("Passengers")) {
                    var children = list(image, "Passengers"); if (children == null) { return null; } pending.push(new Layer(children, true));
                }
            }
        }
        return found;
    }
    private static UUID snapshotIdentity(CompoundTag snapshot) {
        if (!snapshot.contains("snapshot_id", Tag.TAG_STRING)) { return null; }
        var text = snapshot.getString("snapshot_id");
        if (text.length() != 36) { return null; }
        try { var id = UUID.fromString(text); return id.toString().equals(text) ? id : null; }
        catch (IllegalArgumentException malformed) { return null; }
    }
    private static ListTag list(CompoundTag root, String key) {
        return root.get(key) instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND) ? list : null;
    }
}

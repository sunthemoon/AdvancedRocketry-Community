package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Native full-slot census. A snapshot ID remains available in U even when D and its logical ID are absent. */
final class SealedEntityImages {
    private SealedEntityImages() { }
    static boolean present(CompoundTag root, UUID logical, UUID snapshot, UUID entity, CompoundTag frozen) {
        var named = named(root, logical, snapshot, entity);
        return named != null && named.size() == 1 && frozen.hasUUID("UUID") && frozen.getUUID("UUID").equals(entity)
                && RocketRetirementProof.matches(named.get(0), frozen);
    }
    static boolean absent(CompoundTag root, UUID logical, UUID snapshot, UUID entity) {
        var named = named(root, logical, snapshot, entity); return named != null && named.isEmpty();
    }
    private static List<CompoundTag> named(CompoundTag root, UUID logical, UUID snapshot, UUID entity) {
        if (!BoundedNbt.fits(root, AnvilRecordReader.DECODED_BYTES, 64, RocketRetirementProof.MAX_ENTITY_TAGS)) { return null; }
        var initial = list(root, "Entities"); if (initial == null) { return null; }
        var pending = new ArrayDeque<ListTag>(); pending.push(initial); var found = new ArrayList<CompoundTag>(); int count = 0;
        while (!pending.isEmpty()) {
            for (Tag raw : pending.pop()) {
                if (++count > RocketRetirementProof.MAX_ENTITY_TAGS || !(raw instanceof CompoundTag image)) { return null; }
                boolean matches = image.hasUUID("UUID") && image.getUUID("UUID").equals(entity);
                if (image.getString("id").equals(ModIdentity.MOD_ID + ":rocket") || image.contains(RocketRetirementProof.DATA)) {
                    if (!image.contains(RocketRetirementProof.DATA, Tag.TAG_COMPOUND)) { return null; }
                    var data = image.getCompound(RocketRetirementProof.DATA);
                    if (!data.hasUUID("assembly_transaction_id") || !data.contains("snapshot", Tag.TAG_COMPOUND)
                            || !data.getCompound("snapshot").hasUUID("snapshot_id")) { return null; }
                    matches |= data.getCompound("snapshot").getUUID("snapshot_id").equals(snapshot)
                            || logical != null && RocketRetirementProof.names(image, logical, entity);
                }
                if (matches) { found.add(image); }
                if (image.contains("Passengers")) {
                    var children = list(image, "Passengers"); if (children == null) { return null; } pending.push(children);
                }
            }
        }
        return found;
    }
    private static ListTag list(CompoundTag root, String key) {
        return root.get(key) instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND) ? list : null;
    }
}

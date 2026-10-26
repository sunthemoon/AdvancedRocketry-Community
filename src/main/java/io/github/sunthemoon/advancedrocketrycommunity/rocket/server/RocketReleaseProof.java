package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketReleasePlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.ArrayDeque;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** B0/B1 predicates on the exact captured and read-back native slot, not a live-entity observation. */
final class RocketReleaseProof {
    private RocketReleaseProof() { }
    private record Entities(ListTag list, boolean top) { }

    /** -1 is invalid; 0 and 1 are combined across the complete recorded census by the coordinator. */
    static int count(CompoundTag root, Batch batch, Slot slot, RocketPassengerManifest b0) {
        if (!batch.slots().contains(slot) || !BoundedNbt.fits(root, AnvilRecordReader.DECODED_BYTES, 64, 262_144)
                || !root.contains("Position", Tag.TAG_INT_ARRAY)
                || !Arrays.equals(root.getIntArray("Position"), new int[] {slot.x(), slot.z()})
                || !compoundList(root, "Entities")) { return -1; }
        var stack = new ArrayDeque<Entities>(); stack.push(new Entities(root.getList("Entities", Tag.TAG_COMPOUND), true));
        int found = 0;
        while (!stack.isEmpty()) {
            var list = stack.pop();
            for (var raw : list.list()) {
                var entity = (CompoundTag) raw;
                if (RocketRetirementProof.names(entity, batch.rocket(), batch.authority())) {
                    if (!list.top() || ++found > 1 || !authority(entity, batch, slot, b0)) { return -1; }
                }
                if (entity.contains("Passengers")) {
                    if (!compoundList(entity, "Passengers")) { return -1; }
                    stack.push(new Entities(entity.getList("Passengers", Tag.TAG_COMPOUND), false));
                }
            }
        }
        return b0 != null && found != 1 ? -1 : found;
    }

    private static boolean authority(CompoundTag entity, Batch batch, Slot slot, RocketPassengerManifest b0) {
        if (!slot.equals(new Slot(batch.dimension(), batch.pad().x() >> 4, batch.pad().z() >> 4))
                || !entity.getString("id").equals("advancedrocketrycommunity:rocket")
                || !entity.hasUUID("UUID") || !entity.getUUID("UUID").equals(batch.authority())) { return false; }
        var pos = entity.getList("Pos", Tag.TAG_DOUBLE);
        if (pos.size() != 3 || pos.getDouble(0) != batch.pad().x() + .5D || pos.getDouble(1) != batch.pad().y()
                || pos.getDouble(2) != batch.pad().z() + .5D) { return false; }
        var data = entity.getCompound(RocketRetirementProof.DATA);
        if (data.getInt("schema_version") < 2 || data.getInt("schema_version") > 3
                || !data.hasUUID("assembly_transaction_id") || !data.getUUID("assembly_transaction_id").equals(batch.rocket())) { return false; }
        var snapshot = RocketSnapshotNbtCodec.decode(data.getCompound("snapshot")).snapshot().orElse(null);
        var flight = RocketFlightNbtCodec.decode(data.getCompound("flight_data")).data().orElse(null);
        if (snapshot == null || flight == null || !flight.logicalRocketId().equals(batch.rocket())
                || flight.activeTransferId().isPresent() || !flight.state().acceptsFuel()
                || !snapshot.sourceDimension().toString().equals(batch.dimension())
                || !flight.currentDimension().equals(snapshot.sourceDimension()) || !flight.currentOrigin().equals(snapshot.sourceOrigin())
                || !position(snapshot.sourceOrigin()).equals(batch.pad())
                || !RocketSnapshotNbtCodec.encode(snapshot).equals(data.getCompound("snapshot"))
                || !RocketFlightNbtCodec.encode(flight).equals(data.getCompound("flight_data"))
                || !RocketReleasePlan.resources(snapshot, flight).equals(batch.resources())) { return false; }
        var region = RocketRegion.fromSnapshot(snapshot);
        if (!position(region.minimum()).equals(batch.minimum()) || !position(region.maximum()).equals(batch.maximum())) { return false; }
        return b0 != null ? flight.passengers().equals(b0)
                : flight.passengers().assignments().stream().noneMatch(s -> batch.released().contains(s.passengerId()));
    }
    private static boolean compoundList(CompoundTag root, String name) {
        return root.get(name) instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND);
    }
    private static Position position(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition p) {
        return new Position(p.x(), p.y(), p.z());
    }
}

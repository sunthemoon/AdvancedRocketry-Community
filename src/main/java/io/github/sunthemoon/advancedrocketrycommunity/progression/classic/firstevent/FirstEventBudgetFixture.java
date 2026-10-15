package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Valid record-only budget fixture; no ignored padding, changed budget, or imported player/platform claim. */
final class FirstEventBudgetFixture {
    private FirstEventBudgetFixture() { }

    static CompoundTag nearBound(CompoundTag preWarp, FirstEvent.StationWarp event) {
        CompoundTag root = preWarp.copy();
        ListTag stations = root.getList("stations", 10);
        CompoundTag template = stations.getCompound(0);
        int size = RocketNbtSize.uncompressedBytes(root);
        int full = payload(record(template, 0, true));
        int minimal = payload(record(template, 0, false));
        int index = 0;
        while (size + full <= StationLimits.MAX_REGISTRY_NBT_BYTES - 32) {
            if (stations.size() >= StationLimits.MAX_STATIONS - 2) { throw new IllegalStateException("Budget fixture exhausted its record bound"); }
            stations.add(record(template, index++, true)); size += full;
        }
        if (size + minimal > StationLimits.MAX_REGISTRY_NBT_BYTES - 32) {
            size -= payload(stations.getCompound(stations.size() - 1));
            stations.remove(stations.size() - 1); index--;
        }
        stations.add(record(template, index++, false)); size += minimal;
        if (StationLimits.MAX_REGISTRY_NBT_BYTES - 32 - size > full - minimal) {
            stations.add(record(template, index++, false)); size += minimal;
        }
        CompoundTag tail = stations.getCompound(stations.size() - 1);
        int id = 0;
        for (String list : new String[]{"members", "invitations"}) {
            ListTag values = tail.getList(list, 10);
            while (values.size() < 32) {
                var item = identifier(index, ++id);
                int bytes = payload(item);
                if (size + bytes > StationLimits.MAX_REGISTRY_NBT_BYTES - 32) { break; }
                values.add(item); size += bytes;
            }
            tail.put(list, values);
        }
        int extra = StationLimits.MAX_REGISTRY_NBT_BYTES - 32 - size;
        if (extra < 0 || extra > 47) { throw new IllegalStateException("Budget fixture cannot close the exact size"); }
        tail.putString("name", "B".repeat(1 + extra));
        int exact = RocketNbtSize.uncompressedBytes(root);
        var loaded = StationRegistrySavedData.load(root.copy());
        if (exact != StationLimits.MAX_REGISTRY_NBT_BYTES - 32 || !loaded.operational() || loaded.admitsFirstWarp(0)) {
            throw new IllegalStateException("The near-bound root is not valid or still admits an outbox");
        }
        CompoundTag candidate = root.copy();
        candidate.put("classic_first_warp", FirstEventOutbox.elected(event).encode(event.kind()));
        candidate.getCompound("orbital_clock").putLong("logical_tick", Math.max(event.orbitalTick(),
                candidate.getCompound("orbital_clock").getLong("logical_tick")));
        if (RocketNbtSize.uncompressedBytes(candidate) <= StationLimits.MAX_REGISTRY_NBT_BYTES) {
            throw new IllegalStateException("The exact first-event candidate does not exceed the unchanged bound");
        }
        return root;
    }

    private static int payload(CompoundTag value) { return RocketNbtSize.uncompressedBytes(value) - 3; }

    private static CompoundTag identifier(int record, int value) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", new UUID(0x57444c00_00000000L + record, value));
        return tag;
    }

    private static CompoundTag record(CompoundTag template, int index, boolean full) {
        CompoundTag tag = template.copy();
        var cell = new StationGridCell(100 + index, 100);
        var region = cell.region();
        tag.putUUID("station_id", new UUID(0x57444c10_00000000L, index + 1L));
        tag.putUUID("owner_id", new UUID(0x57444c20_00000000L, index + 1L));
        tag.putString("name", full ? "B".repeat(48) : "B");
        tag.putInt("cell_x", cell.x()); tag.putInt("cell_z", cell.z());
        tag.putIntArray("region", new int[]{region.minimumX(), region.minimumZ(), region.maximumX(), region.maximumZ()});
        tag.putIntArray("landing_pad", new int[]{cell.centerX(), StationLimits.LANDING_Y, cell.centerZ()});
        var members = new ListTag(); var invitations = new ListTag();
        if (full) { for (int id = 1; id <= 32; id++) { members.add(identifier(index, id)); invitations.add(identifier(index, id + 32)); } }
        tag.put("members", members); tag.put("invitations", invitations);
        return tag;
    }
}

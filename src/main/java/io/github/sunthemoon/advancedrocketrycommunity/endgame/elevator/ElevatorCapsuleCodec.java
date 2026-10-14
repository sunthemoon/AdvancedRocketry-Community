package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** W46: a bounded, transient vanilla update-tag projection, never a saved ride or a player identity. */
public final class ElevatorCapsuleCodec {
    public static final String KEY = "capsule_rides";
    public static final int MAX_RIDES = Math.min(8, ElevatorRules.PENDING_RIDES_PER_ENDPOINT);
    private static final Set<String> ENTRY_KEYS = Set.of("entity_id", "remaining_ticks");
    private static final Set<String> UPDATE_KEYS = Set.of("tether", KEY);

    public record Entry(int entityId, int remainingTicks) {
        public Entry {
            if (entityId < 0 || remainingTicks < 0 || remainingTicks > ElevatorRules.RIDE_COUNTDOWN_TICKS) {
                throw new IllegalArgumentException("Invalid elevator capsule projection");
            }
        }
    }

    private ElevatorCapsuleCodec() {
    }

    public static void encode(CompoundTag update, List<Entry> rides) {
        if (rides.size() > MAX_RIDES) {
            throw new IllegalArgumentException("Too many projected elevator rides");
        }
        Set<Integer> ids = new HashSet<>();
        ListTag list = new ListTag();
        for (Entry ride : rides) {
            if (!ids.add(ride.entityId())) {
                throw new IllegalArgumentException("Duplicate projected elevator rider");
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("entity_id", ride.entityId());
            entry.putInt("remaining_ticks", ride.remainingTicks());
            list.add(entry);
        }
        update.put(KEY, list);
    }

    /** Reject the entire projection, without copying or traversing oversized/foreign payloads. */
    public static List<Entry> decode(@Nullable CompoundTag update) {
        if (update == null || update.size() > UPDATE_KEYS.size()
                || !UPDATE_KEYS.containsAll(update.getAllKeys()) || !(update.get(KEY) instanceof ListTag list)
                || list.size() > MAX_RIDES || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) {
            return List.of();
        }
        List<Entry> rides = new ArrayList<>(list.size());
        Set<Integer> ids = new HashSet<>();
        for (Tag value : list) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.getAllKeys().equals(ENTRY_KEYS) || !entry.contains("entity_id", Tag.TAG_INT)
                    || !entry.contains("remaining_ticks", Tag.TAG_INT)) {
                return List.of();
            }
            int id = entry.getInt("entity_id");
            int ticks = entry.getInt("remaining_ticks");
            if (id < 0 || ticks < 0 || ticks > ElevatorRules.RIDE_COUNTDOWN_TICKS || !ids.add(id)) {
                return List.of();
            }
            rides.add(new Entry(id, ticks));
        }
        return List.copyOf(rides);
    }
}

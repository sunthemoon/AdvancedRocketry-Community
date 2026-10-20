package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * W98 (P1-N3 leaf N3-A1): the pure parts of an ordinary retirement proof. The census validators read one entity slot's
 * root as the barrier captures it and as it reads it back; the crew rule is R17's placement rule with R3's exception.
 */
final class RocketRetirementProof {
    static final String DATA = "RocketEntityData";
    /** Passenger nesting visited per slot; the barrier already bounds a slot's depth and node count. */
    static final int MAX_ENTITY_TAGS = 262_144;

    private RocketRetirementProof() { }

    /** One census slot: an entity-section column of one Level. */
    record Slot(ResourceLocation dimension, ChunkPos chunk) {
        Slot { Objects.requireNonNull(dimension); Objects.requireNonNull(chunk); }

        /** An end's origin column, where the ordinary flight holds its ticket. */
        static Slot origin(RocketStructureSnapshot snapshot) {
            var origin = snapshot.sourceOrigin();
            return new Slot(snapshot.sourceDimension(), new ChunkPos(origin.x() >> 4, origin.z() >> 4));
        }
    }

    /**
     * W98-R7: adds a census slot once. A new slot past {@code limit} is refused, never dropped: truncation is not absence,
     * so the caller holds the return.
     */
    static boolean addSlot(List<Slot> census, Slot slot, int limit) {
        if (census.contains(slot)) { return true; }
        if (census.size() >= limit) { return false; }
        census.add(slot);
        return true;
    }

    /** A crew member's observed placement. */
    enum Seat { OFFLINE, PLACED, UNPLACED }

    /** The survivor's slot holds exactly one entity naming L or the survivor's UUID, and it equals the frozen projection. */
    static boolean survivorSlot(CompoundTag root, UUID logical, CompoundTag frozen) {
        var named = named(root, logical, frozen.getUUID("UUID"));
        return named != null && named.size() == 1 && matches(named.get(0), frozen);
    }

    /** A discarded candidate's slot holds no entity naming L or the survivor's UUID, at any passenger depth. */
    static boolean absentSlot(CompoundTag root, UUID logical, UUID survivor) {
        var named = named(root, logical, survivor);
        return named != null && named.isEmpty();
    }

    /** Entity tags naming L, or carrying the survivor's UUID, including nested passengers; null past the bound. */
    static List<CompoundTag> named(CompoundTag root, UUID logical, UUID survivor) {
        if (!root.contains("Entities", Tag.TAG_LIST)) { return null; }
        var found = new ArrayList<CompoundTag>();
        var lists = new ArrayDeque<ListTag>();
        lists.push(root.getList("Entities", Tag.TAG_COMPOUND));
        int visited = 0;
        while (!lists.isEmpty()) {
            for (Tag raw : lists.pop()) {
                if (++visited > MAX_ENTITY_TAGS || !(raw instanceof CompoundTag entity)) { return null; }
                if (names(entity, logical, survivor)) { found.add(entity); }
                if (entity.contains("Passengers", Tag.TAG_LIST)) { lists.push(entity.getList("Passengers", Tag.TAG_COMPOUND)); }
            }
        }
        return found;
    }

    /** Any image of L: its UUID, its assembly identity, or its flight data's logical identity. */
    static boolean names(CompoundTag entity, UUID logical, UUID survivor) {
        if (entity.hasUUID("UUID") && entity.getUUID("UUID").equals(survivor)) { return true; }
        if (!entity.contains(DATA, Tag.TAG_COMPOUND)) { return false; }
        var data = entity.getCompound(DATA);
        if (data.hasUUID("assembly_transaction_id") && data.getUUID("assembly_transaction_id").equals(logical)) { return true; }
        return data.contains("flight_data", Tag.TAG_COMPOUND) && data.getCompound("flight_data").hasUUID("logical_rocket_id")
                && data.getCompound("flight_data").getUUID("logical_rocket_id").equals(logical);
    }

    /** The frozen projection: type, UUID, position and the complete rocket payload, with no nested entity. */
    static boolean matches(CompoundTag image, CompoundTag frozen) {
        return frozen.hasUUID("UUID") && image.getString("id").equals(frozen.getString("id"))
                && Objects.equals(image.get("UUID"), frozen.get("UUID")) && Objects.equals(image.get("Pos"), frozen.get("Pos"))
                && image.contains(DATA, Tag.TAG_COMPOUND) && Objects.equals(image.get(DATA), frozen.get(DATA))
                && !image.contains("Passengers") && !frozen.contains("Passengers");
    }

    /**
     * R3: every seat of T's source manifest is placed (online, alive, connected, in the survivor's Level, aboard it or
     * unmounted), was already placed during this return, or is offline where {@code offlineAllowed} (a countdown
     * cancellation never left its pad). A placement is remembered, so a later dismount or logout never undoes delivery.
     */
    static boolean crewReady(List<UUID> seats, Function<UUID, Seat> observe, Set<UUID> delivered, boolean offlineAllowed) {
        boolean ready = true;
        for (UUID seat : seats) {
            if (delivered.contains(seat)) { continue; }
            switch (observe.apply(seat)) {
                case PLACED -> delivered.add(seat);
                case OFFLINE -> ready &= offlineAllowed;
                case UNPLACED -> ready = false;
            }
        }
        return ready;
    }

    /** R17's placement predicate, as {@code EscrowGovernedTerminalPassengers.placement} for a cancellation. */
    static Seat seat(boolean present, boolean online, boolean sameLevel, boolean aboardSurvivor, boolean unmounted) {
        return !present ? Seat.OFFLINE : online && sameLevel && (aboardSurvivor || unmounted) ? Seat.PLACED : Seat.UNPLACED;
    }
}

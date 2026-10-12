package io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconIndex;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The server's one beacon index (ADR-065 section 7), stored with the Overworld data. A root that does not decode
 * (unknown, future, malformed or over a bound) is kept by reference and written back unchanged; while it is kept the
 * index is unavailable, so no beacon registers and no finder sees a marker, and nothing is deleted. Recovery is an
 * offline repair of {@code data/advancedrocketrycommunity_planetary_beacons.dat} from a backup; loaded beacons
 * re-register themselves after the next restart with a valid root.
 */
public final class BeaconIndexSavedData extends SavedData {
    public static final String NAME = "advancedrocketrycommunity_planetary_beacons";

    private final BeaconIndex index;
    private final CompoundTag refused;

    private BeaconIndexSavedData(BeaconIndex index, CompoundTag refused) {
        this.index = index;
        this.refused = refused;
    }

    public BeaconIndexSavedData() {
        this(BeaconIndexCodec.emptyIndex(), null);
    }

    public static BeaconIndexSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(BeaconIndexSavedData::load,
                BeaconIndexSavedData::new, NAME);
    }

    public static BeaconIndexSavedData load(CompoundTag raw) {
        try {
            return new BeaconIndexSavedData(BeaconIndexCodec.decode(raw), null);
        } catch (IllegalArgumentException refusedRoot) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_BEACON_INDEX_REFUSED: the planetary beacon index is kept "
                    + "unchanged and unavailable ({}); back it up and repair it offline", refusedRoot.getMessage());
            return new BeaconIndexSavedData(BeaconIndexCodec.emptyIndex(), raw);
        }
    }

    public boolean operational() {
        return refused == null;
    }

    public Optional<BeaconRecord> get(UUID beaconId) {
        return operational() ? index.get(beaconId) : Optional.empty();
    }

    /** The owner's records, at most 32; empty while the index is unavailable. */
    public List<BeaconRecord> ownedBy(UUID owner) {
        return operational() ? index.ownedBy(owner) : List.of();
    }

    /** Inserts or replaces a record; empty while the index is unavailable. */
    public Optional<BeaconIndex.Admission> put(BeaconRecord record) {
        if (!operational()) {
            return Optional.empty();
        }
        BeaconIndex.Admission admission = index.put(record);
        if (admission == BeaconIndex.Admission.ADMITTED) {
            setDirty();
        }
        return Optional.of(admission);
    }

    /**
     * Inserts or replaces a record; a refused replacement at the same position still takes the new activity and
     * generation ({@link BeaconIndex#update}). Empty while the index is unavailable.
     */
    public Optional<BeaconIndex.Admission> update(BeaconRecord record) {
        if (!operational()) {
            return Optional.empty();
        }
        Optional<BeaconRecord> before = index.get(record.beaconId());
        BeaconIndex.Admission admission = index.update(record);
        if (!index.get(record.beaconId()).equals(before)) {
            setDirty();
        }
        return Optional.of(admission);
    }

    public boolean remove(UUID beaconId) {
        if (!operational() || index.remove(beaconId).isEmpty()) {
            return false;
        }
        setDirty();
        return true;
    }

    public int size() {
        return index.size();
    }

    @Override
    public CompoundTag save(CompoundTag ignored) {
        return refused != null ? refused : BeaconIndexCodec.encode(index);
    }
}

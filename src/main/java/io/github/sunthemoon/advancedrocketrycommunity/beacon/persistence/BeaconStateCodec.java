package io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The schema-1 {@code arce_planetary_beacon} block-entity root: the stable beacon UUID, the optional owner, the name,
 * the owner's enabled switch and the last structure generation. The block entity holds no item, fluid or energy, so a
 * root that does not decode is kept unchanged and the beacon stays inactive and unregistered.
 */
public final class BeaconStateCodec {
    public static final String KEY = "arce_planetary_beacon";
    public static final int SCHEMA = 1;
    private static final Set<String> REQUIRED = Set.of("schema_version", "beacon_id", "name", "enabled", "generation");
    private static final Set<String> WITH_OWNER = Set.of("schema_version", "beacon_id", "owner", "name", "enabled",
            "generation");

    private BeaconStateCodec() {
    }

    /** One beacon's persistent state. */
    public record State(UUID beaconId, Optional<UUID> owner, String name, boolean enabled, long generation) {
        public State {
            Objects.requireNonNull(beaconId, "beaconId");
            Objects.requireNonNull(owner, "owner");
            if (!BeaconLimits.validName(name) || generation < 0) {
                throw new IllegalArgumentException("A beacon name or generation is out of range");
            }
        }

        public static State fresh() {
            return new State(UUID.randomUUID(), Optional.empty(), "", true, 0);
        }

        public State withOwner(UUID value) {
            return new State(beaconId, Optional.of(value), name, enabled, generation);
        }

        public State withName(String value) {
            return new State(beaconId, owner, value, enabled, generation);
        }

        public State withEnabled(boolean value) {
            return new State(beaconId, owner, name, value, generation);
        }

        /** The next structure generation, saturating at {@link Long#MAX_VALUE}. */
        public State nextGeneration() {
            return new State(beaconId, owner, name, enabled, generation == Long.MAX_VALUE ? generation : generation + 1);
        }
    }

    public static CompoundTag encode(State state) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema_version", SCHEMA);
        tag.putUUID("beacon_id", state.beaconId());
        state.owner().ifPresent(owner -> tag.putUUID("owner", owner));
        tag.putString("name", state.name());
        tag.putBoolean("enabled", state.enabled());
        tag.putLong("generation", state.generation());
        return tag;
    }

    public static State decode(Tag raw) {
        if (!(raw instanceof CompoundTag tag)) {
            throw new IllegalArgumentException("Beacon state is not a compound");
        }
        boolean owned = tag.getAllKeys().equals(WITH_OWNER);
        if (!owned && !tag.getAllKeys().equals(REQUIRED) || !tag.contains("schema_version", Tag.TAG_INT)
                || !BeaconIndexCodec.uuid(tag, "beacon_id") || owned && !BeaconIndexCodec.uuid(tag, "owner")
                || !tag.contains("name", Tag.TAG_STRING) || !tag.contains("enabled", Tag.TAG_BYTE)
                || !tag.contains("generation", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Beacon state keys or types");
        }
        if (tag.getInt("schema_version") != SCHEMA) {
            throw new IllegalArgumentException("Unsupported beacon state schema");
        }
        byte enabled = tag.getByte("enabled");
        if (enabled != 0 && enabled != 1) {
            throw new IllegalArgumentException("Beacon enabled flag");
        }
        return new State(tag.getUUID("beacon_id"), owned ? Optional.of(tag.getUUID("owner")) : Optional.empty(),
                tag.getString("name"), enabled == 1, tag.getLong("generation"));
    }
}

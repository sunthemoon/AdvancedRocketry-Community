package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * The {@code advancedrocketrycommunity_endgame.dat} root, schema 1 (ADR-054 section 10). Sections: {@code endpoints}
 * (index records, and tombstones: young ones in full, settled ones as six longs), {@code dispatched_through} (one
 * long array of three longs per non-zero entry, review R3-L5), {@code transits} and {@code elevator_pairs} (empty
 * until C12) and {@code zones}. Decoding is strict and runs the full root checks, so the pre-start validation and
 * the runtime load agree.
 */
public final class EndgameRootCodec {
    public static final String ENDPOINTS = "endpoints";
    public static final String DISPATCHED_THROUGH = "dispatched_through";
    public static final String TRANSITS = "transits";
    public static final String ELEVATOR_PAIRS = "elevator_pairs";
    public static final String ZONES = "zones";
    public static final String SAVE_EPOCH = "save_epoch";
    private static final String SETTLE_ORDER = "settle_order";
    private static final String SCHEMA = "schema_version";
    private static final String FORMAT_EPOCH = "format_epoch";
    private static final Set<String> ROOT_KEYS = Set.of(SCHEMA, FORMAT_EPOCH, SAVE_EPOCH, SETTLE_ORDER, ENDPOINTS,
            DISPATCHED_THROUGH, TRANSITS, ELEVATOR_PAIRS, ZONES);
    private static final Set<String> RECORD_KEYS = Set.of("id", "kind", "owner", "level", "pos", "state",
            "registered_epoch");
    private static final Set<String> YOUNG_KEYS = Set.of("id", "owner", "level", "pos", "state");
    private static final Set<String> SETTLED_KEYS = Set.of("s");
    private static final Set<String> ZONE_KEYS = Set.of("name", "level", "min_x", "min_z", "max_x", "max_z", "allow");
    private static final String RETIRED = "RETIRED";
    private static final int MAX_ENDPOINT_ENTRIES = EndgameLimits.MAX_ENDPOINTS + EndgameLimits.MAX_SETTLED_TOMBSTONES
            + EndgameLimits.MAX_PINNED_TOMBSTONES;

    private EndgameRootCodec() {
    }

    public static CompoundTag encode(EndgameRoot root, CompoundTag target) {
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.ENDGAME, target);
        target.putLong(SAVE_EPOCH, root.epochToWrite());
        target.putInt(SETTLE_ORDER, root.nextSettleOrder());
        ListTag endpoints = new ListTag();
        root.endpoints().forEach(record -> endpoints.add(encodeRecord(record)));
        root.youngTombstones().forEach(tombstone -> endpoints.add(encodeYoung(tombstone)));
        root.settledTombstones().forEach(tombstone -> endpoints.add(encodeSettled(tombstone)));
        target.put(ENDPOINTS, endpoints);
        target.put(DISPATCHED_THROUGH, encodeDispatchedThrough(root.dispatchedThrough()));
        target.put(TRANSITS, new ListTag());
        target.put(ELEVATOR_PAIRS, new ListTag());
        ListTag zones = new ListTag();
        root.zones().forEach(zone -> zones.add(encodeZone(zone)));
        target.put(ZONES, zones);
        if (EndgameNbt.uncompressedBytes(target) > EndgameLimits.MAX_ROOT_BYTES) {
            throw new IllegalStateException("Encoded endgame root exceeds its fixed 4 MiB bound");
        }
        return target;
    }

    /** Decodes a current-schema payload (the migrator has checked schema and format epoch). */
    public static EndgameRoot decode(CompoundTag source) {
        if (EndgameNbt.uncompressedBytes(source) > EndgameLimits.MAX_ROOT_BYTES) {
            throw new IllegalArgumentException("Endgame root exceeds its fixed 4 MiB bound");
        }
        EndgameNbt.requireKeys(source, ROOT_KEYS, "endgame root");
        if (EndgameNbt.requireInt(source, SCHEMA) != EndgameLimits.ROOT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported endgame root schema");
        }
        EndgameRoot root = EndgameRoot.restore(EndgameNbt.requireLong(source, SAVE_EPOCH),
                EndgameNbt.requireInt(source, SETTLE_ORDER));
        for (Tag raw : EndgameNbt.requireList(source, ENDPOINTS, Tag.TAG_COMPOUND, MAX_ENDPOINT_ENTRIES)) {
            CompoundTag entry = (CompoundTag) raw;
            if (entry.contains("s")) {
                root.restoreSettled(decodeSettled(entry));
            } else if (RETIRED.equals(entry.getString("state"))) {
                root.restoreYoung(decodeYoung(entry));
            } else {
                root.restoreEndpoint(decodeRecord(entry));
            }
        }
        long[] dispatched = EndgameNbt.requireLongArray(source, DISPATCHED_THROUGH);
        if (dispatched.length % 3 != 0) {
            throw new IllegalArgumentException("dispatched_through holds three longs per entry");
        }
        for (int i = 0; i < dispatched.length; i += 3) {
            root.restoreDispatchedThrough(new UUID(dispatched[i], dispatched[i + 1]), dispatched[i + 2]);
        }
        if (!EndgameNbt.requireList(source, TRANSITS, Tag.TAG_COMPOUND, 0).isEmpty()
                || !EndgameNbt.requireList(source, ELEVATOR_PAIRS, Tag.TAG_COMPOUND, 0).isEmpty()) {
            throw new IllegalArgumentException("Transit records and elevator pairs need the C12 codec");
        }
        for (Tag raw : EndgameNbt.requireList(source, ZONES, Tag.TAG_COMPOUND, EndgameLimits.MAX_ZONES)) {
            root.restoreZone(decodeZone((CompoundTag) raw));
        }
        root.finishRestore();
        return root;
    }

    static CompoundTag encodeRecord(EndpointRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", record.id());
        tag.putString("kind", record.kind().toString());
        tag.putUUID("owner", record.owner());
        tag.putString("level", record.level().toString());
        tag.putLong("pos", record.pos());
        tag.putString("state", record.state().name());
        tag.putLong("registered_epoch", record.registeredEpoch());
        return tag;
    }

    private static EndpointRecord decodeRecord(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, RECORD_KEYS, "endpoint record");
        EndpointRecord.State state = switch (EndgameNbt.requireString(tag, "state", 16)) {
            case "ACTIVE" -> EndpointRecord.State.ACTIVE;
            case "MISSING" -> EndpointRecord.State.MISSING;
            default -> throw new IllegalArgumentException("Unknown endpoint state");
        };
        return new EndpointRecord(EndgameNbt.requireUuid(tag, "id"),
                EndgameNbt.requireLocation(tag, "kind", EndpointRecord.MAX_KIND_LENGTH),
                EndgameNbt.requireUuid(tag, "owner"),
                EndgameNbt.requireLocation(tag, "level", EndpointRecord.MAX_LEVEL_LENGTH),
                EndgameNbt.requireLong(tag, "pos"), state, EndgameNbt.requireLong(tag, "registered_epoch"));
    }

    static CompoundTag encodeYoung(Tombstone.Young tombstone) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", tombstone.id());
        tag.putUUID("owner", tombstone.owner());
        tag.putString("level", tombstone.level().toString());
        tag.putLong("pos", tombstone.pos());
        tag.putString("state", RETIRED);
        return tag;
    }

    private static Tombstone.Young decodeYoung(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, YOUNG_KEYS, "young tombstone");
        return new Tombstone.Young(EndgameNbt.requireUuid(tag, "id"), EndgameNbt.requireUuid(tag, "owner"),
                EndgameNbt.requireLocation(tag, "level", EndpointRecord.MAX_LEVEL_LENGTH),
                EndgameNbt.requireLong(tag, "pos"));
    }

    static CompoundTag encodeSettled(Tombstone.Settled tombstone) {
        CompoundTag tag = new CompoundTag();
        tag.put("s", new LongArrayTag(new long[] {
                tombstone.id().getMostSignificantBits(), tombstone.id().getLeastSignificantBits(),
                tombstone.owner().getMostSignificantBits(), tombstone.owner().getLeastSignificantBits(),
                tombstone.pos(), ((long) tombstone.levelHash() << 32) | (tombstone.order() & 0xFFFFFFFFL)}));
        return tag;
    }

    private static Tombstone.Settled decodeSettled(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, SETTLED_KEYS, "settled tombstone");
        long[] values = EndgameNbt.requireLongArray(tag, "s");
        if (values.length != 6) {
            throw new IllegalArgumentException("A settled tombstone has six longs");
        }
        return new Tombstone.Settled(new UUID(values[0], values[1]), new UUID(values[2], values[3]),
                (int) (values[5] >> 32), values[4], (int) values[5]);
    }

    private static LongArrayTag encodeDispatchedThrough(Map<UUID, Long> values) {
        List<Long> flat = new ArrayList<>(values.size() * 3);
        values.forEach((id, value) -> {
            flat.add(id.getMostSignificantBits());
            flat.add(id.getLeastSignificantBits());
            flat.add(value);
        });
        return new LongArrayTag(flat);
    }

    static CompoundTag encodeZone(ProtectedZone zone) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", zone.name());
        tag.putString("level", zone.level().toString());
        tag.putInt("min_x", zone.minX());
        tag.putInt("min_z", zone.minZ());
        tag.putInt("max_x", zone.maxX());
        tag.putInt("max_z", zone.maxZ());
        ListTag allow = new ListTag();
        zone.allowList().forEach(player -> allow.add(NbtUtils.createUUID(player)));
        tag.put("allow", allow);
        return tag;
    }

    private static ProtectedZone decodeZone(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, ZONE_KEYS, "zone");
        List<UUID> allow = new ArrayList<>();
        for (Tag raw : EndgameNbt.requireList(tag, "allow", Tag.TAG_INT_ARRAY, EndgameLimits.MAX_ZONE_ALLOW_LIST)) {
            if (((net.minecraft.nbt.IntArrayTag) raw).getAsIntArray().length != 4) {
                throw new IllegalArgumentException("A zone allow entry is not a UUID");
            }
            allow.add(NbtUtils.loadUUID(raw));
        }
        return new ProtectedZone(EndgameNbt.requireString(tag, "name", EndgameLimits.MAX_ZONE_NAME_LENGTH),
                EndgameNbt.requireLocation(tag, "level", EndpointRecord.MAX_LEVEL_LENGTH),
                EndgameNbt.requireInt(tag, "min_x"), EndgameNbt.requireInt(tag, "min_z"),
                EndgameNbt.requireInt(tag, "max_x"), EndgameNbt.requireInt(tag, "max_z"), allow);
    }
}

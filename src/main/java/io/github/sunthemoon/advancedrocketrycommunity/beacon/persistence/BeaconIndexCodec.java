package io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconIndex;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconLimits;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Schema-1 beacon index root (ADR-065 section 7): {@code schema_version} 1 and a {@code beacons} list of strict
 * eight-key records in canonical UUID order. Decoding refuses unknown, missing, mistyped, out-of-range, duplicate or
 * over-bound data with {@link IllegalArgumentException}; the caller then keeps the original root unchanged. Sizes are
 * exact NBT payload byte counts, so the 1 MiB bound is checked before anything is written.
 */
public final class BeaconIndexCodec {
    public static final int SCHEMA = 1;
    private static final String SCHEMA_KEY = "schema_version";
    private static final String BEACONS = "beacons";
    private static final String ID = "id";
    private static final String OWNER = "owner";
    private static final String BODY = "body";
    private static final String LEVEL = "level";
    private static final String POS = "pos";
    private static final String GENERATION = "generation";
    private static final String ACTIVE = "active";
    private static final String NAME = "name";
    private static final Set<String> ROOT_KEYS = Set.of(SCHEMA_KEY, BEACONS);
    private static final Set<String> RECORD_KEYS = Set.of(ID, OWNER, BODY, LEVEL, POS, GENERATION, ACTIVE, NAME);
    /** An empty root: the schema int, the list header and the end byte. */
    public static final long ROOT_OVERHEAD = entry(SCHEMA_KEY, Integer.BYTES) + entry(BEACONS, 1 + Integer.BYTES) + 1;

    private BeaconIndexCodec() {
    }

    public static BeaconIndex emptyIndex() {
        return new BeaconIndex(BeaconIndexCodec::recordBytes, ROOT_OVERHEAD);
    }

    public static CompoundTag encode(BeaconIndex index) {
        ListTag beacons = new ListTag();
        for (BeaconRecord record : index.records()) {
            beacons.add(encode(record));
        }
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA_KEY, SCHEMA);
        root.put(BEACONS, beacons);
        return root;
    }

    public static CompoundTag encode(BeaconRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, record.beaconId());
        tag.putUUID(OWNER, record.owner());
        tag.putString(BODY, record.body());
        tag.putString(LEVEL, record.level());
        tag.putIntArray(POS, new int[] {record.x(), record.y(), record.z()});
        tag.putLong(GENERATION, record.generation());
        tag.putBoolean(ACTIVE, record.active());
        tag.putString(NAME, record.name());
        return tag;
    }

    public static BeaconIndex decode(CompoundTag root) {
        if (root == null || !root.getAllKeys().equals(ROOT_KEYS) || !root.contains(SCHEMA_KEY, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Beacon index root keys");
        }
        if (root.getInt(SCHEMA_KEY) != SCHEMA) {
            throw new IllegalArgumentException("Unsupported beacon index schema " + root.getInt(SCHEMA_KEY));
        }
        if (!root.contains(BEACONS, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Beacon index list");
        }
        ListTag beacons = (ListTag) root.get(BEACONS);
        if (beacons.size() > BeaconLimits.MAX_TOTAL
                || !beacons.isEmpty() && beacons.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("Beacon index list bound or type");
        }
        BeaconIndex index = emptyIndex();
        for (Tag element : beacons) {
            BeaconRecord record = decodeRecord((CompoundTag) element);
            if (index.get(record.beaconId()).isPresent()) {
                throw new IllegalArgumentException("Duplicate beacon " + record.beaconId());
            }
            BeaconIndex.Admission admission = index.put(record);
            if (admission != BeaconIndex.Admission.ADMITTED) {
                throw new IllegalArgumentException("Beacon index bound " + admission);
            }
        }
        return index;
    }

    static BeaconRecord decodeRecord(CompoundTag tag) {
        if (!tag.getAllKeys().equals(RECORD_KEYS) || !uuid(tag, ID) || !uuid(tag, OWNER)
                || !tag.contains(BODY, Tag.TAG_STRING) || !tag.contains(LEVEL, Tag.TAG_STRING)
                || !tag.contains(POS, Tag.TAG_INT_ARRAY) || tag.getIntArray(POS).length != 3
                || !tag.contains(GENERATION, Tag.TAG_LONG) || !tag.contains(ACTIVE, Tag.TAG_BYTE)
                || !tag.contains(NAME, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Beacon record keys or types");
        }
        byte active = tag.getByte(ACTIVE);
        if (active != 0 && active != 1) {
            throw new IllegalArgumentException("Beacon active flag");
        }
        int[] position = tag.getIntArray(POS);
        return new BeaconRecord(tag.getUUID(ID), tag.getUUID(OWNER), tag.getString(BODY), tag.getString(LEVEL),
                position[0], position[1], position[2], tag.getLong(GENERATION), active == 1, tag.getString(NAME));
    }

    /** The exact payload size of one encoded record as a list element. */
    public static long recordBytes(BeaconRecord record) {
        long uuid = Integer.BYTES + 4L * Integer.BYTES;
        return entry(ID, uuid) + entry(OWNER, uuid) + entry(BODY, string(record.body()))
                + entry(LEVEL, string(record.level())) + entry(POS, Integer.BYTES + 3L * Integer.BYTES)
                + entry(GENERATION, Long.BYTES) + entry(ACTIVE, 1) + entry(NAME, string(record.name())) + 1;
    }

    /** A named entry: its type byte, the modified-UTF-8 key with its length, and its payload. */
    private static long entry(String key, long payload) {
        return 1 + string(key) + payload;
    }

    /** A NBT string: an unsigned short length and its modified UTF-8 bytes. */
    static long string(String value) {
        long length = 2;
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            length += unit >= 0x0001 && unit <= 0x007F ? 1 : unit <= 0x07FF ? 2 : 3;
        }
        return length;
    }

    /** Whether {@code key} holds a UUID in the exact four-int layout of {@link CompoundTag#putUUID}. */
    static boolean uuid(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_INT_ARRAY) && tag.getIntArray(key).length == 4;
    }
}

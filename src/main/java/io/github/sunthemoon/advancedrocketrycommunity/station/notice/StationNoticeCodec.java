package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Schema-1 station notice root (ADR-065 section 5.3): {@code schema_version} 1 and a {@code stations} list of strict
 * entries {@code {id, rev, acks: [{id, rev}], outcome?: {key, args}, owner?: {id, rev}}}. The root is preflighted at
 * 8 MiB, depth 16 and {@link #MAX_ROOT_NODES} before it is read. Decoding refuses unknown, missing, mistyped,
 * out-of-range, duplicate or over-bound data with {@link IllegalArgumentException}; the caller then keeps the original
 * root unchanged. Short keys keep the largest valid store, about 7.8 MB, inside the 8 MiB bound.
 */
public final class StationNoticeCodec {
    private static final String SCHEMA_KEY = "schema_version";
    private static final String STATIONS = "stations";
    private static final String ID = "id";
    private static final String REVISION = "rev";
    private static final String ACKNOWLEDGEMENTS = "acks";
    private static final String OUTCOME = "outcome";
    private static final String OWNER = "owner";
    private static final String KEY = "key";
    private static final String ARGUMENTS = "args";
    private static final Set<String> ROOT_KEYS = Set.of(SCHEMA_KEY, STATIONS);
    private static final Set<String> ENTRY_REQUIRED = Set.of(ID, REVISION, ACKNOWLEDGEMENTS);
    private static final Set<String> ENTRY_KEYS = Set.of(ID, REVISION, ACKNOWLEDGEMENTS, OUTCOME, OWNER);
    private static final Set<String> ACKNOWLEDGEMENT_KEYS = Set.of(ID, REVISION);
    private static final Set<String> NOTICE_KEYS = Set.of(KEY, ARGUMENTS);
    /**
     * The root, its schema and its list, then per entry: the compound, its ID, revision and acknowledgement list, the
     * member acknowledgements and the owner's (three nodes each), and the outcome with its key, list and arguments.
     */
    public static final int MAX_ROOT_NODES = 3 + StationNoticeLimits.MAX_STATIONS
            * (4 + 3 * StationNoticeLimits.MAX_MEMBER_ACKNOWLEDGEMENTS + 3 + 3 + StationNoticeKind.MAX_ARGUMENTS);

    private StationNoticeCodec() {
    }

    public static CompoundTag encode(StationNoticeBook book) {
        ListTag stations = new ListTag();
        for (Map.Entry<UUID, StationNoticeSummary> entry : book.entries()) {
            stations.add(encode(entry.getKey(), entry.getValue()));
        }
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA_KEY, StationNoticeLimits.ROOT_SCHEMA);
        root.put(STATIONS, stations);
        return root;
    }

    /** The localized argument envelope of one notice. */
    public static CompoundTag encode(StationNotice notice) {
        ListTag arguments = new ListTag();
        notice.arguments().forEach(argument -> arguments.add(StringTag.valueOf(argument)));
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY, notice.kind().key());
        tag.put(ARGUMENTS, arguments);
        return tag;
    }

    public static StationNoticeBook decode(CompoundTag root) {
        if (root == null || !BoundedNbt.fits(root, StationNoticeLimits.MAX_ROOT_BYTES,
                StationNoticeLimits.MAX_ROOT_DEPTH, MAX_ROOT_NODES)) {
            throw new IllegalArgumentException("Station notice root exceeds its byte, depth or node bound");
        }
        if (!root.getAllKeys().equals(ROOT_KEYS) || !root.contains(SCHEMA_KEY, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Station notice root keys");
        }
        if (root.getInt(SCHEMA_KEY) != StationNoticeLimits.ROOT_SCHEMA) {
            throw new IllegalArgumentException("Unsupported station notice schema " + root.getInt(SCHEMA_KEY));
        }
        ListTag stations = list(root, STATIONS, StationNoticeLimits.MAX_STATIONS);
        StationNoticeBook book = new StationNoticeBook();
        for (Tag element : stations) {
            CompoundTag entry = (CompoundTag) element;
            Set<String> keys = entry.getAllKeys();
            if (!keys.containsAll(ENTRY_REQUIRED) || !ENTRY_KEYS.containsAll(keys) || !uuid(entry, ID)
                    || !entry.contains(REVISION, Tag.TAG_LONG)) {
                throw new IllegalArgumentException("Station notice entry keys or types");
            }
            Map<UUID, Long> members = new HashMap<>();
            for (Tag acknowledgement : list(entry, ACKNOWLEDGEMENTS, StationNoticeLimits.MAX_MEMBER_ACKNOWLEDGEMENTS)) {
                StationNoticeSummary.Acknowledgement decoded = acknowledgement((CompoundTag) acknowledgement);
                if (members.put(decoded.player(), decoded.revision()) != null) {
                    throw new IllegalArgumentException("Duplicate station notice acknowledgement");
                }
            }
            Optional<StationNoticeSummary.Acknowledgement> owner = Optional.empty();
            if (keys.contains(OWNER)) {
                if (!entry.contains(OWNER, Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Station notice owner acknowledgement type");
                }
                owner = Optional.of(acknowledgement(entry.getCompound(OWNER)));
            }
            Optional<StationNotice> outcome = Optional.empty();
            if (keys.contains(OUTCOME)) {
                if (!entry.contains(OUTCOME, Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Station notice outcome type");
                }
                outcome = Optional.of(decodeNotice(entry.getCompound(OUTCOME)));
            }
            book.restore(entry.getUUID(ID), new StationNoticeSummary(entry.getLong(REVISION), outcome, owner, members));
        }
        return book;
    }

    /** One stored outcome; the key must name a stored kind and the envelope must fit its byte budget. */
    static StationNotice decodeNotice(CompoundTag tag) {
        if (!tag.getAllKeys().equals(NOTICE_KEYS) || !tag.contains(KEY, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Station notice envelope keys or types");
        }
        StationNoticeKind kind = StationNoticeKind.byKey(tag.getString(KEY)).filter(StationNoticeKind::stored)
                .orElseThrow(() -> new IllegalArgumentException("Station notice key is not a stored outcome"));
        ListTag arguments = list(tag, ARGUMENTS, kind.roles().size());
        if (arguments.size() != kind.roles().size() || arguments.getElementType() != Tag.TAG_STRING) {
            throw new IllegalArgumentException("Station notice arguments");
        }
        List<String> values = new ArrayList<>(arguments.size());
        arguments.forEach(argument -> values.add(argument.getAsString()));
        StationNotice notice = new StationNotice(kind, values);
        if (envelopeBytes(notice) > StationNoticeLimits.MAX_ENVELOPE_BYTES) {
            throw new IllegalArgumentException("Station notice envelope exceeds its byte budget");
        }
        return notice;
    }

    /** The exact NBT payload of {@link #encode(StationNotice)}: its key, its argument list and the end byte. */
    public static long envelopeBytes(StationNotice notice) {
        long arguments = 1L + Integer.BYTES;
        for (String argument : notice.arguments()) {
            arguments += string(argument);
        }
        return entry(KEY, string(notice.kind().key())) + entry(ARGUMENTS, arguments) + 1;
    }

    private static CompoundTag encode(UUID stationId, StationNoticeSummary summary) {
        CompoundTag entry = new CompoundTag();
        entry.putUUID(ID, stationId);
        entry.putLong(REVISION, summary.revision());
        ListTag members = new ListTag();
        summary.members().entrySet().stream().sorted(Comparator.comparing(member -> member.getKey().toString()))
                .forEach(member -> members.add(acknowledgement(member.getKey(), member.getValue())));
        entry.put(ACKNOWLEDGEMENTS, members);
        summary.outcome().ifPresent(outcome -> entry.put(OUTCOME, encode(outcome)));
        summary.owner().ifPresent(owner -> entry.put(OWNER, acknowledgement(owner.player(), owner.revision())));
        return entry;
    }

    private static CompoundTag acknowledgement(UUID player, long revision) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(ID, player);
        tag.putLong(REVISION, revision);
        return tag;
    }

    private static StationNoticeSummary.Acknowledgement acknowledgement(CompoundTag tag) {
        if (!tag.getAllKeys().equals(ACKNOWLEDGEMENT_KEYS) || !uuid(tag, ID) || !tag.contains(REVISION, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Station notice acknowledgement keys or types");
        }
        return new StationNoticeSummary.Acknowledgement(tag.getUUID(ID), tag.getLong(REVISION));
    }

    /** A list of at most {@code max} elements, all compounds unless it is the argument list. */
    private static ListTag list(CompoundTag tag, String key, int max) {
        if (!tag.contains(key, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("Station notice list " + key);
        }
        ListTag list = (ListTag) tag.get(key);
        int element = key.equals(ARGUMENTS) ? Tag.TAG_STRING : Tag.TAG_COMPOUND;
        if (list.size() > max || !list.isEmpty() && list.getElementType() != element) {
            throw new IllegalArgumentException("Station notice list " + key + " bound or type");
        }
        return list;
    }

    /** A named entry: its type byte, the modified-UTF-8 key with its length, and its payload. */
    private static long entry(String key, long payload) {
        return 1 + string(key) + payload;
    }

    /** A NBT string: an unsigned short length and its modified UTF-8 bytes. */
    private static long string(String value) {
        long length = 2;
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            length += unit >= 0x0001 && unit <= 0x007F ? 1 : unit <= 0x07FF ? 2 : 3;
        }
        return length;
    }

    /** Whether {@code key} holds a UUID in the exact four-int layout of {@link CompoundTag#putUUID}. */
    private static boolean uuid(CompoundTag tag, String key) {
        return tag.contains(key, Tag.TAG_INT_ARRAY) && tag.getIntArray(key).length == 4;
    }
}

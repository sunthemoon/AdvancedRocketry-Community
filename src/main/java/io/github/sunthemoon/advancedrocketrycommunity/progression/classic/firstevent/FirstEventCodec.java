package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The one strict codec of a world-first event, shared by both source outboxes and the progression receipts (W44
 * sections 1.1, 2.1 and 3.1). Every key set is exact, every type strict, and decoding recomputes the fingerprint.
 */
public final class FirstEventCodec {
    static final Set<String> WARP_KEYS = Set.of("kind", "event_id", "station_id", "actor_id", "from_body", "to_body",
            "cost", "orbital_tick", "participants", "fingerprint");
    static final Set<String> MOON_KEYS = Set.of("kind", "event_id", "transfer_id", "logical_rocket_id", "owner_id",
            "terminal_rocket_id", "record_checksum", "level", "terminal_origin", "completed_at_game_time",
            "participants", "fingerprint");

    private FirstEventCodec() {
    }

    public static CompoundTag encode(FirstEvent event) {
        CompoundTag target = new CompoundTag();
        target.putString("kind", event.kind().serializedName());
        target.putUUID("event_id", event.eventId());
        if (event instanceof FirstEvent.StationWarp warp) {
            target.putUUID("station_id", warp.stationId());
            target.putUUID("actor_id", warp.actorId());
            target.putString("from_body", warp.fromBody().toString());
            target.putString("to_body", warp.toBody().toString());
            target.putInt("cost", warp.cost());
            target.putLong("orbital_tick", warp.orbitalTick());
        } else if (event instanceof FirstEvent.MoonLanding moon) {
            target.putUUID("transfer_id", moon.transferId());
            target.putUUID("logical_rocket_id", moon.logicalRocketId());
            target.putUUID("owner_id", moon.ownerId());
            target.putUUID("terminal_rocket_id", moon.terminalRocketId());
            target.putString("record_checksum", moon.recordChecksum());
            target.putString("level", moon.level().toString());
            target.putIntArray("terminal_origin", new int[] {moon.originX(), moon.originY(), moon.originZ()});
            target.putLong("completed_at_game_time", moon.completedAtGameTime());
        }
        target.put("participants", encodeParticipants(event.participants()));
        target.putString("fingerprint", event.fingerprint());
        return target;
    }

    /** Strict decode of one event of {@code expected} kind; any deviation throws {@link IllegalArgumentException}. */
    public static FirstEvent decode(CompoundTag source, FirstEventKind expected) {
        if (source == null) { throw new IllegalArgumentException("Missing first event"); }
        if (!requireString(source, "kind", 64).equals(expected.serializedName())) {
            throw new IllegalArgumentException("First event kind does not match its slot");
        }
        UUID eventId = requireUuid(source, "event_id");
        List<UUID> participants = decodeParticipants(source, expected);
        String fingerprint = requireString(source, "fingerprint", 64);
        if (!fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("First event fingerprint is malformed");
        }
        return switch (expected) {
            case FIRST_STATION_WARP -> {
                requireKeys(source, WARP_KEYS);
                yield new FirstEvent.StationWarp(eventId, requireUuid(source, "station_id"),
                        requireUuid(source, "actor_id"), requireBody(source, "from_body"),
                        requireBody(source, "to_body"), requireInt(source, "cost"),
                        requireLong(source, "orbital_tick"), participants, fingerprint);
            }
            case FIRST_MOON_LANDING -> {
                requireKeys(source, MOON_KEYS);
                if (!source.contains("terminal_origin", Tag.TAG_INT_ARRAY)
                        || source.getIntArray("terminal_origin").length != 3) {
                    throw new IllegalArgumentException("First Moon landing origin must be exactly three ints");
                }
                int[] origin = source.getIntArray("terminal_origin");
                ResourceLocation level = ResourceLocation.tryParse(requireString(source, "level", 128));
                yield new FirstEvent.MoonLanding(eventId, requireUuid(source, "transfer_id"),
                        requireUuid(source, "logical_rocket_id"), requireUuid(source, "owner_id"),
                        requireUuid(source, "terminal_rocket_id"), requireString(source, "record_checksum", 64),
                        level, origin[0], origin[1], origin[2], requireLong(source, "completed_at_game_time"),
                        participants, fingerprint);
            }
        };
    }

    static IntArrayTag encodeParticipants(List<UUID> participants) {
        int[] values = new int[participants.size() * 4];
        for (int index = 0; index < participants.size(); index++) {
            System.arraycopy(UUIDUtil.uuidToIntArray(participants.get(index)), 0, values, index * 4, 4);
        }
        return new IntArrayTag(values);
    }

    /** 4n ints, n within the kind's bound; strict ascending order is enforced by the event constructor. */
    private static List<UUID> decodeParticipants(CompoundTag source, FirstEventKind kind) {
        if (!source.contains("participants", Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("Missing first event participants");
        }
        int[] values = source.getIntArray("participants");
        if (values.length % 4 != 0 || values.length / 4 > kind.maxParticipants()) {
            throw new IllegalArgumentException("First event participants are malformed or exceed their bound");
        }
        List<UUID> participants = new ArrayList<>(values.length / 4);
        for (int offset = 0; offset < values.length; offset += 4) {
            participants.add(UUIDUtil.uuidFromIntArray(new int[] {
                    values[offset], values[offset + 1], values[offset + 2], values[offset + 3]}));
        }
        return participants;
    }

    static void requireKeys(CompoundTag source, Set<String> keys) {
        if (!source.getAllKeys().equals(keys)) {
            throw new IllegalArgumentException("First event keys are not exact");
        }
    }

    private static UUID requireUuid(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT_ARRAY) || source.getIntArray(key).length != 4) {
            throw new IllegalArgumentException("Missing first event UUID " + key);
        }
        return source.getUUID(key);
    }

    private static int requireInt(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT)) { throw new IllegalArgumentException("Missing first event int " + key); }
        return source.getInt(key);
    }

    private static long requireLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) { throw new IllegalArgumentException("Missing first event long " + key); }
        return source.getLong(key);
    }

    private static String requireString(CompoundTag source, String key, int maximumLength) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing first event string " + key);
        }
        String value = source.getString(key);
        if (value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalArgumentException("First event string " + key + " is outside its bound");
        }
        return value;
    }

    private static ResourceLocation requireBody(CompoundTag source, String key) {
        ResourceLocation body = ResourceLocation.tryParse(requireString(source, key,
                FirstEvent.StationWarp.MAX_BODY_LENGTH));
        if (body == null) { throw new IllegalArgumentException("First event body " + key + " is malformed"); }
        return body;
    }
}

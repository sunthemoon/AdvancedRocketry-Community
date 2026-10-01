package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Strict NBT for ADR-054 section 11: transit records in the endgame root, and the outbox entries, incoming payloads
 * and receipt keys an endpoint keeps in its own root. A missing, mistyped or unknown field, or a record over its
 * 2.5 KiB bound, is an error, never a default.
 */
public final class TransitCodec {
    private static final Set<String> RECORD_KEYS = Set.of("source", "seq", "system", "owner", "destination",
            "payload", "paid_fe", "dispatch_epoch", "arrive_at", "state", "paid_endpoint", "acknowledged", "ack_epoch",
            "redirected");
    private static final Set<String> ENTRY_KEYS = Set.of("seq", "destination", "payload", "paid_fe", "travel",
            "system");
    private static final Set<String> KEY_KEYS = Set.of("source", "seq");
    private static final Set<String> INCOMING_KEYS = Set.of("source", "seq", "payload");

    private TransitCodec() {
    }

    public static CompoundTag encodeRecord(TransitRecord record) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("source", record.key().source());
        tag.putLong("seq", record.key().seq());
        tag.putString("system", record.system().id());
        tag.putUUID("owner", record.owner());
        tag.putUUID("destination", record.destination());
        if (record.payload() != null) {
            tag.put("payload", record.payload().tag());
        }
        tag.putInt("paid_fe", record.paidFe());
        tag.putLong("dispatch_epoch", record.dispatchEpoch());
        tag.putLong("arrive_at", record.arriveAt());
        tag.putString("state", record.state().name());
        if (record.paidEndpoint() != null) {
            tag.putUUID("paid_endpoint", record.paidEndpoint());
        }
        tag.putBoolean("acknowledged", record.acknowledged());
        tag.putLong("ack_epoch", record.ackEpoch());
        tag.putBoolean("redirected", record.redirected());
        if (EndgameNbt.uncompressedBytes(tag) > EndgameLimits.TRANSIT_RECORD_BYTES) {
            throw new IllegalStateException("A transit record exceeds its 2.5 KiB bound");
        }
        return tag;
    }

    public static TransitRecord decodeRecord(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, RECORD_KEYS, "transit record");
        if (EndgameNbt.uncompressedBytes(tag) > EndgameLimits.TRANSIT_RECORD_BYTES) {
            throw new IllegalArgumentException("A transit record exceeds its 2.5 KiB bound");
        }
        TransitRecord.State state = switch (EndgameNbt.requireString(tag, "state", 16)) {
            case "IN_TRANSIT" -> TransitRecord.State.IN_TRANSIT;
            case "ARRIVED" -> TransitRecord.State.ARRIVED;
            case "CLAIMED" -> TransitRecord.State.CLAIMED;
            case "QUARANTINED" -> TransitRecord.State.QUARANTINED;
            default -> throw new IllegalArgumentException("Unknown transit state");
        };
        TransitPayload payload = tag.contains("payload")
                ? TransitPayload.raw(EndgameNbt.requireList(tag, "payload", Tag.TAG_COMPOUND, TransitPayload.MAX_STACKS))
                : null;
        UUID paid = tag.contains("paid_endpoint") ? EndgameNbt.requireUuid(tag, "paid_endpoint") : null;
        return new TransitRecord(new TransitKey(EndgameNbt.requireUuid(tag, "source"), EndgameNbt.requireLong(tag,
                "seq")), system(tag), EndgameNbt.requireUuid(tag, "owner"), EndgameNbt.requireUuid(tag, "destination"),
                payload, EndgameNbt.requireInt(tag, "paid_fe"), EndgameNbt.requireLong(tag, "dispatch_epoch"),
                EndgameNbt.requireLong(tag, "arrive_at"), state, paid, EndgameNbt.requireBoolean(tag, "acknowledged"),
                EndgameNbt.requireLong(tag, "ack_epoch"), EndgameNbt.requireBoolean(tag, "redirected"));
    }

    public static CompoundTag encodeEntry(OutboxEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("seq", entry.seq());
        tag.putUUID("destination", entry.destination());
        tag.put("payload", entry.payload().tag());
        tag.putInt("paid_fe", entry.paidFe());
        tag.putInt("travel", entry.travel());
        tag.putString("system", entry.system().id());
        return tag;
    }

    public static OutboxEntry decodeEntry(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, ENTRY_KEYS, "outbox entry");
        return new OutboxEntry(EndgameNbt.requireLong(tag, "seq"), EndgameNbt.requireUuid(tag, "destination"),
                TransitPayload.raw(EndgameNbt.requireList(tag, "payload", Tag.TAG_COMPOUND, TransitPayload.MAX_STACKS)),
                EndgameNbt.requireInt(tag, "paid_fe"), EndgameNbt.requireInt(tag, "travel"), system(tag));
    }

    public static CompoundTag encodeKey(TransitKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("source", key.source());
        tag.putLong("seq", key.seq());
        return tag;
    }

    public static TransitKey decodeKey(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, KEY_KEYS, "transfer key");
        return new TransitKey(EndgameNbt.requireUuid(tag, "source"), EndgameNbt.requireLong(tag, "seq"));
    }

    public static CompoundTag encodeIncoming(TransitKey key, TransitPayload payload) {
        CompoundTag tag = encodeKey(key);
        tag.put("payload", payload.tag());
        return tag;
    }

    /** An incoming payload: its key, read with {@link #decodeKey} rules, and its raw payload. */
    public static IncomingPayload decodeIncoming(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, INCOMING_KEYS, "incoming payload");
        return new IncomingPayload(new TransitKey(EndgameNbt.requireUuid(tag, "source"),
                EndgameNbt.requireLong(tag, "seq")),
                TransitPayload.raw(EndgameNbt.requireList(tag, "payload", Tag.TAG_COMPOUND, TransitPayload.MAX_STACKS)));
    }

    public record IncomingPayload(TransitKey key, TransitPayload payload) {
    }

    private static EndgameSystem system(CompoundTag tag) {
        EndgameSystem system = EndgameSystem.byId(EndgameNbt.requireString(tag, "system", 32))
                .orElseThrow(() -> new IllegalArgumentException("Unknown transit system"));
        if (system != EndgameSystem.RAILGUN && system != EndgameSystem.SPACE_ELEVATOR) {
            throw new IllegalArgumentException("Only railgun and elevator cargo uses the ledger");
        }
        return system;
    }

    /** A list of encoded items, checked for its element type and size. */
    public static ListTag requireList(CompoundTag source, String key, int maxSize) {
        return EndgameNbt.requireList(source, key, Tag.TAG_COMPOUND, maxSize);
    }
}

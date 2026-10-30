package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** The transaction journal's schema 2 entry codec, moved unchanged out of {@link RocketTransactionSavedData}. */
final class RocketTransactionCodec {
    private RocketTransactionCodec() { }

    static CompoundTag encodeEntry(RocketPersistedTransaction entry) {
        CompoundTag data = encodeMetadata(entry);
        data.put("snapshot", RocketSnapshotNbtCodec.encode(entry.snapshot()));
        return data;
    }

    /** Shared exact record framing for encoding and same-snapshot admission-size updates. */
    static CompoundTag encodeMetadata(RocketPersistedTransaction entry) {
        RocketTransactionRecord record = entry.record();
        CompoundTag data = new CompoundTag();
        data.putUUID("transaction_id", record.transactionId());
        data.putString("type", record.type().name());
        data.putString("phase", record.phase().name());
        data.putUUID("snapshot_id", record.snapshotId());
        data.putString("content_hash", record.contentHash());
        data.putString("dimension", record.region().dimension().toString());
        data.putIntArray("minimum", positionArray(record.region().minimum()));
        data.putIntArray("maximum", positionArray(record.region().maximum()));
        data.putInt("progress", record.progress());
        record.rocketEntityIdOptional().ifPresent(id -> data.putUUID("rocket_entity_id", id));
        data.putUUID("owner_id", entry.ownerId());
        return data;
    }

    static RocketPersistedTransaction decodeEntry(CompoundTag data) {
        UUID transactionId = requireUuid(data, "transaction_id");
        RocketTransactionType type = RocketTransactionType.valueOf(requireString(data, "type"));
        RocketTransactionPhase phase = RocketTransactionPhase.valueOf(requireString(data, "phase"));
        UUID snapshotId = requireUuid(data, "snapshot_id");
        String contentHash = requireString(data, "content_hash");
        if (!contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Transaction content hash is invalid");
        }
        ResourceLocation dimension = ResourceLocation.tryParse(requireString(data, "dimension"));
        if (dimension == null) {
            throw new IllegalArgumentException("Transaction dimension is invalid");
        }
        RocketRegion region = new RocketRegion(
                dimension,
                requirePosition(data, "minimum"),
                requirePosition(data, "maximum")
        );
        int progress = requireInt(data, "progress");
        UUID rocketEntityId = data.hasUUID("rocket_entity_id")
                ? data.getUUID("rocket_entity_id")
                : null;
        UUID ownerId = requireUuid(data, "owner_id");
        if (!data.contains("snapshot", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Transaction snapshot is missing");
        }
        RocketSnapshotDecodeResult decoded = RocketSnapshotNbtCodec.decode(data.getCompound("snapshot"));
        if (decoded.status() != RocketSnapshotDecodeResult.Status.VALID) {
            throw new IllegalArgumentException("Transaction snapshot is not valid: " + decoded.message());
        }
        RocketStructureSnapshot snapshot = decoded.snapshot().orElseThrow();
        RocketTransactionRecord record = new RocketTransactionRecord(
                transactionId,
                type,
                phase,
                snapshotId,
                contentHash,
                region,
                progress,
                rocketEntityId
        );
        if (!RocketRegion.fromSnapshot(snapshot).equals(region)) {
            throw new IllegalArgumentException("Transaction region does not match snapshot bounds");
        }
        return new RocketPersistedTransaction(record, snapshot, ownerId);
    }

    private static UUID requireUuid(CompoundTag data, String key) {
        if (!data.hasUUID(key)) {
            throw new IllegalArgumentException("Missing transaction UUID " + key);
        }
        return data.getUUID(key);
    }

    private static String requireString(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing transaction string " + key);
        }
        return data.getString(key);
    }

    private static int requireInt(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing transaction integer " + key);
        }
        int value = data.getInt(key);
        if (value < 0) {
            throw new IllegalArgumentException("Negative transaction integer " + key);
        }
        return value;
    }

    private static RocketPosition requirePosition(CompoundTag data, String key) {
        if (!data.contains(key, Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("Missing transaction position " + key);
        }
        int[] position = data.getIntArray(key);
        if (position.length != 3) {
            throw new IllegalArgumentException("Transaction position has the wrong length");
        }
        return new RocketPosition(position[0], position[1], position[2]);
    }

    private static int[] positionArray(RocketPosition position) {
        return new int[]{position.x(), position.y(), position.z()};
    }
}

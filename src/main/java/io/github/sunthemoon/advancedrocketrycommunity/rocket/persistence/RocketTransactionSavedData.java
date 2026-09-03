package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-attached versioned journal for same- and cross-level rocket recovery. */
public final class RocketTransactionSavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_rocket_transactions";
    public static final int SCHEMA_VERSION = 2;

    private final Map<UUID, RocketPersistedTransaction> entries = new LinkedHashMap<>();
    private CompoundTag preservedBlockedData;

    public static RocketTransactionSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                RocketTransactionSavedData::load,
                RocketTransactionSavedData::new,
                DATA_NAME
        );
    }

    public static RocketTransactionSavedData load(CompoundTag source) {
        RocketTransactionSavedData data = new RocketTransactionSavedData();
        try {
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    ManagedSavedDataType.ROCKET_TRANSACTIONS,
                    source
            );
            if (migration.status() == SavedDataSchemaMigrator.MigrationStatus.FUTURE) {
                data.preservedBlockedData = source.copy();
                return data;
            }
            CompoundTag payload = migration.payload();
            Tag rawTransactions = payload.get("transactions");
            if (!(rawTransactions instanceof ListTag transactions)
                    || (!transactions.isEmpty() && transactions.getElementType() != Tag.TAG_COMPOUND)
                    || transactions.size() > RocketLimits.MAX_ACTIVE_TRANSACTIONS) {
                throw new IllegalArgumentException("Rocket transaction list is invalid or oversized");
            }
            for (Tag raw : transactions) {
                if (!(raw instanceof CompoundTag entryTag)) {
                    throw new IllegalArgumentException("Transaction entry has the wrong type");
                }
                RocketPersistedTransaction entry = decodeEntry(entryTag);
                if (data.entries.put(entry.record().transactionId(), entry) != null) {
                    throw new IllegalArgumentException("Duplicate rocket transaction ID");
                }
            }
            if (migration.changed()) {
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.entries.clear();
            data.preservedBlockedData = source.copy();
        }
        return data;
    }

    public boolean operational() {
        return preservedBlockedData == null;
    }

    public Optional<CompoundTag> preservedBlockedData() {
        return preservedBlockedData == null
                ? Optional.empty()
                : Optional.of(preservedBlockedData.copy());
    }

    public List<RocketPersistedTransaction> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(entry -> entry.record().transactionId()))
                .toList();
    }

    public RocketTransactionJournal journalFor(
            RocketStructureSnapshot snapshot,
            UUID ownerId
    ) {
        if (!operational()) {
            throw new IllegalStateException("Rocket transaction journal contains unsupported or invalid data");
        }
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(ownerId, "ownerId");
        return new BoundJournal(snapshot, ownerId);
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.ROCKET_TRANSACTIONS, target);
        ListTag transactions = new ListTag();
        entries().forEach(entry -> transactions.add(encodeEntry(entry)));
        target.put("transactions", transactions);
        return target;
    }

    private void write(
            RocketTransactionRecord record,
            RocketStructureSnapshot snapshot,
            UUID ownerId
    ) {
        if (!record.snapshotId().equals(snapshot.snapshotId())
                || !record.contentHash().equals(snapshot.contentHash())) {
            throw new IllegalArgumentException("Journal record does not match bound snapshot");
        }
        RocketPersistedTransaction existing = entries.get(record.transactionId());
        if (existing == null && entries.size() >= RocketLimits.MAX_ACTIVE_TRANSACTIONS) {
            throw new IllegalStateException("Rocket transaction journal is full");
        }
        if (existing != null
                && (!existing.snapshot().snapshotId().equals(snapshot.snapshotId())
                || !existing.ownerId().equals(ownerId)
                || existing.record().type() != record.type())) {
            throw new IllegalArgumentException("Transaction ID was rebound to different authority data");
        }
        entries.put(record.transactionId(), new RocketPersistedTransaction(record, snapshot, ownerId));
        setDirty();
    }

    private void remove(UUID transactionId) {
        if (entries.remove(transactionId) != null) {
            setDirty();
        }
    }

    private static CompoundTag encodeEntry(RocketPersistedTransaction entry) {
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
        data.put("snapshot", RocketSnapshotNbtCodec.encode(entry.snapshot()));
        return data;
    }

    private static RocketPersistedTransaction decodeEntry(CompoundTag data) {
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

    private final class BoundJournal implements RocketTransactionJournal {
        private final RocketStructureSnapshot snapshot;
        private final UUID ownerId;

        private BoundJournal(RocketStructureSnapshot snapshot, UUID ownerId) {
            this.snapshot = snapshot;
            this.ownerId = ownerId;
        }

        @Override
        public void write(RocketTransactionRecord record) {
            RocketTransactionSavedData.this.write(record, snapshot, ownerId);
        }

        @Override
        public void remove(UUID transactionId) {
            RocketTransactionSavedData.this.remove(transactionId);
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Overworld-owned, strict versioned Earth/Moon transfer journal. */
public final class RocketTransferSavedData extends SavedData {
    public static final String DATA_NAME = "advancedrocketrycommunity_rocket_transfers";
    public static final int ROOT_SCHEMA_VERSION = 2;

    private final Map<UUID, RocketTransferRecord> entries = new LinkedHashMap<>();
    private CompoundTag preservedBlockedData;

    public static RocketTransferSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(
                RocketTransferSavedData::load,
                RocketTransferSavedData::new,
                DATA_NAME
        );
    }

    public static RocketTransferSavedData load(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        RocketTransferSavedData data = new RocketTransferSavedData();
        CompoundTag preserved = source.copy();
        try {
            if (RocketNbtSize.uncompressedBytes(source)
                    > RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES) {
                throw new IllegalArgumentException("Transfer journal exceeds the fixed NBT limit");
            }
            SavedDataSchemaMigrator.MigrationResult migration = SavedDataSchemaMigrator.migrate(
                    ManagedSavedDataType.ROCKET_TRANSFERS,
                    source
            );
            if (migration.status() == SavedDataSchemaMigrator.MigrationStatus.FUTURE) {
                throw new IllegalArgumentException("Transfer journal uses a future root schema");
            }
            CompoundTag payload = migration.payload();
            ListTag transfers = requireList(payload, "transfers", Tag.TAG_COMPOUND);
            if (transfers.size() > RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
                throw new IllegalArgumentException("Transfer journal exceeds the active-record bound");
            }
            for (Tag raw : transfers) {
                RocketTransferRecord record = decodeRecord((CompoundTag) raw);
                data.insertValidated(record);
            }
            if (migration.changed()) {
                data.setDirty();
            }
        } catch (RuntimeException exception) {
            data.entries.clear();
            data.preservedBlockedData = preserved;
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

    public List<RocketTransferRecord> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparing(RocketTransferRecord::transferId))
                .toList();
    }

    public Optional<RocketTransferRecord> find(UUID transferId) {
        return Optional.ofNullable(entries.get(Objects.requireNonNull(transferId, "transferId")));
    }

    public Optional<RocketTransferRecord> findByLogicalRocket(UUID logicalRocketId) {
        Objects.requireNonNull(logicalRocketId, "logicalRocketId");
        return entries.values().stream()
                .filter(record -> record.logicalRocketId().equals(logicalRocketId))
                .findFirst();
    }

    public void put(RocketTransferRecord record) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        Objects.requireNonNull(record, "record");
        CompoundTag encoded = encodeRecord(record);
        if (RocketNbtSize.uncompressedBytes(encoded) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Transfer record exceeds the fixed NBT limit");
        }
        RocketTransferRecord existing = entries.get(record.transferId());
        if (existing == null && entries.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            throw new IllegalStateException("Transfer journal is full");
        }
        if (existing != null) {
            if (!existing.logicalRocketId().equals(record.logicalRocketId())
                    || !existing.checksum().equals(record.checksum())
                    || record.phase().ordinal() < existing.phase().ordinal()) {
                throw new IllegalArgumentException("Transfer ID was rebound or moved backwards");
            }
        } else {
            ensureNoAuthorityConflict(record);
        }
        entries.put(record.transferId(), record);
        setDirty();
    }

    public void remove(UUID transferId) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        if (entries.remove(Objects.requireNonNull(transferId, "transferId")) != null) {
            setDirty();
        }
    }

    /** Atomically replaces a committed landed reservation with its return flight. */
    public void replace(UUID previousTransferId, RocketTransferRecord replacement) {
        if (!operational()) {
            throw new IllegalStateException("Transfer journal is blocked by unsupported or invalid data");
        }
        Objects.requireNonNull(previousTransferId, "previousTransferId");
        Objects.requireNonNull(replacement, "replacement");
        RocketTransferRecord previous = entries.get(previousTransferId);
        if (previous == null
                || previous.phase() != RocketTransferPhase.COMMITTED
                || !previous.logicalRocketId().equals(replacement.logicalRocketId())) {
            throw new IllegalArgumentException("Only a committed landed reservation may be replaced");
        }
        entries.remove(previousTransferId);
        try {
            CompoundTag encoded = encodeRecord(replacement);
            if (RocketNbtSize.uncompressedBytes(encoded) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
                throw new IllegalArgumentException("Replacement transfer record exceeds the fixed NBT limit");
            }
            insertValidated(replacement);
        } catch (RuntimeException exception) {
            entries.put(previousTransferId, previous);
            throw exception;
        }
        setDirty();
    }

    /** Synchronously persists each authority boundary rather than waiting for autosave. */
    public void flush(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (isDirty()) {
            server.overworld().getDataStorage().save();
        }
    }

    @Override
    public CompoundTag save(CompoundTag target) {
        if (preservedBlockedData != null) {
            return preservedBlockedData.copy();
        }
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.ROCKET_TRANSFERS, target);
        ListTag transfers = new ListTag();
        entries().forEach(record -> transfers.add(encodeRecord(record)));
        target.put("transfers", transfers);
        if (RocketNbtSize.uncompressedBytes(target) > RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES) {
            throw new IllegalStateException("Encoded transfer journal exceeds the fixed NBT limit");
        }
        return target;
    }

    private void insertValidated(RocketTransferRecord record) {
        if (entries.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) {
            throw new IllegalArgumentException("Transfer journal exceeds the active-record bound");
        }
        if (entries.put(record.transferId(), record) != null) {
            throw new IllegalArgumentException("Transfer journal contains a duplicate transaction ID");
        }
        try {
            ensureNoAuthorityConflict(record);
        } catch (RuntimeException exception) {
            entries.remove(record.transferId());
            throw exception;
        }
    }

    private void ensureNoAuthorityConflict(RocketTransferRecord candidate) {
        RocketRegion destination = RocketRegion.fromSnapshot(candidate.destinationSnapshot());
        for (RocketTransferRecord existing : entries.values()) {
            if (existing.transferId().equals(candidate.transferId())) {
                continue;
            }
            if (existing.logicalRocketId().equals(candidate.logicalRocketId())) {
                throw new IllegalArgumentException("Logical rocket already has an active transfer");
            }
            if (RocketRegion.fromSnapshot(existing.destinationSnapshot()).overlaps(destination)) {
                throw new IllegalArgumentException("Transfer destination overlaps an active reservation");
            }
        }
    }

    private static CompoundTag encodeRecord(RocketTransferRecord record) {
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", record.schemaVersion());
        target.putUUID("transfer_id", record.transferId());
        target.putString("phase", record.phase().name());
        target.putUUID("logical_rocket_id", record.logicalRocketId());
        target.putUUID("owner_id", record.ownerId());
        target.putUUID("source_entity_id", record.sourceEntityId());
        record.destinationEntityId().ifPresent(id -> target.putUUID("destination_entity_id", id));
        target.put("source_snapshot", RocketSnapshotNbtCodec.encode(record.sourceSnapshot()));
        target.put("destination_snapshot", RocketSnapshotNbtCodec.encode(record.destinationSnapshot()));
        target.put("source_flight", RocketFlightNbtCodec.encode(record.sourceFlightData()));
        target.put("destination_flight", RocketFlightNbtCodec.encode(record.destinationFlightData()));
        target.putLong("required_fuel", record.requiredFuel());
        target.putLong("created_at_game_time", record.createdAtGameTime());
        target.putString("checksum", record.checksum());
        return target;
    }

    private static RocketTransferRecord decodeRecord(CompoundTag source) {
        if (RocketNbtSize.uncompressedBytes(source) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Transfer record exceeds the fixed NBT limit");
        }
        int schema = requireInt(source, "schema_version");
        UUID transferId = requireUuid(source, "transfer_id");
        RocketTransferPhase phase = RocketTransferPhase.valueOf(requireString(source, "phase", 64));
        UUID logicalRocketId = requireUuid(source, "logical_rocket_id");
        UUID ownerId = requireUuid(source, "owner_id");
        UUID sourceEntityId = requireUuid(source, "source_entity_id");
        UUID destinationEntityId = source.contains("destination_entity_id")
                ? requireUuid(source, "destination_entity_id")
                : null;
        RocketStructureSnapshot sourceSnapshot = decodeSnapshot(
                requireCompound(source, "source_snapshot"),
                "source"
        );
        RocketStructureSnapshot destinationSnapshot = decodeSnapshot(
                requireCompound(source, "destination_snapshot"),
                "destination"
        );
        RocketFlightData sourceFlight = decodeFlight(requireCompound(source, "source_flight"), "source");
        RocketFlightData destinationFlight = decodeFlight(
                requireCompound(source, "destination_flight"),
                "destination"
        );
        long requiredFuel = requireNonNegativeLong(source, "required_fuel");
        long createdAt = requireNonNegativeLong(source, "created_at_game_time");
        String checksum = requireString(source, "checksum", 64);
        if (!checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Transfer checksum is malformed");
        }
        return RocketTransferRecord.restore(
                schema,
                transferId,
                phase,
                logicalRocketId,
                ownerId,
                sourceEntityId,
                destinationEntityId,
                sourceSnapshot,
                destinationSnapshot,
                sourceFlight,
                destinationFlight,
                requiredFuel,
                createdAt,
                checksum
        );
    }

    private static RocketStructureSnapshot decodeSnapshot(CompoundTag source, String side) {
        RocketSnapshotDecodeResult decoded = RocketSnapshotNbtCodec.decode(source);
        if (decoded.status() != RocketSnapshotDecodeResult.Status.VALID) {
            throw new IllegalArgumentException(side + " transfer snapshot is invalid: " + decoded.message());
        }
        return decoded.snapshot().orElseThrow();
    }

    private static RocketFlightData decodeFlight(CompoundTag source, String side) {
        RocketFlightDecodeResult decoded = RocketFlightNbtCodec.decode(source);
        if (decoded.status() != RocketFlightDecodeResult.Status.VALID) {
            throw new IllegalArgumentException(side + " transfer flight data is invalid: " + decoded.message());
        }
        return decoded.data().orElseThrow();
    }

    private static int requireInt(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing transfer integer " + key);
        }
        return source.getInt(key);
    }

    private static long requireNonNegativeLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing transfer long " + key);
        }
        long value = source.getLong(key);
        if (value < 0L) {
            throw new IllegalArgumentException("Negative transfer long " + key);
        }
        return value;
    }

    private static UUID requireUuid(CompoundTag source, String key) {
        if (!source.hasUUID(key)) {
            throw new IllegalArgumentException("Missing transfer UUID " + key);
        }
        return source.getUUID(key);
    }

    private static String requireString(CompoundTag source, String key, int maximumLength) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing transfer string " + key);
        }
        String value = source.getString(key);
        if (value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Transfer string " + key + " is outside its bound");
        }
        return value;
    }

    private static CompoundTag requireCompound(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing transfer compound " + key);
        }
        return source.getCompound(key);
    }

    private static ListTag requireList(CompoundTag source, String key, byte elementType) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != elementType)) {
            throw new IllegalArgumentException("Missing or invalid transfer list " + key);
        }
        return list;
    }
}

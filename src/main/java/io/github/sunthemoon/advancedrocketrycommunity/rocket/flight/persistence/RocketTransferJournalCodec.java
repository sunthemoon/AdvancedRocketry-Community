package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict record/root NBT codec, independent of writer and lifecycle state. */
final class RocketTransferJournalCodec {
    private RocketTransferJournalCodec() { }
    static CompoundTag encodeRoot(List<RocketTransferRecord> records, FirstEventOutbox firstMoonLanding,
                                  CompoundTag target) {
        return encodeRoot(records, firstMoonLanding, target, RocketTransferJournalCodec::encodeRecord);
    }

    static CompoundTag encodeRoot(List<RocketTransferRecord> records, FirstEventOutbox firstMoonLanding,
                                  CompoundTag target, java.util.function.Function<RocketTransferRecord, CompoundTag> encoder) {
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.ROCKET_TRANSFERS, target);
        ListTag transfers = new ListTag();
        records.stream().sorted(Comparator.comparing(RocketTransferRecord::transferId)).forEach(record -> {
            CompoundTag encoded = encoder.apply(record);
            if (RocketNbtSize.uncompressedBytes(encoded) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
                throw new IllegalStateException("Encoded transfer record exceeds the fixed NBT limit");
            }
            transfers.add(encoded);
        });
        target.put("transfers", transfers);
        target.put(RocketTransferSavedData.FIRST_MOON_LANDING, firstMoonLanding.encode(FirstEventKind.FIRST_MOON_LANDING));
        if (RocketNbtSize.uncompressedBytes(target) > RocketFlightLimits.MAX_TRANSFER_JOURNAL_NBT_BYTES) {
            throw new IllegalStateException("Encoded transfer journal exceeds the fixed NBT limit");
        }
        return target;
    }

    static CompoundTag encodeRecord(RocketTransferRecord record) {
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
        record.nativeTransfer().ifPresent(value -> target.put("native_transfer", value.encode()));
        return target;
    }

    static RocketTransferRecord decodeRecord(CompoundTag source) {
        if (RocketNbtSize.uncompressedBytes(source) > RocketFlightLimits.MAX_TRANSFER_RECORD_NBT_BYTES) {
            throw new IllegalArgumentException("Transfer record exceeds the fixed NBT limit");
        }
        int schema = requireInt(source, "schema_version");
        if (schema < 1 || schema > RocketTransferRecord.GOVERNED_SCHEMA_VERSION
                || source.contains("native_transfer") != (schema == RocketTransferRecord.GOVERNED_SCHEMA_VERSION)) {
            throw new IllegalArgumentException("Unsupported transfer record/envelope version");
        }
        if (schema == 3 && !java.util.Set.of("schema_version", "transfer_id", "phase", "logical_rocket_id",
                "owner_id", "source_entity_id", "destination_entity_id", "source_snapshot", "destination_snapshot",
                "source_flight", "destination_flight", "required_fuel", "created_at_game_time", "checksum", "native_transfer")
                .containsAll(source.getAllKeys())) {
            throw new IllegalArgumentException("Unknown governed transfer field");
        }
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
        if (schema == 3) {
            var envelope = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTransferEnvelope
                    .decode(requireCompound(source, "native_transfer"));
            return RocketTransferRecord.restoreGoverned(transferId, phase, logicalRocketId, ownerId, sourceEntityId,
                    destinationEntityId, sourceSnapshot, destinationSnapshot, sourceFlight, destinationFlight,
                    requiredFuel, createdAt, checksum, envelope);
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

    static ListTag requireList(CompoundTag source, String key, byte elementType) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != elementType)) {
            throw new IllegalArgumentException("Missing or invalid transfer list " + key);
        }
        return list;
    }
}

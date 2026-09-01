package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Canonical checksum over immutable transfer authority data. */
final class RocketTransferChecksum {
    private RocketTransferChecksum() {
    }

    static String compute(
            int schemaVersion,
            UUID transferId,
            UUID logicalRocketId,
            UUID ownerId,
            UUID sourceEntityId,
            RocketStructureSnapshot sourceSnapshot,
            RocketStructureSnapshot destinationSnapshot,
            RocketFlightData sourceFlightData,
            RocketFlightData destinationFlightData,
            long requiredFuel,
            long createdAtGameTime
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(schemaVersion);
                writeUuid(output, transferId);
                writeUuid(output, logicalRocketId);
                writeUuid(output, ownerId);
                writeUuid(output, sourceEntityId);
                writeSnapshot(output, sourceSnapshot);
                writeSnapshot(output, destinationSnapshot);
                writeFlight(output, sourceFlightData);
                writeFlight(output, destinationFlightData);
                output.writeLong(requiredFuel);
                output.writeLong(createdAtGameTime);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(Character.forDigit((value >>> 4) & 0xF, 16));
                result.append(Character.forDigit(value & 0xF, 16));
            }
            return result.toString();
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to checksum rocket transfer", exception);
        }
    }

    private static void writeSnapshot(DataOutputStream output, RocketStructureSnapshot snapshot)
            throws IOException {
        writeUuid(output, snapshot.snapshotId());
        writeString(output, snapshot.contentHash());
        writeString(output, snapshot.sourceDimension().toString());
        writePosition(output, snapshot.sourceOrigin());
        output.writeLong(snapshot.createdAtGameTime());
    }

    private static void writeFlight(DataOutputStream output, RocketFlightData data) throws IOException {
        output.writeInt(data.schemaVersion());
        writeUuid(output, data.logicalRocketId());
        writeString(output, data.state().name());
        output.writeLong(data.fuel().capacity());
        output.writeLong(data.fuel().amount());
        output.writeInt(data.fuel().committedDebits().size());
        for (UUID debit : data.fuel().committedDebits()) {
            writeUuid(output, debit);
        }
        output.writeBoolean(data.plan().isPresent());
        if (data.plan().isPresent()) {
            RocketFlightPlan plan = data.plan().orElseThrow();
            output.writeInt(plan.schemaVersion());
            writeUuid(output, plan.requestId());
            writeString(output, plan.sourceBody().toString());
            writeString(output, plan.destinationBody().toString());
            writeString(output, plan.sourceDimension().toString());
            writeString(output, plan.destinationDimension().toString());
            if (plan.schemaVersion() >= 2) {
                output.writeBoolean(plan.destinationStation().isPresent());
                if (plan.destinationStation().isPresent()) {
                    writeUuid(output, plan.destinationStation().orElseThrow());
                }
            }
            output.writeLong(plan.requiredFuel());
            output.writeLong(plan.createdAtGameTime());
        }
        output.writeInt(data.passengers().seatCapacity());
        output.writeInt(data.passengers().assignments().size());
        for (RocketPassengerSeat passenger : data.passengers().assignments()) {
            writeUuid(output, passenger.passengerId());
            output.writeInt(passenger.seatIndex());
        }
        writeString(output, data.currentBody().toString());
        writeString(output, data.currentDimension().toString());
        writePosition(output, data.currentOrigin());
        output.writeLong(data.stateStartedGameTime());
        output.writeBoolean(data.activeTransferId().isPresent());
        if (data.activeTransferId().isPresent()) {
            writeUuid(output, data.activeTransferId().orElseThrow());
        }
    }

    private static void writePosition(DataOutputStream output, RocketPosition position) throws IOException {
        output.writeInt(position.x());
        output.writeInt(position.y());
        output.writeInt(position.z());
    }

    private static void writeUuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }
}

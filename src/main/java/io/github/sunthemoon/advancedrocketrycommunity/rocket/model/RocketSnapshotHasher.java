package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

final class RocketSnapshotHasher {
    private RocketSnapshotHasher() {
    }

    static String hash(
            int schemaVersion,
            ResourceLocation sourceDimension,
            RocketPosition sourceOrigin,
            RocketBounds bounds,
            List<RocketBlock> blocks,
            List<RocketPosition> passengerAnchors,
            RocketStats stats
    ) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(schemaVersion);
                writeString(output, sourceDimension.toString());
                writePosition(output, sourceOrigin);
                writePosition(output, bounds.minimum());
                writePosition(output, bounds.maximum());
                output.writeInt(blocks.size());
                for (RocketBlock block : blocks) {
                    writePosition(output, block.position());
                    writeString(output, block.state().blockId().toString());
                    output.writeInt(block.state().properties().size());
                    for (var property : block.state().properties().entrySet()) {
                        writeString(output, property.getKey());
                        writeString(output, property.getValue());
                    }
                    if (block.blockEntityPayload().isPresent()) {
                        RocketBlockEntityPayload payload = block.blockEntityPayload().orElseThrow();
                        output.writeBoolean(true);
                        writeString(output, payload.adapterId().toString());
                        writeTag(output, payload.data());
                    } else {
                        output.writeBoolean(false);
                    }
                }
                output.writeInt(passengerAnchors.size());
                for (RocketPosition anchor : passengerAnchors) {
                    writePosition(output, anchor);
                }
                output.writeInt(stats.blockCount());
                output.writeLong(stats.mass());
                output.writeLong(stats.thrust());
                output.writeLong(stats.fuelCapacity());
                output.writeInt(stats.engineCount());
                output.writeInt(stats.seatCount());
                output.writeInt(stats.guidanceCount());
                output.writeInt(stats.blockEntityCount());
            }
            return toHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to hash rocket snapshot", exception);
        }
    }

    private static void writeTag(DataOutputStream output, Tag tag) throws IOException {
        output.writeByte(tag.getId());
        if (tag instanceof CompoundTag compound) {
            var keys = compound.getAllKeys().stream().sorted().toList();
            output.writeInt(keys.size());
            for (String key : keys) {
                writeString(output, key);
                writeTag(output, compound.get(key));
            }
        } else if (tag instanceof ListTag list) {
            output.writeInt(list.size());
            for (Tag entry : list) {
                writeTag(output, entry);
            }
        } else {
            writeString(output, tag.getAsString());
        }
    }

    private static void writePosition(DataOutputStream output, RocketPosition position) throws IOException {
        output.writeInt(position.x());
        output.writeInt(position.y());
        output.writeInt(position.z());
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }
}

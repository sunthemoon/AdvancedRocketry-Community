package io.github.sunthemoon.advancedrocketrycommunity.rocket.network;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Deterministic bounded binary codec for transient BlockEntity-free rendering data. */
public final class RocketVisualSnapshotCodec {
    public static final int SCHEMA_VERSION = 1;

    private RocketVisualSnapshotCodec() {
    }

    public static byte[] encode(RocketVisualSnapshot snapshot) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(SCHEMA_VERSION);
                output.writeLong(snapshot.snapshotId().getMostSignificantBits());
                output.writeLong(snapshot.snapshotId().getLeastSignificantBits());
                writeString(output, snapshot.structureContentHash(), 64, "structure hash");

                List<RocketBlockState> palette = snapshot.blocks().stream()
                        .map(RocketVisualBlock::state)
                        .distinct()
                        .sorted()
                        .toList();
                output.writeInt(palette.size());
                HashMap<RocketBlockState, Integer> indexes = new HashMap<>();
                for (int index = 0; index < palette.size(); index++) {
                    RocketBlockState state = palette.get(index);
                    indexes.put(state, index);
                    writeString(output, state.blockId().toString(), RocketLimits.MAX_IDENTIFIER_LENGTH, "block ID");
                    output.writeInt(state.properties().size());
                    for (Map.Entry<String, String> property : state.properties().entrySet()) {
                        writeString(output, property.getKey(), RocketLimits.MAX_PROPERTY_NAME_LENGTH, "property name");
                        writeString(output, property.getValue(), RocketLimits.MAX_PROPERTY_VALUE_LENGTH, "property value");
                    }
                }

                output.writeInt(snapshot.blocks().size());
                for (RocketVisualBlock block : snapshot.blocks()) {
                    output.writeInt(block.position().x());
                    output.writeInt(block.position().y());
                    output.writeInt(block.position().z());
                    output.writeInt(indexes.get(block.state()));
                }
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length <= 0 || encoded.length > RocketLimits.MAX_VISUAL_SNAPSHOT_BYTES) {
                throw new IllegalArgumentException("Visual snapshot exceeds the fixed payload limit");
            }
            return encoded;
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory visual snapshot encoding failed", exception);
        }
    }

    public static RocketVisualSnapshot decode(byte[] encoded) {
        if (encoded == null || encoded.length <= 0
                || encoded.length > RocketLimits.MAX_VISUAL_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Visual snapshot payload length is invalid");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int schema = input.readInt();
            if (schema != SCHEMA_VERSION) {
                throw new IllegalArgumentException("Unsupported visual snapshot schema " + schema);
            }
            UUID snapshotId = new UUID(input.readLong(), input.readLong());
            String structureHash = readString(input, 64, "structure hash");
            if (!structureHash.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("Visual snapshot structure hash is invalid");
            }

            int paletteSize = input.readInt();
            if (paletteSize <= 0 || paletteSize > RocketLimits.MAX_PALETTE_ENTRIES) {
                throw new IllegalArgumentException("Visual palette count is outside the fixed limit");
            }
            ArrayList<RocketBlockState> palette = new ArrayList<>(paletteSize);
            RocketBlockState previousState = null;
            for (int index = 0; index < paletteSize; index++) {
                ResourceLocation blockId = ResourceLocation.tryParse(readString(
                        input,
                        RocketLimits.MAX_IDENTIFIER_LENGTH,
                        "block ID"
                ));
                if (blockId == null) {
                    throw new IllegalArgumentException("Visual palette contains an invalid block ID");
                }
                int propertyCount = input.readInt();
                if (propertyCount < 0 || propertyCount > RocketLimits.MAX_BLOCK_PROPERTIES) {
                    throw new IllegalArgumentException("Visual property count is outside the fixed limit");
                }
                HashMap<String, String> properties = new HashMap<>();
                for (int propertyIndex = 0; propertyIndex < propertyCount; propertyIndex++) {
                    String name = readString(
                            input,
                            RocketLimits.MAX_PROPERTY_NAME_LENGTH,
                            "property name"
                    );
                    String value = readString(
                            input,
                            RocketLimits.MAX_PROPERTY_VALUE_LENGTH,
                            "property value"
                    );
                    if (properties.put(name, value) != null) {
                        throw new IllegalArgumentException("Visual state contains a duplicate property");
                    }
                }
                RocketBlockState state = new RocketBlockState(blockId, properties);
                if (previousState != null && previousState.compareTo(state) >= 0) {
                    throw new IllegalArgumentException("Visual palette is not in unique canonical order");
                }
                palette.add(state);
                previousState = state;
            }

            int blockCount = input.readInt();
            if (blockCount <= 0 || blockCount > RocketLimits.MAX_BLOCKS) {
                throw new IllegalArgumentException("Visual block count is outside the fixed limit");
            }
            ArrayList<RocketVisualBlock> blocks = new ArrayList<>(blockCount);
            RocketPosition previousPosition = null;
            for (int index = 0; index < blockCount; index++) {
                RocketPosition position = new RocketPosition(
                        input.readInt(),
                        input.readInt(),
                        input.readInt()
                );
                if (previousPosition != null && previousPosition.compareTo(position) >= 0) {
                    throw new IllegalArgumentException("Visual blocks are not in unique canonical order");
                }
                int paletteIndex = input.readInt();
                if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                    throw new IllegalArgumentException("Visual block references an invalid palette index");
                }
                blocks.add(new RocketVisualBlock(position, palette.get(paletteIndex)));
                previousPosition = position;
            }
            if (input.read() != -1) {
                throw new IllegalArgumentException("Visual snapshot contains trailing bytes");
            }
            return new RocketVisualSnapshot(snapshotId, structureHash, blocks);
        } catch (EOFException exception) {
            throw new IllegalArgumentException("Visual snapshot ended before all fields were decoded", exception);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Visual snapshot could not be decoded", exception);
        }
    }

    public static String sha256(byte[] payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(String.format(java.util.Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide SHA-256", exception);
        }
    }

    private static void writeString(
            DataOutputStream output,
            String value,
            int maxCharacters,
            String label
    ) throws IOException {
        if (value == null || value.isEmpty() || value.length() > maxCharacters) {
            throw new IllegalArgumentException("Visual " + label + " length is invalid");
        }
        byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
        if (utf8.length > 65_535) {
            throw new IllegalArgumentException("Visual " + label + " UTF-8 encoding is too long");
        }
        output.writeShort(utf8.length);
        output.write(utf8);
    }

    private static String readString(
            DataInputStream input,
            int maxCharacters,
            String label
    ) throws IOException {
        int byteLength = input.readUnsignedShort();
        if (byteLength <= 0 || byteLength > RocketLimits.MAX_VISUAL_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("Visual " + label + " byte length is invalid");
        }
        byte[] utf8 = input.readNBytes(byteLength);
        if (utf8.length != byteLength) {
            throw new EOFException("Visual " + label + " ended early");
        }
        String value = new String(utf8, StandardCharsets.UTF_8);
        if (value.isEmpty() || value.length() > maxCharacters
                || !java.util.Arrays.equals(utf8, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Visual " + label + " encoding or character length is invalid");
        }
        return value;
    }
}

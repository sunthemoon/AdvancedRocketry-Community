package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Canonical schema-1 palette codec with bounded compressed and expanded reads. */
public final class RocketSnapshotNbtCodec {
    private static final String SCHEMA = "schema_version";
    private static final String SNAPSHOT_ID = "snapshot_id";
    private static final String SOURCE_DIMENSION = "source_dimension";
    private static final String SOURCE_ORIGIN = "source_origin";
    private static final String BOUNDING_BOX = "bounding_box";
    private static final String PALETTE = "block_palette";
    private static final String BLOCKS = "relative_blocks";
    private static final String PASSENGER_ANCHORS = "passenger_anchors";
    private static final String MASS_INPUTS = "mass_inputs";
    private static final String CREATED_AT = "created_at_game_time";
    private static final String CONTENT_HASH = "content_hash";

    private RocketSnapshotNbtCodec() {
    }

    public static CompoundTag encode(RocketStructureSnapshot snapshot) {
        CompoundTag root = encodeUnchecked(snapshot);
        int bytes = RocketNbtSize.uncompressedBytes(root);
        if (bytes > RocketLimits.MAX_TOTAL_NBT_BYTES) {
            throw new RocketSnapshotException(
                    RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                    "Rocket snapshot is " + bytes + " bytes; limit is "
                            + RocketLimits.MAX_TOTAL_NBT_BYTES
            );
        }
        return root;
    }

    public static byte[] encodeCompressed(RocketStructureSnapshot snapshot) {
        CompoundTag root = encode(snapshot);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bytes);
            if (bytes.size() > RocketLimits.MAX_TOTAL_NBT_BYTES) {
                throw new RocketSnapshotException(
                        RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                        "Compressed rocket snapshot exceeds the fixed byte limit"
                );
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory rocket snapshot compression failed", exception);
        }
    }

    public static RocketSnapshotDecodeResult decodeCompressed(byte[] compressed) {
        if (compressed == null || compressed.length == 0
                || compressed.length > RocketLimits.MAX_TOTAL_NBT_BYTES) {
            return RocketSnapshotDecodeResult.invalid(
                    new CompoundTag(),
                    RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                    "Compressed rocket snapshot length is invalid"
            );
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
             DataInputStream input = new DataInputStream(gzip)) {
            CompoundTag root = NbtIo.read(
                    input,
                    new NbtAccounter(RocketLimits.MAX_TOTAL_NBT_BYTES)
            );
            if (input.read() != -1) {
                return RocketSnapshotDecodeResult.invalid(
                        root,
                        RocketValidationCode.MALFORMED_SNAPSHOT,
                        "Compressed rocket snapshot contains trailing expanded data"
                );
            }
            return decode(root);
        } catch (IOException | RuntimeException exception) {
            return RocketSnapshotDecodeResult.invalid(
                    new CompoundTag(),
                    RocketValidationCode.MALFORMED_SNAPSHOT,
                    "Unable to decode compressed rocket snapshot: " + exception.getMessage()
            );
        }
    }

    public static RocketSnapshotDecodeResult decode(CompoundTag source) {
        if (source == null) {
            return RocketSnapshotDecodeResult.invalid(
                    new CompoundTag(),
                    RocketValidationCode.MALFORMED_SNAPSHOT,
                    "Rocket snapshot payload is missing"
            );
        }
        CompoundTag preserved = source.copy();
        try {
            if (RocketNbtSize.uncompressedBytes(source) > RocketLimits.MAX_TOTAL_NBT_BYTES) {
                return RocketSnapshotDecodeResult.invalid(
                        preserved,
                        RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE,
                        "Expanded rocket snapshot exceeds the fixed byte limit"
                );
            }
            requireType(source, SCHEMA, Tag.TAG_INT);
            int schema = source.getInt(SCHEMA);
            if (schema > RocketLimits.SNAPSHOT_SCHEMA_VERSION) {
                return RocketSnapshotDecodeResult.future(preserved, schema);
            }
            if (schema != RocketLimits.SNAPSHOT_SCHEMA_VERSION) {
                throw malformed("Unsupported rocket snapshot schema " + schema);
            }

            UUID snapshotId = parseUuid(requireString(source, SNAPSHOT_ID));
            ResourceLocation sourceDimension = parseIdentifier(requireString(source, SOURCE_DIMENSION));
            RocketPosition sourceOrigin = readPosition(source, SOURCE_ORIGIN);
            RocketBounds encodedBounds = readBounds(source, BOUNDING_BOX);
            List<RocketBlockState> palette = readPalette(source);
            List<RocketBlock> blocks = readBlocks(source, palette);
            List<RocketPosition> anchors = readAnchors(source);
            RocketStats stats = readStats(source);
            requireType(source, CREATED_AT, Tag.TAG_LONG);
            long createdAt = source.getLong(CREATED_AT);
            String expectedHash = requireString(source, CONTENT_HASH);
            if (!expectedHash.matches("[0-9a-f]{64}")) {
                throw malformed("Rocket snapshot hash is not lowercase SHA-256");
            }

            RocketStructureSnapshot snapshot = RocketStructureSnapshot.create(
                    snapshotId,
                    sourceDimension,
                    sourceOrigin,
                    blocks,
                    anchors,
                    stats,
                    createdAt
            );
            if (!encodedBounds.equals(snapshot.bounds())) {
                throw malformed("Rocket snapshot bounding box does not match its blocks");
            }
            if (!expectedHash.equals(snapshot.contentHash())) {
                return RocketSnapshotDecodeResult.invalid(
                        preserved,
                        RocketValidationCode.HASH_MISMATCH,
                        "Rocket snapshot content hash does not match"
                );
            }
            return RocketSnapshotDecodeResult.valid(snapshot);
        } catch (RocketSnapshotException exception) {
            return RocketSnapshotDecodeResult.invalid(preserved, exception.code(), exception.getMessage());
        } catch (RuntimeException exception) {
            return RocketSnapshotDecodeResult.invalid(
                    preserved,
                    RocketValidationCode.MALFORMED_SNAPSHOT,
                    "Malformed rocket snapshot: " + exception.getMessage()
            );
        }
    }

    private static CompoundTag encodeUnchecked(RocketStructureSnapshot snapshot) {
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA, snapshot.schemaVersion());
        root.putString(SNAPSHOT_ID, snapshot.snapshotId().toString());
        root.putString(SOURCE_DIMENSION, snapshot.sourceDimension().toString());
        root.putIntArray(SOURCE_ORIGIN, positionArray(snapshot.sourceOrigin()));
        root.putIntArray(BOUNDING_BOX, boundsArray(snapshot.bounds()));

        List<RocketBlockState> palette = snapshot.blocks().stream()
                .map(RocketBlock::state)
                .distinct()
                .sorted()
                .toList();
        Map<RocketBlockState, Integer> paletteIndexes = new HashMap<>();
        ListTag paletteTag = new ListTag();
        for (int index = 0; index < palette.size(); index++) {
            RocketBlockState state = palette.get(index);
            paletteIndexes.put(state, index);
            CompoundTag entry = new CompoundTag();
            entry.putString("id", state.blockId().toString());
            CompoundTag properties = new CompoundTag();
            state.properties().forEach(properties::putString);
            entry.put("properties", properties);
            paletteTag.add(entry);
        }
        root.put(PALETTE, paletteTag);

        ListTag blockTags = new ListTag();
        for (RocketBlock block : snapshot.blocks()) {
            CompoundTag entry = new CompoundTag();
            entry.putIntArray("position", positionArray(block.position()));
            entry.putInt("palette", paletteIndexes.get(block.state()));
            block.blockEntityPayload().ifPresent(payload -> {
                CompoundTag encodedPayload = new CompoundTag();
                encodedPayload.putString("adapter", payload.adapterId().toString());
                encodedPayload.put("data", payload.data());
                entry.put("block_entity", encodedPayload);
            });
            blockTags.add(entry);
        }
        root.put(BLOCKS, blockTags);

        ListTag anchors = new ListTag();
        for (RocketPosition anchor : snapshot.passengerAnchors()) {
            anchors.add(new IntArrayTag(positionArray(anchor)));
        }
        root.put(PASSENGER_ANCHORS, anchors);

        RocketStats stats = snapshot.stats();
        CompoundTag massInputs = new CompoundTag();
        massInputs.putInt("block_count", stats.blockCount());
        massInputs.putLong("mass", stats.mass());
        massInputs.putLong("thrust", stats.thrust());
        massInputs.putLong("fuel_capacity", stats.fuelCapacity());
        massInputs.putInt("engine_count", stats.engineCount());
        massInputs.putInt("seat_count", stats.seatCount());
        massInputs.putInt("guidance_count", stats.guidanceCount());
        massInputs.putInt("block_entity_count", stats.blockEntityCount());
        root.put(MASS_INPUTS, massInputs);
        root.putLong(CREATED_AT, snapshot.createdAtGameTime());
        root.putString(CONTENT_HASH, snapshot.contentHash());
        return root;
    }

    private static List<RocketBlockState> readPalette(CompoundTag source) {
        ListTag entries = requireList(source, PALETTE, Tag.TAG_COMPOUND);
        if (entries.isEmpty() || entries.size() > RocketLimits.MAX_PALETTE_ENTRIES) {
            throw new RocketSnapshotException(
                    RocketValidationCode.TOO_MANY_PALETTE_ENTRIES,
                    "Rocket palette count is outside the allowed range"
            );
        }
        ArrayList<RocketBlockState> palette = new ArrayList<>(entries.size());
        RocketBlockState previous = null;
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            ResourceLocation id = parseIdentifier(requireString(entry, "id"));
            requireType(entry, "properties", Tag.TAG_COMPOUND);
            CompoundTag propertyTag = entry.getCompound("properties");
            Map<String, String> properties = new HashMap<>();
            for (String name : propertyTag.getAllKeys()) {
                requireType(propertyTag, name, Tag.TAG_STRING);
                properties.put(name, propertyTag.getString(name));
            }
            RocketBlockState state = new RocketBlockState(id, properties);
            if (previous != null && previous.compareTo(state) >= 0) {
                throw malformed("Rocket palette is not unique canonical order");
            }
            palette.add(state);
            previous = state;
        }
        return List.copyOf(palette);
    }

    private static List<RocketBlock> readBlocks(
            CompoundTag source,
            List<RocketBlockState> palette
    ) {
        ListTag entries = requireList(source, BLOCKS, Tag.TAG_COMPOUND);
        if (entries.isEmpty()) {
            throw new RocketSnapshotException(
                    RocketValidationCode.EMPTY_STRUCTURE,
                    "Rocket snapshot block list is empty"
            );
        }
        if (entries.size() > RocketLimits.MAX_BLOCKS) {
            throw new RocketSnapshotException(
                    RocketValidationCode.TOO_MANY_BLOCKS,
                    "Rocket snapshot block list exceeds the fixed limit"
            );
        }
        ArrayList<RocketBlock> blocks = new ArrayList<>(entries.size());
        RocketPosition previous = null;
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            RocketPosition position = readPosition(entry, "position");
            if (previous != null && previous.compareTo(position) >= 0) {
                throw new RocketSnapshotException(
                        previous.equals(position)
                                ? RocketValidationCode.DUPLICATE_BLOCK_POSITION
                                : RocketValidationCode.MALFORMED_SNAPSHOT,
                        position,
                        "Rocket blocks are not in unique canonical order"
                );
            }
            previous = position;
            requireType(entry, "palette", Tag.TAG_INT);
            int paletteIndex = entry.getInt("palette");
            if (paletteIndex < 0 || paletteIndex >= palette.size()) {
                throw malformed("Rocket block references an invalid palette index");
            }
            RocketBlockEntityPayload payload = null;
            if (entry.contains("block_entity")) {
                requireType(entry, "block_entity", Tag.TAG_COMPOUND);
                CompoundTag payloadTag = entry.getCompound("block_entity");
                ResourceLocation adapter = parseIdentifier(requireString(payloadTag, "adapter"));
                requireType(payloadTag, "data", Tag.TAG_COMPOUND);
                payload = new RocketBlockEntityPayload(adapter, payloadTag.getCompound("data"));
            }
            blocks.add(new RocketBlock(position, palette.get(paletteIndex), payload));
        }
        return List.copyOf(blocks);
    }

    private static List<RocketPosition> readAnchors(CompoundTag source) {
        ListTag entries = requireList(source, PASSENGER_ANCHORS, Tag.TAG_INT_ARRAY);
        ArrayList<RocketPosition> anchors = new ArrayList<>(entries.size());
        RocketPosition previous = null;
        Set<RocketPosition> unique = new HashSet<>();
        for (Tag raw : entries) {
            if (!(raw instanceof IntArrayTag array)) {
                throw malformed("Passenger anchor has the wrong NBT type");
            }
            RocketPosition position = parsePositionArray(array.getAsIntArray(), PASSENGER_ANCHORS);
            if (!unique.add(position) || (previous != null && previous.compareTo(position) >= 0)) {
                throw malformed("Passenger anchors are not in unique canonical order");
            }
            anchors.add(position);
            previous = position;
        }
        return List.copyOf(anchors);
    }

    private static RocketStats readStats(CompoundTag source) {
        requireType(source, MASS_INPUTS, Tag.TAG_COMPOUND);
        CompoundTag stats = source.getCompound(MASS_INPUTS);
        return new RocketStats(
                requireInt(stats, "block_count"),
                requireLong(stats, "mass"),
                requireLong(stats, "thrust"),
                requireLong(stats, "fuel_capacity"),
                requireInt(stats, "engine_count"),
                requireInt(stats, "seat_count"),
                requireInt(stats, "guidance_count"),
                requireInt(stats, "block_entity_count")
        );
    }

    private static RocketBounds readBounds(CompoundTag source, String key) {
        requireType(source, key, Tag.TAG_INT_ARRAY);
        int[] values = source.getIntArray(key);
        if (values.length != 6) {
            throw malformed(key + " must contain six integers");
        }
        return new RocketBounds(
                new RocketPosition(values[0], values[1], values[2]),
                new RocketPosition(values[3], values[4], values[5])
        );
    }

    private static RocketPosition readPosition(CompoundTag source, String key) {
        requireType(source, key, Tag.TAG_INT_ARRAY);
        return parsePositionArray(source.getIntArray(key), key);
    }

    private static RocketPosition parsePositionArray(int[] values, String key) {
        if (values.length != 3) {
            throw malformed(key + " must contain three integers");
        }
        return new RocketPosition(values[0], values[1], values[2]);
    }

    private static int[] positionArray(RocketPosition position) {
        return new int[]{position.x(), position.y(), position.z()};
    }

    private static int[] boundsArray(RocketBounds bounds) {
        return new int[]{
                bounds.minimum().x(), bounds.minimum().y(), bounds.minimum().z(),
                bounds.maximum().x(), bounds.maximum().y(), bounds.maximum().z()
        };
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw malformed("Invalid rocket snapshot UUID");
        }
    }

    private static ResourceLocation parseIdentifier(String value) {
        if (value.length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw malformed("Namespaced identifier exceeds the fixed length limit");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw malformed("Invalid namespaced identifier: " + value);
        }
        return parsed;
    }

    private static ListTag requireList(CompoundTag source, String key, byte elementType) {
        requireType(source, key, Tag.TAG_LIST);
        ListTag list = (ListTag) source.get(key);
        if (!list.isEmpty() && list.getElementType() != elementType) {
            throw malformed(key + " has the wrong element type");
        }
        return list;
    }

    private static String requireString(CompoundTag source, String key) {
        requireType(source, key, Tag.TAG_STRING);
        return source.getString(key);
    }

    private static int requireInt(CompoundTag source, String key) {
        requireType(source, key, Tag.TAG_INT);
        return source.getInt(key);
    }

    private static long requireLong(CompoundTag source, String key) {
        requireType(source, key, Tag.TAG_LONG);
        return source.getLong(key);
    }

    private static void requireType(CompoundTag source, String key, int type) {
        if (!source.contains(key, type)) {
            throw malformed("Missing or incorrectly typed rocket snapshot field: " + key);
        }
    }

    private static RocketSnapshotException malformed(String message) {
        return new RocketSnapshotException(RocketValidationCode.MALFORMED_SNAPSHOT, message);
    }
}

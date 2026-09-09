package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Codec for the independent {@code arce_multiblock} schema. */
public final class MultiblockControllerNbtCodec {
    public static final String ROOT = "arce_multiblock";

    private MultiblockControllerNbtCodec() {
    }

    public static CompoundTag encode(MultiblockControllerState state) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", state.schemaVersion());
        root.putString("machine_instance_id", state.machineInstanceId().toString());
        root.putLong("generation", state.generation());
        root.putInt("rotation", state.selectedTransform().rotation().degrees());
        root.putBoolean("mirror_local_x", state.selectedTransform().mirroredLocalX());
        root.putString("formation_state", state.formationState().name());
        ListTag parts = new ListTag();
        state.partPositions().forEach(position -> parts.add(encodePosition(position)));
        root.put("parts", parts);
        return root;
    }

    public static MultiblockNbtLoadResult<MultiblockControllerState> decode(CompoundTag parent) {
        if (!parent.contains(ROOT)) {
            return MultiblockNbtLoadResult.empty();
        }
        Tag raw = parent.get(ROOT);
        if (!(raw instanceof CompoundTag root)) {
            return MultiblockNbtLoadResult.rejected(
                    MultiblockNbtStatus.INVALID_DATA,
                    raw == null ? new CompoundTag() : raw
            );
        }
        if (!root.contains("schema_version", Tag.TAG_INT)) {
            return MultiblockNbtLoadResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
        if (root.getInt("schema_version") != MultiblockControllerState.SCHEMA_VERSION) {
            return MultiblockNbtLoadResult.rejected(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, root);
        }
        try {
            requireTypes(root);
            ListTag encodedParts = root.getList("parts", Tag.TAG_COMPOUND);
            if (encodedParts.size() > MultiblockControllerState.MAX_PARTS) {
                throw new IllegalArgumentException("controller NBT exceeds the part limit");
            }
            Set<BlockPos> parts = new HashSet<>();
            for (int index = 0; index < encodedParts.size(); index++) {
                BlockPos part = decodePosition(encodedParts.getCompound(index));
                if (!parts.add(part)) {
                    throw new IllegalArgumentException("controller NBT contains duplicate parts");
                }
            }
            MultiblockControllerState state = new MultiblockControllerState(
                    root.getInt("schema_version"),
                    parseUuid(root.getString("machine_instance_id")),
                    root.getLong("generation"),
                    new PatternTransform(parseRotation(root.getInt("rotation")), root.getBoolean("mirror_local_x")),
                    MultiblockFormationState.valueOf(root.getString("formation_state")),
                    parts
            );
            return MultiblockNbtLoadResult.supported(state);
        } catch (IllegalArgumentException exception) {
            return MultiblockNbtLoadResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    private static void requireTypes(CompoundTag root) {
        require(root, "machine_instance_id", Tag.TAG_STRING);
        require(root, "generation", Tag.TAG_LONG);
        require(root, "rotation", Tag.TAG_INT);
        require(root, "mirror_local_x", Tag.TAG_BYTE);
        require(root, "formation_state", Tag.TAG_STRING);
        require(root, "parts", Tag.TAG_LIST);
        if (root.getString("machine_instance_id").length() > 36
                || root.getString("formation_state").length() > 32) {
            throw new IllegalArgumentException("controller NBT string exceeds its limit");
        }
    }

    private static void require(CompoundTag root, String key, int type) {
        if (!root.contains(key, type)) {
            throw new IllegalArgumentException("controller NBT field has the wrong type");
        }
    }

    private static CompoundTag encodePosition(BlockPos position) {
        CompoundTag encoded = new CompoundTag();
        encoded.putInt("x", position.getX());
        encoded.putInt("y", position.getY());
        encoded.putInt("z", position.getZ());
        return encoded;
    }

    private static BlockPos decodePosition(CompoundTag encoded) {
        require(encoded, "x", Tag.TAG_INT);
        require(encoded, "y", Tag.TAG_INT);
        require(encoded, "z", Tag.TAG_INT);
        return new BlockPos(encoded.getInt("x"), encoded.getInt("y"), encoded.getInt("z"));
    }

    private static UUID parseUuid(String encoded) {
        return UUID.fromString(encoded);
    }

    private static PatternRotation parseRotation(int degrees) {
        return switch (degrees) {
            case 0 -> PatternRotation.ZERO;
            case 90 -> PatternRotation.CLOCKWISE_90;
            case 180 -> PatternRotation.CLOCKWISE_180;
            case 270 -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("unsupported controller rotation");
        };
    }
}

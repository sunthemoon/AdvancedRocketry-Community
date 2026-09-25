package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Codec for the independent {@code arce_part_binding} schema. */
public final class MultiblockPartBindingNbtCodec {
    public static final String ROOT = "arce_part_binding";
    public static final int MAX_ROOT_BYTES = 65_536;

    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version", "controller_level", "controller", "machine_instance_id", "generation"
    );
    private static final Set<String> POSITION_FIELDS = Set.of("x", "y", "z");

    private MultiblockPartBindingNbtCodec() {
    }

    public static CompoundTag encode(MultiblockPartBinding binding) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", binding.schemaVersion());
        root.putString("controller_level", binding.controllerLevel().location().toString());
        root.put("controller", encodePosition(binding.controllerPosition()));
        root.putString("machine_instance_id", binding.machineInstanceId().toString());
        root.putLong("generation", binding.generation());
        if (root.sizeInBytes() > MAX_ROOT_BYTES) {
            throw new IllegalStateException("part binding NBT exceeded its 64 KiB bound");
        }
        return root;
    }

    public static MultiblockNbtLoadResult<MultiblockPartBinding> decode(CompoundTag parent) {
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
        if (root.sizeInBytes() > MAX_ROOT_BYTES
                || !root.contains("schema_version", Tag.TAG_INT)) {
            return MultiblockNbtLoadResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
        if (root.getInt("schema_version") != MultiblockPartBinding.SCHEMA_VERSION) {
            return MultiblockNbtLoadResult.rejected(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, root);
        }
        try {
            if (!ROOT_FIELDS.equals(root.getAllKeys())) {
                throw new IllegalArgumentException("part binding NBT has missing or unexpected fields");
            }
            require(root, "controller_level", Tag.TAG_STRING);
            require(root, "controller", Tag.TAG_COMPOUND);
            require(root, "machine_instance_id", Tag.TAG_STRING);
            require(root, "generation", Tag.TAG_LONG);
            String levelId = root.getString("controller_level");
            if (levelId.length() > MultiblockPartBinding.MAX_LEVEL_ID_CHARS
                    || root.getString("machine_instance_id").length() > 36) {
                throw new IllegalArgumentException("part binding NBT string exceeds its limit");
            }
            ResourceLocation location = ResourceLocation.tryParse(levelId);
            if (location == null || !location.toString().equals(levelId)) {
                throw new IllegalArgumentException("part binding level is invalid");
            }
            ResourceKey<Level> level = ResourceKey.create(Registries.DIMENSION, location);
            MultiblockPartBinding binding = new MultiblockPartBinding(
                    root.getInt("schema_version"),
                    level,
                    decodePosition(root.getCompound("controller")),
                    parseUuid(root.getString("machine_instance_id")),
                    root.getLong("generation")
            );
            return MultiblockNbtLoadResult.supported(binding);
        } catch (IllegalArgumentException exception) {
            return MultiblockNbtLoadResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    private static void require(CompoundTag root, String key, int type) {
        if (!root.contains(key, type)) {
            throw new IllegalArgumentException("part binding NBT field has the wrong type");
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
        if (!POSITION_FIELDS.equals(encoded.getAllKeys())) {
            throw new IllegalArgumentException("part binding position NBT has missing or unexpected fields");
        }
        require(encoded, "x", Tag.TAG_INT);
        require(encoded, "y", Tag.TAG_INT);
        require(encoded, "z", Tag.TAG_INT);
        return new BlockPos(encoded.getInt("x"), encoded.getInt("y"), encoded.getInt("z"));
    }

    private static UUID parseUuid(String encoded) {
        UUID parsed = UUID.fromString(encoded);
        if (!parsed.toString().equals(encoded)) {
            throw new IllegalArgumentException("part binding UUID is not canonical");
        }
        return parsed;
    }
}

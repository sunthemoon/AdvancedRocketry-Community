package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Generation-aware persistent binding stored by a multiblock part. */
public record MultiblockPartBinding(
        int schemaVersion,
        ResourceKey<Level> controllerLevel,
        BlockPos controllerPosition,
        UUID machineInstanceId,
        long generation
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_LEVEL_ID_CHARS = 128;

    public MultiblockPartBinding {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported multiblock part binding schema");
        }
        Objects.requireNonNull(controllerLevel, "controllerLevel");
        if (controllerLevel.location().toString().length() > MAX_LEVEL_ID_CHARS) {
            throw new IllegalArgumentException("controller level ID exceeds the binding limit");
        }
        controllerPosition = Objects.requireNonNull(controllerPosition, "controllerPosition").immutable();
        Objects.requireNonNull(machineInstanceId, "machineInstanceId");
        if (generation < 1) {
            throw new IllegalArgumentException("part binding generation must be positive");
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Immutable schema-1 controller state; inventories remain owned by machine adapters. */
public record MultiblockControllerState(
        int schemaVersion,
        UUID machineInstanceId,
        long generation,
        PatternTransform selectedTransform,
        MultiblockFormationState formationState,
        Set<BlockPos> partPositions
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PARTS = PatternSize.MAX_CELLS - 1;
    private static final Comparator<BlockPos> POSITION_ORDER = Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX);

    public MultiblockControllerState {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported multiblock controller schema");
        }
        Objects.requireNonNull(machineInstanceId, "machineInstanceId");
        if (generation < 0) {
            throw new IllegalArgumentException("controller generation cannot be negative");
        }
        Objects.requireNonNull(selectedTransform, "selectedTransform");
        Objects.requireNonNull(formationState, "formationState");
        Objects.requireNonNull(partPositions, "partPositions");
        if (partPositions.size() > MAX_PARTS) {
            throw new IllegalArgumentException("controller exceeds the part position limit");
        }
        LinkedHashSet<BlockPos> copy = new LinkedHashSet<>();
        partPositions.stream().map(BlockPos::immutable).sorted(POSITION_ORDER).forEach(copy::add);
        partPositions = Collections.unmodifiableSet(copy);
        if (formationState == MultiblockFormationState.FORMED && generation == 0) {
            throw new IllegalArgumentException("formed controllers require a positive generation");
        }
        if (!retainsParts(formationState) && !partPositions.isEmpty()) {
            throw new IllegalArgumentException("inactive controller state cannot retain part positions");
        }
    }

    public static MultiblockControllerState initial(UUID instanceId, PatternTransform transform) {
        return new MultiblockControllerState(
                SCHEMA_VERSION,
                instanceId,
                0,
                transform,
                MultiblockFormationState.UNFORMED,
                Set.of()
        );
    }

    public MultiblockControllerState withState(
            MultiblockFormationState state,
            Set<BlockPos> positions
    ) {
        return new MultiblockControllerState(
                schemaVersion,
                machineInstanceId,
                generation,
                selectedTransform,
                state,
                positions
        );
    }

    public MultiblockControllerState formed(long nextGeneration, Set<BlockPos> positions) {
        return new MultiblockControllerState(
                schemaVersion,
                machineInstanceId,
                nextGeneration,
                selectedTransform,
                MultiblockFormationState.FORMED,
                positions
        );
    }

    public MultiblockControllerState selectTransform(PatternTransform transform) {
        if (formationState == MultiblockFormationState.FORMED
                || formationState == MultiblockFormationState.WAITING_UNLOADED
                || !partPositions.isEmpty()) {
            throw new IllegalStateException("cannot change transform while bindings are retained");
        }
        return new MultiblockControllerState(
                schemaVersion,
                machineInstanceId,
                generation,
                transform,
                formationState,
                partPositions
        );
    }

    private static boolean retainsParts(MultiblockFormationState state) {
        return state == MultiblockFormationState.FORMED
                || state == MultiblockFormationState.WAITING_UNLOADED
                || state == MultiblockFormationState.BINDING_CONFLICT;
    }
}

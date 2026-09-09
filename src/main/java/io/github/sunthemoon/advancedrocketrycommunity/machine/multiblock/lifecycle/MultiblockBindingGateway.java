package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle;

import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

/** Server-thread adapter contract for bounded and atomic part-binding mutation. */
public interface MultiblockBindingGateway {
    /**
     * Discovers binding-capable loaded parts from the validated pattern cells. Every
     * required position must resolve to a target; ordinary blocks may be omitted.
     */
    Optional<Set<BlockPos>> discoverBindings(
            Set<BlockPos> candidatePositions,
            Set<BlockPos> requiredPositions
    );

    boolean bindingsMatch(Set<BlockPos> positions, MultiblockPartBinding binding);

    /**
     * Replaces the complete known binding set atomically. A conflict must leave every
     * old and candidate part unchanged.
     */
    BindingMutationResult replaceBindings(
            Set<BlockPos> previousPositions,
            Optional<MultiblockPartBinding> previousBinding,
            Set<BlockPos> candidatePositions,
            MultiblockPartBinding candidateBinding
    );

    /** Unbinds matching loaded parts only and must never load a chunk. */
    int unbindLoaded(Set<BlockPos> positions, MultiblockPartBinding expectedBinding);
}

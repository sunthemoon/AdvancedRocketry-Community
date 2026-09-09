package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import java.util.Optional;

/** Implemented by part BlockEntities; binding changes must not mutate stored resources. */
public interface MultiblockPartBindingTarget {
    Optional<MultiblockPartBinding> multiblockBinding();

    void setMultiblockBinding(Optional<MultiblockPartBinding> binding);
}

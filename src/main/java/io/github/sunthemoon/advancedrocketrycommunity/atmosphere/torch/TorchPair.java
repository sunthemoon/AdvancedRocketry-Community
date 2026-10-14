package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One supported combustion torch (ADR-066 section 3.2): a standing and a wall block sharing one item. */
public record TorchPair(ResourceLocation id, ResourceLocation standingBlock, ResourceLocation wallBlock,
                        ResourceLocation item) {
    public TorchPair {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(standingBlock, "standingBlock");
        Objects.requireNonNull(wallBlock, "wallBlock");
        Objects.requireNonNull(item, "item");
        if (standingBlock.equals(wallBlock)) {
            throw new IllegalArgumentException("A torch pair needs two distinct blocks");
        }
    }

    /** The pair's lit block for one shape. */
    public ResourceLocation block(boolean wall) {
        return wall ? wallBlock : standingBlock;
    }
}

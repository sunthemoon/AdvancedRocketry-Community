package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import net.minecraft.resources.ResourceLocation;

/** Immutable output row; native registration and stack limits are checked by the guarded planner. */
public record ClassicItemOutput(ResourceLocation itemId, int count) {
    public ClassicItemOutput {
        ClassicValueChecks.id(itemId);
        ClassicValueChecks.require(count >= 1 && count <= 64, "Recipe Item output count");
    }
}

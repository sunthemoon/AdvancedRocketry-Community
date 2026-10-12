package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import net.minecraft.resources.ResourceLocation;

/** An ordered independent percentage candidate, separate from deterministic outputs. */
public record ClassicChanceOutput(ResourceLocation itemId, int count, int weight) {
    public ClassicChanceOutput {
        ClassicValueChecks.id(itemId);
        ClassicValueChecks.require(count >= 1 && count <= 64 && weight >= 1 && weight <= 100,
                "Chance output count/weight");
    }
}

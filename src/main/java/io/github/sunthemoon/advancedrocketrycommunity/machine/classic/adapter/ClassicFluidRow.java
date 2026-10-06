package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import net.minecraft.resources.ResourceLocation;

/** Immutable fluid row; this is not a live bank, resolved Fluid or transfer permission. */
public record ClassicFluidRow(ResourceLocation fluidId, int amount) {
    public ClassicFluidRow {
        ClassicValueChecks.id(fluidId);
        ClassicValueChecks.require(amount >= 1 && amount <= 16_000, "Recipe Fluid row amount");
    }
}

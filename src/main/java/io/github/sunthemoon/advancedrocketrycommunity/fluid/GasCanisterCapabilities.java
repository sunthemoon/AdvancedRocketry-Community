package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

/** Delegation point for OxygenCanisterItem, retaining its existing suit use. */
public final class GasCanisterCapabilities {
    private GasCanisterCapabilities() { }

    public static ICapabilityProvider create(ItemStack stack) {
        return new GasCanisterHandler(stack, GasCanisterCatalog::registered);
    }
}

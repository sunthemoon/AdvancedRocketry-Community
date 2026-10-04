package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

/** Empty, hydrogen and nitrogen canisters share the same stateless swap adapter. */
public final class GasCanisterItem extends Item {
    public GasCanisterItem(Properties properties) { super(properties); }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return GasCanisterCapabilities.create(stack);
    }
}

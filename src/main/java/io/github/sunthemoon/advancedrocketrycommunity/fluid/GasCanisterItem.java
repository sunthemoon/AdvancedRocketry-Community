package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.Nullable;

/** Empty, hydrogen and nitrogen canisters share the same stateless swap adapter. */
public final class GasCanisterItem extends Item {
    public GasCanisterItem(Properties properties) { super(properties); }

    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return TankCanisterUse.use(level, player, hand);
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return GasCanisterCapabilities.create(stack);
    }
}

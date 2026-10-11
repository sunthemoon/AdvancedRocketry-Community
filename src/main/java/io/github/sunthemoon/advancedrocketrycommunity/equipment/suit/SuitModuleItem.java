package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Installable only: motion and sensory behavior are separate C18 slices. */
public final class SuitModuleItem extends Item {
    private final int base;
    public SuitModuleItem(int base) { super(new Properties().stacksTo(1)); this.base = base; }
    @Override public ItemStack getDefaultInstance() { ItemStack stack = super.getDefaultInstance(); initialize(stack); return stack; }
    @Override public void onCraftedBy(ItemStack stack, Level level, Player player) {
        super.onCraftedBy(stack, level, player);
        if (!level.isClientSide) { initialize(stack); }
    }
    private void initialize(ItemStack stack) {
        if (base > 0 && (!stack.hasTag() || !stack.getTag().contains(TankData.KEY))) {
            stack.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.EMPTY, 0,
                    base * CommonConfig.suitTankMultiplier()).encode());
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Never applies BlockEntityTag. Carried resources have their own bounded root; placer ownership is rebound. */
public final class PumpBlockItem extends BlockItem {
    public PumpBlockItem(Block block, Properties properties) { super(block, properties); }
    @Override public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        if (stack.hasTag() && stack.getTag().contains(PumpSave.ROOT) && !PumpSave.bounded(stack.getTag().get(PumpSave.ROOT))) {
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
    @Override protected boolean updateCustomBlockEntityTag(BlockPos position, Level level, Player player,
            ItemStack stack, BlockState state) { return false; }
}

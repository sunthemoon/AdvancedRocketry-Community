package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public final class ScrubberBlock extends BaseEntityBlock {
    public ScrubberBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ScrubberBlockEntity(pos, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!(level.getBlockEntity(pos) instanceof ScrubberBlockEntity entity) || !DeviceAccess.player(entity, player)) { return InteractionResult.FAIL; }
        var items = entity.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (items == null) { return InteractionResult.FAIL; }
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() && player.isShiftKeyDown()) {
            ItemStack removed = items.extractItem(0, 1, false);
            if (removed.isEmpty()) { return InteractionResult.FAIL; }
            player.setItemInHand(hand, removed); return InteractionResult.CONSUME;
        }
        if (CarbonCartridge.uses(held) < 0 || !items.insertItem(0, held, true).isEmpty()
                || !items.insertItem(0, held, false).isEmpty()) { return InteractionResult.FAIL; }
        player.setItemInHand(hand, ItemStack.EMPTY); return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState old, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (!old.is(next.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ScrubberBlockEntity scrubber) { scrubber.detach(); }
        super.onRemove(old, level, pos, next, moved);
    }
    @Override public float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ScrubberBlockEntity entity && entity.protectedData() ? 0 : super.getDestroyProgress(state, player, level, pos);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Full-cube support tile; the indexed landing position is the air immediately above it. */
public final class LandingPadBlock extends BaseEntityBlock {
    public LandingPadBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new LandingPadBlockEntity(position, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                           InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            LandingPadCommands.interact(serverPlayer, serverLevel, position, player.isShiftKeyDown(),
                    player.getItemInHand(hand).hasCustomHoverName() ? player.getItemInHand(hand).getHoverName().getString() : "Landing Pad");
        }
        return InteractionResult.CONSUME;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(position) instanceof LandingPadBlockEntity pad && pad.reference() != null) {
            LandingPadIndex.get(serverLevel.getServer()).remove(pad.stationId(), pad.reference(), pad.physicalId());
        }
        super.onRemove(state, level, position, replacement, moving);
    }
}

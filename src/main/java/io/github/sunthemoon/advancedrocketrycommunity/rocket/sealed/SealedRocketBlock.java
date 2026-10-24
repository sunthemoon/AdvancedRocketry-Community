package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

/** Inert terrain authority. No native target or resource surface exists until checked unsealing. */
public final class SealedRocketBlock extends BaseEntityBlock {
    public SealedRocketBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SealedRocketBlockEntity(pos, state); }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.sealedOnPlace(server, pos);
        }
        super.onPlace(state, level, pos, previous, moving);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (level instanceof net.minecraft.server.level.ServerLevel server
                && io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime.sealedOnRemove(server, pos, next)) { return; }
        super.onRemove(state, level, pos, next, moving);
    }
    @Override public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) { return ItemStack.EMPTY; }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) { return InteractionResult.FAIL; }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            boolean harvest, FluidState fluid) { return false; }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) { return false; }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) { }
}

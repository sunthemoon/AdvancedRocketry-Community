package io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class SuitWorkstationBlock extends BaseEntityBlock {
    public SuitWorkstationBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new SuitWorkstationBlockEntity(pos, state); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (player instanceof ServerPlayer server && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof SuitWorkstationBlockEntity station && station.admitted(player)) {
            NetworkHooks.openScreen(server, station, buffer -> buffer.writeByte(1)); return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof SuitWorkstationBlockEntity station) { station.dropContents(); }
        super.onRemove(state, level, pos, replacement, moved);
    }
    static boolean protectedData(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SuitWorkstationBlockEntity station && station.repairRequired();
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean harvest, FluidState fluid) {
        return !protectedData(level, pos) && super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return !protectedData(level, pos) && super.canEntityDestroy(state, level, pos, entity);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!protectedData(level, pos)) { super.onBlockExploded(state, level, pos, explosion); }
    }
}

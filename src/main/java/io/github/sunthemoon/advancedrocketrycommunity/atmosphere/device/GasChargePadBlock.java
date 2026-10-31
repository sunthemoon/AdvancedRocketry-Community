package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;

public final class GasChargePadBlock extends BaseEntityBlock {
    public GasChargePadBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GasChargePadBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.GAS_CHARGE_PAD.get(), GasChargePadBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof GasChargePadBlockEntity pad && pad.interact(player, hand)) {
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.gas_charge_pad.instructions"), true);
        return InteractionResult.FAIL;
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof GasChargePadBlockEntity pad) { pad.dropContents(); }
        super.onRemove(state, level, pos, replacement, moved);
    }
    private boolean protectedAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GasChargePadBlockEntity pad && pad.protectedData();
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean harvest, FluidState fluid) {
        return !protectedAt(level, pos) && super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return !protectedAt(level, pos) && super.canEntityDestroy(state, level, pos, entity);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!protectedAt(level, pos)) { super.onBlockExploded(state, level, pos, explosion); }
    }
    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return protectedAt(level, pos) ? 0 : super.getDestroyProgress(state, player, level, pos);
    }
}

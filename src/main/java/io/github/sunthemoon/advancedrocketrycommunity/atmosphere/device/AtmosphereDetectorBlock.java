package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class AtmosphereDetectorBlock extends BaseEntityBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public AtmosphereDetectorBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(POWERED, false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(POWERED); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AtmosphereDetectorBlockEntity(pos, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public boolean isSignalSource(BlockState state) { return true; }
    @Override public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return level.getBlockEntity(pos) instanceof AtmosphereDetectorBlockEntity entity && entity.signal() ? 15 : 0;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ATMOSPHERE_DETECTOR.get(), AtmosphereDetectorBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!(level.getBlockEntity(pos) instanceof AtmosphereDetectorBlockEntity entity) || !DeviceAccess.player(entity, player)
                || entity.protectedData()) { return InteractionResult.FAIL; }
        if (player.isShiftKeyDown()) { entity.cycle(); }
        player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.detector.mode",
                Component.translatable("mode.advancedrocketrycommunity.detector." + entity.mode().name().toLowerCase(Locale.ROOT))), true);
        return InteractionResult.CONSUME;
    }
    private static boolean protectedData(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AtmosphereDetectorBlockEntity entity && entity.protectedData();
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean harvest,
                                                  net.minecraft.world.level.material.FluidState fluid) {
        return !protectedData(level, pos) && super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }
    @Override public boolean canEntityDestroy(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                               net.minecraft.world.entity.Entity entity) {
        return !protectedData(level, pos) && super.canEntityDestroy(state, level, pos, entity);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (!protectedData(level, pos)) { super.onBlockExploded(state, level, pos, explosion); }
    }
    @Override public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AtmosphereDetectorBlockEntity entity && entity.protectedData() ? 0 : super.getDestroyProgress(state, player, level, pos);
    }
}

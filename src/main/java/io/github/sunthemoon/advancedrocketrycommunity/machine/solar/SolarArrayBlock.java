package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.NetworkHooks;

/** Horizontal controller with fixed UP exposure; ordinary block drops deliberately destroy stored FE. */
public final class SolarArrayBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private final BiFunction<BlockPos, BlockState, SolarArrayBlockEntity> factory;
    private final Supplier<BlockEntityType<SolarArrayBlockEntity>> type;
    public SolarArrayBlock(Properties properties, BiFunction<BlockPos, BlockState, SolarArrayBlockEntity> factory,
                           Supplier<BlockEntityType<SolarArrayBlockEntity>> type) {
        super(properties); this.factory = factory; this.type = type;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return rotate(state, mirror.getRotation(state.getValue(FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return factory.apply(position, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> requested) {
        return level.isClientSide ? null : createTickerHelper(requested, type.get(),
                (world, pos, blockState, array) -> SolarArrayBlockEntity.serverTick((ServerLevel) world, pos, blockState, array));
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving); SolarArrayPartBlock.changed(level, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        SolarArrayPartBlock.changed(level, pos); super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof FakePlayer || !player.isAlive() || player.isSpectator()) { return InteractionResult.FAIL; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread()) { return InteractionResult.FAIL; }
        var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        var owner = chunk == null ? null : chunk.getBlockEntities().get(pos);
        if (owner instanceof SolarArrayBlockEntity array && SolarArrayMenu.admitted(player, array)) {
            if (array.repairRequired()) {
                player.sendSystemMessage(Component.translatable("screen.advancedrocketrycommunity.solar.reason.repair_required"));
            } else if (player instanceof ServerPlayer connected && array.available()) {
                NetworkHooks.openScreen(connected, array, SolarArrayMenu::writeOpenData); return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.FAIL;
    }
    static boolean protectedData(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SolarArrayBlockEntity array && array.repairRequired();
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

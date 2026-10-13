package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

public final class ClassicControllerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final java.util.function.BiFunction<BlockPos, BlockState, BlockEntity> factory;
    public ClassicControllerBlock(Properties properties) {
        this(properties, ClassicControllerBlockEntity::new);
    }
    public ClassicControllerBlock(Properties properties, java.util.function.BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(properties); this.factory = factory; registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return factory.apply(position, state); }
    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(position) instanceof ResearchControllerBlockEntity research) { research.placed(player.getUUID()); }
    }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (world, position, blockState, entity) -> {
            if (entity instanceof ResearchControllerBlockEntity research) { research.loadedTick(); }
        };
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.hasChunkAt(position)
                && level.getBlockEntity(position) instanceof ClassicControllerBlockEntity controller
                && ClassicControllerMenu.canOpen(serverPlayer, controller)) {
            NetworkHooks.openScreen(serverPlayer, controller, buffer -> {
                if (controller instanceof ResearchControllerBlockEntity research) { ResearchControllerMenu.writeOpening(buffer, position, research.processor()); }
                else { ClassicControllerMenu.writeOpening(buffer, position); }
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighbor, BlockPos from, boolean moved) {
        if (level instanceof ServerLevel server) { ClassicRuntime.manager(server).wake(position); }
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (level instanceof ServerLevel server && !state.is(replacement.getBlock())
                && level.getBlockEntity(position) instanceof ClassicControllerBlockEntity controller) {
            controller.removeController(); ClassicRuntime.manager(server).forget(controller);
        }
        super.onRemove(state, level, position, replacement, moved);
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos position, Entity entity) {
        return !ClassicRemovalPolicy.blocks(level, position) && super.canEntityDestroy(state, level, position, entity);
    }
    @Override public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos position, Explosion explosion) {
        return !ClassicRemovalPolicy.blocks(level, position) && super.canDropFromExplosion(state, level, position, explosion);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos position, Explosion explosion) {
        if (!ClassicRemovalPolicy.blocks(level, position)) { super.onBlockExploded(state, level, position, explosion); }
    }
}

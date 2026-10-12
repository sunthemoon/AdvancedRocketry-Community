package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class ForceFieldProjectorBlock extends ForceFieldBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public ForceFieldProjectorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (context.getLevel() instanceof ServerLevel server) {
            var root = FieldCleanupSavedData.get(server.getServer());
            if (!root.canPlace(server.dimension(), context.getClickedPos(), facing)) { return null; }
        }
        return defaultBlockState().setValue(FACING, facing);
    }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ForceFieldProjectorBlockEntity(pos, state); }
    /** Vanilla destroyBlock asks for loot before removal. Refused/replayed removal must never mint a spare projector. */
    @Override public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder context) {
        BlockEntity entity = context.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (!(entity instanceof ForceFieldProjectorBlockEntity projector) || projector.quarantined() || !projector.isRemoved()) {
            return java.util.List.of();
        }
        return super.getDrops(state, context);
    }
    /** dropResources reaches this hook before destroyBlock attempts removal; a loot query alone does not arm a drop. */
    @Override public void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean experience) {
        super.spawnAfterBreak(state, level, pos, tool, experience);
        if (!level.restoringBlockSnapshots && level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector
                && !projector.quarantined() && !projector.isRemoved()) { projector.deferWorldDrop(pos); }
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector) {
            projector.beginPlacement();
            projector.bind(placer);
            projector.reservePlacement();
        }
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer connected && connected.hasPermissions(2)
                && connected.distanceToSqr(pos.getCenter()) <= 64
                && level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector && projector.bind(connected)) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
        if (level instanceof ServerLevel server && !previous.is(this) && !ForceFieldProtection.permitted(server, pos)) {
            var root = FieldCleanupSavedData.get(server.getServer());
            boolean reserved = level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector
                    && projector.hasReservation();
            if (!reserved && !root.canPlace(server.dimension(), pos, state.getValue(FACING))) {
                ForceFieldProtection.physical(server, pos, () -> level.setBlock(pos, previous, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
                return;
            }
            AtmosphereRuntime.invalidateDoorBoundary(server, pos, pos);
        }
        super.onPlace(state, level, pos, previous, moved);
        if (level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector) { projector.commitPlacement(); }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.satellite.builder;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** Satellite Builder block (ADR-049 section 5); a quarantined root is carried exactly once, as at the terminal. */
public final class SatelliteBuilderBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SatelliteBuilderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(position) instanceof SatelliteBuilderBlockEntity builder) {
            CompoundTag carried = BlockItem.getBlockEntityData(stack);
            if (carried != null && carried.contains(SatelliteBuilderBlockEntity.DATA_KEY)) {
                var raw = carried.get(SatelliteBuilderBlockEntity.DATA_KEY);
                CompoundTag parent = new CompoundTag();
                parent.put(SatelliteBuilderBlockEntity.DATA_KEY, SatelliteBuilderBlockEntity.boundedRoot(raw) ? raw.copy() : raw);
                builder.load(parent);
                builder.setChanged();
            }
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SatelliteBuilderBlockEntity builder
                && builder.blocked()) {
            if (!builder.canCarryData()) {
                return List.of();
            }
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    BlockItem.setBlockEntityData(drop, ModBlockEntities.SATELLITE_BUILDER.get(), builder.carriedData());
                }
            }
        }
        return drops;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!player.getAbilities().instabuild
                && level.getBlockEntity(position) instanceof SatelliteBuilderBlockEntity builder && !builder.canCarryData()) {
            player.displayClientMessage(Component.translatable("status.advancedrocketrycommunity.satellite.unsupported_data"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof SatelliteBuilderBlockEntity builder) {
            long generation = SatelliteRuntime.catalogGeneration();
            NetworkHooks.openScreen(serverPlayer, builder,
                    buffer -> SatelliteBuilderMenu.writeOpenData(buffer, builder, generation));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState next, boolean moved) {
        if (!level.isClientSide && !state.is(next.getBlock())
                && level.getBlockEntity(position) instanceof SatelliteBuilderBlockEntity builder && !builder.blocked()) {
            SimpleContainer drops = new SimpleContainer(SatelliteBuilderBlockEntity.SLOT_COUNT);
            builder.copyInventoryTo(drops);
            Containers.dropContents(level, position, drops);
        }
        super.onRemove(state, level, position, next, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new SatelliteBuilderBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.SATELLITE_BUILDER.get(), SatelliteBuilderBlockEntity::serverTick);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}

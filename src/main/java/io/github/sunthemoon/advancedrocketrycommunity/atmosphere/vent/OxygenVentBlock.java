package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public final class OxygenVentBlock extends BaseEntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public OxygenVentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof OxygenVentBlockEntity vent)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (held.is(ModItems.OXYGEN_CANISTER.get())) {
            boolean accepted = vent.fillFromPlayer(player, hand);
            player.displayClientMessage(Component.translatable(
                    accepted
                            ? "message.advancedrocketrycommunity.vent.oxygen_added"
                            : "message.advancedrocketrycommunity.vent.oxygen_rejected",
                    vent.oxygenUnits()
            ), true);
            return accepted ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (held.is(Items.REDSTONE)) {
            boolean accepted = vent.chargeFromPlayer(player, hand);
            player.displayClientMessage(Component.translatable(
                    accepted
                            ? "message.advancedrocketrycommunity.vent.energy_added"
                            : "message.advancedrocketrycommunity.vent.energy_rejected",
                    vent.energyStored()
            ), true);
            return accepted ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.translatable(
                "message.advancedrocketrycommunity.vent.status",
                vent.status().diagnosticKey(),
                vent.oxygenUnits(),
                vent.energyStored()
        ), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof OxygenVentBlockEntity vent) {
                SimpleContainer drops = new SimpleContainer(OxygenVentBlockEntity.SLOT_COUNT);
                vent.copyInventoryTo(drops);
                Containers.dropContents(level, position, drops);
            }
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new OxygenVentBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide
                ? null
                : createTickerHelper(
                        type,
                        ModBlockEntities.OXYGEN_VENT.get(),
                        OxygenVentBlockEntity::serverTick
                );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}

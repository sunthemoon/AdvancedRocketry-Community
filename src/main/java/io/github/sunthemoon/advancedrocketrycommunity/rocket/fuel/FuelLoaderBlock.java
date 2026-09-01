package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class FuelLoaderBlock extends BaseEntityBlock {
    public FuelLoaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos position,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide
                && placer instanceof Player player
                && level.getBlockEntity(position) instanceof FuelLoaderBlockEntity loader) {
            loader.assignOwner(player.getUUID());
        }
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
        if (!(level.getBlockEntity(position) instanceof FuelLoaderBlockEntity loader)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!loader.authorized(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.fuel_loader.unauthorized"
            ), true);
            return InteractionResult.FAIL;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.ROCKET_FUEL_CELL.get())) {
            boolean accepted = loader.insertFuelFromPlayer(player, hand);
            player.displayClientMessage(Component.translatable(
                    accepted
                            ? "message.advancedrocketrycommunity.fuel_loader.cell_inserted"
                            : "message.advancedrocketrycommunity.fuel_loader.cell_rejected"
            ), true);
            return accepted ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (loader.takeOutput(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.fuel_loader.canister_returned"
            ), true);
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer) {
            loader.assignOwner(player.getUUID());
        }
        player.displayClientMessage(Component.translatable(
                "message.advancedrocketrycommunity.fuel_loader.status",
                Component.translatable(
                        "status.advancedrocketrycommunity.fuel_loader."
                                + loader.status().diagnosticKey()
                ),
                loader.bufferedUnits()
        ), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof FuelLoaderBlockEntity loader) {
                SimpleContainer drops = new SimpleContainer(FuelLoaderBlockEntity.SLOT_COUNT);
                loader.copyInventoryTo(drops);
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
        return new FuelLoaderBlockEntity(position, state);
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
                        ModBlockEntities.FUEL_LOADER.get(),
                        FuelLoaderBlockEntity::serverTick
                );
    }
}

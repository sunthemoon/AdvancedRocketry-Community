package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
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
                && level.getBlockEntity(position) instanceof FuelLoaderBlockEntity loader) {
            // BlockItem recursively merges BE data with defaults. Replace our root so a
            // malformed/future compound cannot gain default fields or become operational.
            var carried = BlockItem.getBlockEntityData(stack);
            if (carried != null && carried.contains(FuelLoaderStorage.DATA_KEY)) {
                var parent = new net.minecraft.nbt.CompoundTag();
                var raw = carried.get(FuelLoaderStorage.DATA_KEY);
                parent.put(FuelLoaderStorage.DATA_KEY, FuelPayloadBounds.root(raw) ? raw.copy() : raw);
                loader.load(parent);
                loader.setChanged();
            }
            if (placer instanceof Player player) { loader.assignOwner(player.getUUID()); }
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
        if (!held.isEmpty() && RocketFuelRuntime.find(held.getItem()) != null) {
            boolean accepted = loader.insertFuelFromPlayer(player, hand);
            player.displayClientMessage(Component.translatable(
                    accepted
                            ? "message.advancedrocketrycommunity.fuel_loader.fuel_inserted"
                            : "message.advancedrocketrycommunity.fuel_loader.fuel_rejected"
            ), true);
            return accepted ? InteractionResult.CONSUME : InteractionResult.FAIL;
        }
        if (loader.takeOutput(player)) {
            player.displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.fuel_loader.item_returned"
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
                                + (loader.status() == FuelLoaderStatus.OUTPUT_READY ? "item_ready" : loader.status().diagnosticKey())
                ),
                loader.bufferedUnits()
        ), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof FuelLoaderBlockEntity loader) {
            if (!loader.canCarryData()) { return List.of(); }
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    BlockItem.setBlockEntityData(drop, ModBlockEntities.FUEL_LOADER.get(), loader.carriedData());
                }
            }
        }
        return drops;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!player.getAbilities().instabuild
                && level.getBlockEntity(position) instanceof FuelLoaderBlockEntity loader && !loader.canCarryData()) {
            player.displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.fuel_loader.repair_required"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
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

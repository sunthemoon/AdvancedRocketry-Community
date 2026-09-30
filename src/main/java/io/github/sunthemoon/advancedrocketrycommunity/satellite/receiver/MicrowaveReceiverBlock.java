package io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.network.NetworkHooks;

/** Microwave receiver block (ADR-049 section 9); breaking it releases its links and drops its chips. */
public final class MicrowaveReceiverBlock extends BaseEntityBlock {
    public MicrowaveReceiverBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(position) instanceof MicrowaveReceiverBlockEntity receiver) {
            CompoundTag carried = BlockItem.getBlockEntityData(stack);
            if (carried != null && carried.contains(MicrowaveReceiverBlockEntity.DATA_KEY)) {
                var raw = carried.get(MicrowaveReceiverBlockEntity.DATA_KEY);
                CompoundTag parent = new CompoundTag();
                parent.put(MicrowaveReceiverBlockEntity.DATA_KEY,
                        MicrowaveReceiverBlockEntity.boundedRoot(raw) ? raw.copy() : raw);
                receiver.load(parent);
                receiver.setChanged();
            }
        }
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof MicrowaveReceiverBlockEntity receiver
                && receiver.blocked()) {
            if (!receiver.canCarryData()) {
                return List.of();
            }
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    BlockItem.setBlockEntityData(drop, ModBlockEntities.MICROWAVE_RECEIVER.get(), receiver.carriedData());
                }
            }
        }
        return drops;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!player.getAbilities().instabuild && level.getBlockEntity(position) instanceof MicrowaveReceiverBlockEntity receiver
                && !receiver.canCarryData()) {
            player.displayClientMessage(Component.translatable("status.advancedrocketrycommunity.satellite.unsupported_data"), true);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(position) instanceof MicrowaveReceiverBlockEntity receiver) {
            NetworkHooks.openScreen(serverPlayer, receiver, buffer -> MicrowaveReceiverMenu.writeOpenData(buffer, receiver));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState next, boolean moved) {
        if (!level.isClientSide && !state.is(next.getBlock())
                && level.getBlockEntity(position) instanceof MicrowaveReceiverBlockEntity receiver && !receiver.blocked()) {
            receiver.released();
            SimpleContainer drops = new SimpleContainer(MicrowaveReceiverBlockEntity.SLOT_COUNT);
            receiver.copyInventoryTo(drops);
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
        return new MicrowaveReceiverBlockEntity(position, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, ModBlockEntities.MICROWAVE_RECEIVER.get(), MicrowaveReceiverBlockEntity::serverTick);
    }
}

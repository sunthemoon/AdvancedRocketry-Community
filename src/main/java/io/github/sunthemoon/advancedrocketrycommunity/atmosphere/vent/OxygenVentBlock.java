package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
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
import net.minecraft.server.level.ServerLevel;
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
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (!io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.DeviceAccess.player(vent, player)) {
                return InteractionResult.FAIL;
            }
            ItemStack recovered = vent.scrubbers().recoverOrphan(vent);
            if (!recovered.isEmpty()) { player.setItemInHand(hand, recovered); }
            player.displayClientMessage(Component.translatable(recovered.isEmpty()
                    ? "message.advancedrocketrycommunity.vent.scrubber_recovery_refused"
                    : "message.advancedrocketrycommunity.vent.scrubber_cartridge_recovered"), true);
            return recovered.isEmpty() ? InteractionResult.FAIL : InteractionResult.CONSUME;
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
                Component.translatable(
                        "status.advancedrocketrycommunity.vent." + vent.status().diagnosticKey()
                ),
                vent.oxygenUnits(),
                vent.energyStored()
        ), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                AtmosphereRuntime.remove(serverLevel, position);
            }
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof OxygenVentBlockEntity vent) {
                if (!vent.protectedDeviceData()) { vent.scrubbers().dropAll(vent); }
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

    private static boolean protectedData(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof OxygenVentBlockEntity entity && entity.protectedDeviceData();
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
    @Override public float getDestroyProgress(BlockState state, Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof OxygenVentBlockEntity vent && vent.protectedDeviceData()
                ? 0 : super.getDestroyProgress(state, player, level, pos);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import java.util.List;
import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/** One native tank; no menu/custom packet, column scan, world-fluid placement or secondary resource drop. */
public final class PressurizedTankBlock extends BaseEntityBlock {
    private final Supplier<BlockEntityType<PressurizedTankBlockEntity>> type;
    private final DoubleSupplier multiplier;

    public PressurizedTankBlock(Properties properties, Supplier<BlockEntityType<PressurizedTankBlockEntity>> type,
            DoubleSupplier multiplier) {
        super(properties);
        this.type = Objects.requireNonNull(type, "type");
        this.multiplier = Objects.requireNonNull(multiplier, "multiplier");
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public PressurizedTankBlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new PressurizedTankBlockEntity(type.get(), position, state, multiplier);
    }
    @Override @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> kind) {
        return level.isClientSide ? null : createTickerHelper(kind, type.get(), (world, position, blockState, tank) ->
                PressurizedTankBlockEntity.serverTick((ServerLevel) world, position, blockState, tank));
    }

    @Override public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighbour,
            BlockPos neighbourPosition, boolean movedByPiston) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof PressurizedTankBlockEntity tank) {
            tank.markTransferDirty();
        }
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player.isSpectator() || player instanceof FakePlayer) { return InteractionResult.FAIL; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!level.hasChunkAt(position) || !(level.getBlockEntity(position) instanceof PressurizedTankBlockEntity tank)
                || player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) > 64
                || !level.mayInteract(player, position)) {
            return InteractionResult.FAIL;
        }
        if (tank.repairRequired()) {
            player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.tank.repair"), true);
            return InteractionResult.FAIL;
        }
        if (player.getItemInHand(hand).isEmpty()) {
            var fluid = tank.fluidState();
            player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.tank.contents",
                    fluid.isEmpty() ? Component.translatable("message.advancedrocketrycommunity.tank.empty")
                            : fluid.getDisplayName(), fluid.getAmount(), tank.capacity()), true);
            return InteractionResult.CONSUME;
        }
        if (tank.exchange(player, hand)) { return InteractionResult.CONSUME; }
        player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.tank.refused"), true);
        return InteractionResult.FAIL;
    }

    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && stack.hasTag() && stack.getTag().contains(TankSave.ROOT)
                && level.getBlockEntity(position) instanceof PressurizedTankBlockEntity tank) {
            // Item.place preflights this before world mutation; keep bounded corrupt roots quarantined if invoked directly.
            var raw = stack.getTag().get(TankSave.ROOT);
            CompoundTag outer = new CompoundTag();
            outer.put(TankSave.ROOT, TankSave.bounded(raw) ? raw.copy() : raw);
            tank.load(outer);
            tank.setChanged();
        }
    }

    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PressurizedTankBlockEntity tank)
                || tank.repairRequired()) { return List.of(); }
        List<ItemStack> drops = super.getDrops(state, params);
        for (ItemStack drop : drops) {
            if (drop.is(asItem())) { drop.getOrCreateTag().put(TankSave.ROOT, tank.resourceRoot()); }
        }
        return drops;
    }

    static boolean protectedData(BlockGetter level, BlockPos position) {
        return !(level.getBlockEntity(position) instanceof PressurizedTankBlockEntity tank) || tank.repairRequired();
    }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
            boolean harvest, FluidState fluid) {
        return !protectedData(level, position) && super.onDestroyedByPlayer(state, level, position, player, harvest, fluid);
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos position, Entity entity) {
        return !protectedData(level, position) && super.canEntityDestroy(state, level, position, entity);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos position, Explosion explosion) {
        if (!protectedData(level, position)) { super.onBlockExploded(state, level, position, explosion); }
    }
}

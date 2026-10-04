package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.util.FakePlayer;

/** One-block pump. Vanilla use intent opens no remote target/control interface. */
public final class PumpBlock extends BaseEntityBlock {
    public PumpBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new PumpBlockEntity(position, state); }
    @Override @Nullable public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, PumpContent.ENTITY.get(),
                (world, pos, blockState, pump) -> PumpBlockEntity.serverTick((ServerLevel) world, pos, blockState, pump));
    }
    @Override public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(position) instanceof PumpBlockEntity pump) {
                pump.placed(placer instanceof ServerPlayer player && !(player instanceof FakePlayer) ? player.getUUID() : null,
                        stack.hasTag() ? stack.getTag().get(PumpSave.ROOT) : null);
            }
        }
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (player instanceof FakePlayer || player.isSpectator()) { return InteractionResult.FAIL; }
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer actor)
                || player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) > 64) {
            return InteractionResult.FAIL;
        }
        var chunk = server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        if (chunk == null || !(chunk.getBlockEntity(position) instanceof PumpBlockEntity pump) || !pump.available()) {
            return InteractionResult.FAIL;
        }
        if (actor.getUUID().equals(pump.owner()) || actor.hasPermissions(2)) { pump.retry(); }
        player.displayClientMessage(Component.translatable("message.advancedrocketrycommunity.pump.status",
                Component.translatable("status.advancedrocketrycommunity.pump." + pump.status().name().toLowerCase(java.util.Locale.ROOT)),
                pump.energy(), pump.fluid().getAmount()), true);
        return InteractionResult.CONSUME;
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos position, Block source, BlockPos sourcePosition, boolean moving) {
        if (level instanceof ServerLevel server) {
            var chunk = server.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
            if (chunk != null && chunk.getBlockEntity(position) instanceof PumpBlockEntity pump) { pump.neighbourWake(); }
        }
    }
    static boolean protectedData(BlockGetter level, BlockPos position) {
        return level.getBlockEntity(position) instanceof PumpBlockEntity pump && pump.removalProtected();
    }
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        var entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (entity instanceof PumpBlockEntity pump && pump.removalProtected()) { return List.of(); }
        List<ItemStack> drops = super.getDrops(state, params);
        if (entity instanceof PumpBlockEntity pump) {
            for (ItemStack stack : drops) {
                if (stack.is(asItem())) { stack.getOrCreateTag().put(PumpSave.ROOT, pump.carriedRoot()); }
            }
        }
        return drops;
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

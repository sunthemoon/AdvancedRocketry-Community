package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.DeferredRoomBoundary;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/** Collision and sealing are full-cube BlockState properties, never a lookup of the source or its BE. */
public class ForceFieldBlock extends BaseEntityBlock implements DeferredRoomBoundary {
    public ForceFieldBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new ForceFieldBlockEntity(pos, state); }
    @Override public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
            boolean harvest, FluidState fluid) {
        if (level instanceof ServerLevel server && !ForceFieldProtection.permitted(server, pos)) {
            if (!(level.getBlockEntity(pos) instanceof ForceFieldNativeBlockEntity entity)) { return false; }
            Optional<java.util.UUID> actor = player instanceof net.minecraft.server.level.ServerPlayer connected
                    && !(player instanceof net.minecraftforge.common.util.FakePlayer)
                    && server.getServer().getPlayerList().getPlayer(player.getUUID()) == connected
                    ? Optional.of(player.getUUID()) : Optional.empty();
            if (!ForceFieldProtection.admitRemoval(server, entity, actor) || !ForceFieldProtection.commitRetirement(server, entity)) { return false; }
            boolean[] result = {false};
            ForceFieldProtection.physical(server, pos, () -> result[0] = super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid));
            return result[0];
        }
        return allowed(level, pos) && super.onDestroyedByPlayer(state, level, pos, player, harvest, fluid);
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return level instanceof Level world && allowed(world, pos) && super.canEntityDestroy(state, level, pos, entity);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector) { projector.takeWorldDrop(); }
        if (level instanceof ServerLevel server && !ForceFieldProtection.permitted(server, pos)) {
            if (!(level.getBlockEntity(pos) instanceof ForceFieldNativeBlockEntity entity)
                    || !ForceFieldProtection.admitRemoval(server, entity, Optional.empty())
                    || !ForceFieldProtection.commitRetirement(server, entity)) { return; }
            ForceFieldProtection.physical(server, pos, () -> super.onBlockExploded(state, level, pos, explosion));
            if (entity instanceof ForceFieldProjectorBlockEntity && entity.isRemoved()
                    && !level.getBlockState(pos).is(this)) {
                popResource(level, pos, new net.minecraft.world.item.ItemStack(
                        io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.FORCE_FIELD_PROJECTOR.get()));
            }
        } else if (allowed(level, pos)) { super.onBlockExploded(state, level, pos, explosion); }
    }
    private static boolean allowed(Level level, BlockPos pos) {
        return !(level instanceof ServerLevel server) || ForceFieldProtection.permitted(server, pos)
                || (level.getBlockEntity(pos) instanceof ForceFieldNativeBlockEntity entity
                && ForceFieldProtection.admitRemoval(server, entity, Optional.empty()));
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof ForceFieldNativeBlockEntity entity) {
            boolean drop = entity instanceof ForceFieldProjectorBlockEntity projector && projector.takeWorldDrop();
            // Only the new, uncommitted projector in Forge's captured transaction may bypass destruction checks.
            if (entity instanceof ForceFieldProjectorBlockEntity projector && projector.rollbackPlacement()) {
                super.onRemove(state, level, pos, replacement, moved);
                return;
            }
            if (!ForceFieldProtection.permitted(server, pos)
                    && (!ForceFieldProtection.admitRemoval(server, entity, Optional.empty())
                    || !ForceFieldProtection.commitRetirement(server, entity))) {
                ForceFieldProtection.restore(server, pos, state, entity);
                return;
            }
            super.onRemove(state, level, pos, replacement, moved);
            AtmosphereRuntime.invalidateDoorBoundary(server, pos, pos);
            if (drop && entity.isRemoved() && !level.getBlockState(pos).is(this)) {
                popResource(level, pos, new net.minecraft.world.item.ItemStack(
                        io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.FORCE_FIELD_PROJECTOR.get()));
            }
            return;
        }
        super.onRemove(state, level, pos, replacement, moved);
    }
}

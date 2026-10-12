package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicHatchKind;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;

/** Physical facade. Forge-aware destruction uses the same loaded-only removal policy. */
public final class ClassicHatchBlock extends BaseEntityBlock {
    private final ClassicHatchKind kind;
    public ClassicHatchBlock(Properties properties, ClassicHatchKind kind) { super(properties); this.kind = kind; }
    public ClassicHatchKind kind() { return kind; }
    @Override public BlockEntity newBlockEntity(BlockPos position, BlockState state) { return new ClassicHatchBlockEntity(position, state); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public void onRemove(BlockState state, Level level, BlockPos position, BlockState replacement, boolean moved) {
        if (level instanceof ServerLevel server && !state.is(replacement.getBlock())
                && level.getBlockEntity(position) instanceof ClassicHatchBlockEntity hatch) {
            hatch.removedByBlock(); ClassicRuntime.manager(server).changed(position);
        }
        super.onRemove(state, level, position, replacement, moved);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos position, BlockState previous, boolean moved) {
        super.onPlace(state, level, position, previous, moved);
        if (level instanceof ServerLevel server) { ClassicRuntime.manager(server).changed(position); }
    }
    @Override public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos position, Entity entity) {
        return !ClassicRemovalPolicy.blocks(level, position) && super.canEntityDestroy(state, level, position, entity);
    }
    @Override public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos position, Explosion explosion) {
        return !ClassicRemovalPolicy.blocks(level, position) && super.canDropFromExplosion(state, level, position, explosion);
    }
    @Override public void onBlockExploded(BlockState state, Level level, BlockPos position, Explosion explosion) {
        if (!ClassicRemovalPolicy.blocks(level, position)) { super.onBlockExploded(state, level, position, explosion); }
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos position, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) { return InteractionResult.SUCCESS; }
        if (!level.hasChunkAt(position) || player.isSpectator() || !player.isAlive() || !level.mayInteract(player, position)
                || player.distanceToSqr(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5) > 64
                || !(level.getBlockEntity(position) instanceof ClassicHatchBlockEntity hatch)) { return InteractionResult.PASS; }
        if (kind == ClassicHatchKind.FLUID_INPUT || kind == ClassicHatchKind.FLUID_OUTPUT) {
            return FluidUtil.interactWithFluidHandler(player, hand, level, position, hit.getDirection()) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        var handler = hatch.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) { return InteractionResult.PASS; }
        ItemStack held = player.getItemInHand(hand);
        if (kind == ClassicHatchKind.ITEM_INPUT && !held.isEmpty()) {
            ItemStack remaining = held.copy();
            for (int slot = 0; slot < 4 && !remaining.isEmpty(); slot++) { remaining = handler.insertItem(slot, remaining, false); }
            player.setItemInHand(hand, remaining); return InteractionResult.CONSUME;
        }
        if (kind == ClassicHatchKind.ITEM_OUTPUT && held.isEmpty()) {
            for (int slot = 0; slot < 4; slot++) {
                ItemStack taken = handler.extractItem(slot, 64, false);
                if (!taken.isEmpty()) { player.setItemInHand(hand, taken); return InteractionResult.CONSUME; }
            }
        }
        return InteractionResult.PASS;
    }
}

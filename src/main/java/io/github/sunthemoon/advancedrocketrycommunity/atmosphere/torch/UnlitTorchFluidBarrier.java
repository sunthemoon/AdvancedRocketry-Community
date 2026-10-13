package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Native fluid admission without changing the torch's collision or mounting shape. FlowingFluid.canHoldFluid consults
 * LiquidBlockContainer first, and spreadTo delegates to placeLiquid instead of deleting the block. These are not
 * waterlogged torches: an admitted native fluid replaces a resolvable/rootless torch with its ordinary drop/fizz rule.
 */
interface UnlitTorchFluidBarrier extends LiquidBlockContainer {
    @Override
    default boolean canPlaceLiquid(BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return (fluid.isSame(Fluids.WATER) || fluid.isSame(Fluids.LAVA))
                && !UnlitTorchBlockEntity.protectedAt(level, pos);
    }

    @Override
    default boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        // Recheck at the mutation boundary, including a caller that bypasses canHoldFluid.
        if (!level.getBlockState(pos).equals(state) || !canPlaceLiquid(level, pos, state, fluid.getType())) {
            return false;
        }
        if (fluid.getType().isSame(Fluids.WATER) || fluid.isSource()) {
            // WaterFluid.beforeDestroyingBlock supplies the block entity to the native loot callback.
            // BucketItem's source placement also ordinarily destroys a non-solid block with drops (even for lava).
            Block.dropResources(state, level, pos, level.getBlockEntity(pos));
        } else {
            // LavaFluid.beforeDestroyingBlock only calls fizz (level event 1501); no torch item drops.
            level.levelEvent(1501, pos, 0);
        }
        return level.setBlock(pos, fluid.createLegacyBlock(), Block.UPDATE_ALL);
    }
}

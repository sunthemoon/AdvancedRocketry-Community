package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.assertFalse;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.Test;

class RecipeSignatureProtectionTest {
    @Test void nonServerViewDoesNotQueryBlocksOrEntities() {
        BlockGetter view = new BlockGetter() {
            @Override public BlockEntity getBlockEntity(BlockPos position) {
                throw new AssertionError("Non-server view queried a BlockEntity");
            }
            @Override public BlockState getBlockState(BlockPos position) {
                throw new AssertionError("Non-server view queried a block");
            }
            @Override public FluidState getFluidState(BlockPos position) {
                throw new AssertionError("Non-server view queried a fluid");
            }
            @Override public int getHeight() { return 384; }
            @Override public int getMinBuildHeight() { return -64; }
        };
        assertFalse(RecipeSignatureProtection.blocksRemoval(view, new BlockPos(20_000_000, 64, 20_000_000)));
    }
}

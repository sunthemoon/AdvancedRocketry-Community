package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

/** Uses the vanilla entity lava-damage action without copying vanilla assets. */
public final class EnrichedLavaBlock extends LiquidBlock {
    public EnrichedLavaBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide) { entity.lavaHurt(); }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import io.github.sunthemoon.advancedrocketrycommunity.fluid.EnrichedLavaBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import javax.annotation.Nullable;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

/** Standard direct-removal sources only; never calls a block's custom pickup adapter. */
public final class PumpSources {
    @Nullable public static Fluid fluid(BlockState state) {
        if (state.hasBlockEntity() || !(state.getBlock() instanceof LiquidBlock)
                || (state.getBlock().getClass() != LiquidBlock.class
                    && state.getBlock().getClass() != EnrichedLavaBlock.class)) { return null; }
        var current = state.getFluidState();
        if (current.isEmpty() || !(current.getType() instanceof FlowingFluid flowing)) { return null; }
        Fluid source = flowing.getSource();
        var id = ForgeRegistries.FLUIDS.getKey(source);
        if (!(source.getBucket() instanceof BucketItem bucket) || bucket.getClass() != BucketItem.class
                || !bucket.getFluid().isSame(source) || id == null
                || id.toString().length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                || !source.defaultFluidState().createLegacyBlock().is(state.getBlock())) { return null; }
        return source;
    }
    private PumpSources() { }
}

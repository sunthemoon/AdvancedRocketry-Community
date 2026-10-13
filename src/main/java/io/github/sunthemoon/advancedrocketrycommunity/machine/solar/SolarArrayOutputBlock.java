package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Genuine output-only interface, not a repurposed C16 power input plug. */
public final class SolarArrayOutputBlock extends BaseEntityBlock {
    public SolarArrayOutputBlock(Properties properties) { super(properties); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SolarArrayOutputBlockEntity(ModBlockEntities.POWER_OUTPUT_PLUG.get(), pos, state);
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving); SolarArrayPartBlock.changed(level, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        SolarArrayPartBlock.changed(level, pos); super.onRemove(state, level, pos, replacement, moving);
    }
}

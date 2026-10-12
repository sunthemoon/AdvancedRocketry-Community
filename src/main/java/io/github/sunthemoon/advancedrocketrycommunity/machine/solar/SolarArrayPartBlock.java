package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Direct part edits also notify the footprint index when callers omit neighbor-update flags. */
public final class SolarArrayPartBlock extends Block {
    public SolarArrayPartBlock(Properties properties) { super(properties); }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving); changed(level, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        changed(level, pos); super.onRemove(state, level, pos, replacement, moving);
    }
    static void changed(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) { SolarArrayRuntime.manager(server).changed(pos); }
    }
}

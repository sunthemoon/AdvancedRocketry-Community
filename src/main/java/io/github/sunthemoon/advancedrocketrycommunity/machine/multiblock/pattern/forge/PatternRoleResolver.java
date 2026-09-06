package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Machine-specific role lookup without pattern scans or world mutation. */
public interface PatternRoleResolver {
    boolean isController(BlockPos position, BlockState state);

    Optional<String> portChannel(BlockPos position, BlockState state);
}

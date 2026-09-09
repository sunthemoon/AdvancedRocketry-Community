package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.PatternRoleResolver;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Constant-time role resolver for Rolling Machine controller and typed ports. */
public final class RollingMachinePatternRoles implements PatternRoleResolver {
    public static final RollingMachinePatternRoles INSTANCE = new RollingMachinePatternRoles();

    private RollingMachinePatternRoles() {
    }

    @Override
    public boolean isController(BlockPos position, BlockState state) {
        return state.is(ModBlocks.ROLLING_MACHINE.get());
    }

    @Override
    public Optional<String> portChannel(BlockPos position, BlockState state) {
        if (state.getBlock() instanceof RollingMachinePortBlock port) {
            return Optional.of(port.portType().channel());
        }
        return Optional.empty();
    }
}

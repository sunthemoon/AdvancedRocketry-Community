package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.PatternRoleResolver;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Constant-time world role lookup for physical Precision Assembler parts. */
public final class PrecisionAssemblerPatternRoles implements PatternRoleResolver {
    public static final PrecisionAssemblerPatternRoles INSTANCE = new PrecisionAssemblerPatternRoles();

    private PrecisionAssemblerPatternRoles() {
    }

    @Override
    public boolean isController(BlockPos position, BlockState state) {
        return state.is(ModBlocks.PRECISION_ASSEMBLER.get());
    }

    @Override
    public Optional<String> portChannel(BlockPos position, BlockState state) {
        if (state.getBlock() instanceof PrecisionAssemblerPortBlock port) {
            return Optional.of(port.portType().patternChannel());
        }
        return Optional.empty();
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import java.lang.ref.WeakReference;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ADR-056 section 6 on a client: at most 8 railgun effects (a launch flash with one upward streak, or an arrival
 * flash) run at once; a ninth is not shown. The slots belong to one client Level and reset when it changes; only the
 * client thread uses them. No entity is spawned.
 */
final class RailgunEffects {
    static final int MAX_CONCURRENT = 8;
    private static final long[] UNTIL = new long[MAX_CONCURRENT];
    @Nullable
    private static WeakReference<Level> owner;

    private RailgunEffects() {
    }

    /** Takes a free slot until {@code now + duration}; false when all 8 are busy. */
    static boolean start(Level level, long now, int duration) {
        if (owner == null || owner.get() != level) {
            owner = new WeakReference<>(level);
            java.util.Arrays.fill(UNTIL, Long.MIN_VALUE);
        }
        for (int slot = 0; slot < MAX_CONCURRENT; slot++) {
            if (UNTIL[slot] <= now) {
                UNTIL[slot] = now + duration;
                return true;
            }
        }
        return false;
    }

    /** One tick of a running effect: the streak rises from the muzzle; an arrival sparkles down onto it. */
    static void tick(Level level, BlockPos controller, BlockState state, int kind) {
        BlockPos barrel = controller.relative(state.getValue(RailgunBlock.FACING).getOpposite());
        double x = barrel.getX() + 0.5D;
        double y = barrel.getY() + RailgunBlockEntity.MUZZLE_HEIGHT + 1.0D;
        double z = barrel.getZ() + 0.5D;
        if (kind == RailgunBlockEntity.EVENT_LAUNCH) {
            level.addParticle(ParticleTypes.END_ROD, x, y, z, 0.0D, 2.0D, 0.0D);
        } else {
            level.addParticle(ParticleTypes.END_ROD, x + level.random.nextGaussian() * 0.4D, y,
                    z + level.random.nextGaussian() * 0.4D, 0.0D, -0.4D, 0.0D);
        }
    }
}

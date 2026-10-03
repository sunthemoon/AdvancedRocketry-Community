package io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The electric mushroom (ADR-063 section 6, revision 6): it stands on any block with a sturdy top face, gives no light
 * and throws sparks. Its lightning is an effect on each client only: the client installs a {@link Flash} that may
 * flash the sky during rain in a stormland. The server installs nothing, so nothing is struck, set on fire or sent.
 */
public final class ElectricMushroomBlock extends BushBlock {
    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);

    /** A client effect run from the mushroom's display tick. */
    @FunctionalInterface
    public interface Flash {
        void maybeFlash(Level level, BlockPos position, RandomSource random);
    }

    private static volatile Flash flash;

    public ElectricMushroomBlock(Properties properties) {
        super(properties);
    }

    /** Called once by the client during setup. */
    public static void installClientFlash(Flash effect) {
        flash = effect;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos position) {
        return state.isFaceSturdy(level, position, Direction.UP);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos position, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos position, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, position.getX() + 0.3D + random.nextDouble() * 0.4D,
                    position.getY() + 0.4D, position.getZ() + 0.3D + random.nextDouble() * 0.4D,
                    random.nextGaussian() * 0.05D, 0.1D, random.nextGaussian() * 0.05D);
        }
        Flash effect = flash;
        if (effect != null) {
            effect.maybeFlash(level, position, random);
        }
    }
}

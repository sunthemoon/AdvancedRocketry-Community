package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.fluids.FluidType;

/** A nonempty registry fluid that cannot create a world fluid or a bucket. */
public final class ContainerGasFluid extends Fluid {
    private final Supplier<? extends FluidType> type;

    public ContainerGasFluid(Supplier<? extends FluidType> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @Override public FluidType getFluidType() { return type.get(); }
    @Override public Item getBucket() { return Items.AIR; }
    @Override protected boolean canBeReplacedWith(FluidState state, BlockGetter level,
            BlockPos pos, Fluid replacement, Direction direction) { return false; }
    @Override protected Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState state) {
        return Vec3.ZERO;
    }
    @Override public int getTickDelay(LevelReader level) { return Integer.MAX_VALUE; }
    @Override protected float getExplosionResistance() { return 0.0F; }
    @Override public float getHeight(FluidState state, BlockGetter level, BlockPos pos) { return 0.0F; }
    @Override public float getOwnHeight(FluidState state) { return 0.0F; }
    @Override protected BlockState createLegacyBlock(FluidState state) { return Blocks.AIR.defaultBlockState(); }
    @Override public boolean isSource(FluidState state) { return true; }
    @Override public int getAmount(FluidState state) { return 8; }
    @Override public VoxelShape getShape(FluidState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}

package io.github.sunthemoon.arceadaptertest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No renderer, menu or item is needed by the dedicated-only fixture. */
final class FixtureContainerBlock extends BaseEntityBlock {
    FixtureContainerBlock() {
        super(Properties.of().strength(1.0F));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new FixtureContainerBlockEntity(position, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos at,
            BlockState previous, boolean moving) {
        SealedStagingProbeFixture.observe("external.onPlace");
        super.onPlace(state, level, at, previous, moving);
    }
}

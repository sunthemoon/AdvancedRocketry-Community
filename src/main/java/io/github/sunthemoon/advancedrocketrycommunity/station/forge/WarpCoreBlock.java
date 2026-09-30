package io.github.sunthemoon.advancedrocketrycommunity.station.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ADR-044 warp core: a stateless terminal. Its block entity only exposes Forge Energy, which charges
 * the balance of the station whose committed region contains the core; the block drops itself only.
 */
public final class WarpCoreBlock extends BaseEntityBlock {
    public WarpCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new WarpCoreBlockEntity(position, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}

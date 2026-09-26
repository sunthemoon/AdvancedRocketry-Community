package io.github.sunthemoon.arceadaptertest;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Full collision in both states: only the public atmosphere rule opens this wall. */
public final class FixtureBoundaryBlock extends Block {
    static final BooleanProperty OPEN = BooleanProperty.create("open");

    public FixtureBoundaryBlock() {
        super(Properties.of().strength(1F));
        registerDefaultState(stateDefinition.any().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}

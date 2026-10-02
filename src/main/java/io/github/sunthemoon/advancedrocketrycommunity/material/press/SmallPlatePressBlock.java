package io.github.sunthemoon.advancedrocketrycommunity.material.press;

import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.PistonEvent;

/**
 * The small plate press (ADR-063 section 3): a full block, always facing down, without a block entity. On a rising
 * redstone edge it presses the block below into its recipe output when the block two below is obsidian. It acts like
 * a vanilla piston on the adjacent block, so it posts Forge's cancellable {@link PistonEvent.Pre} first (ADR-061
 * section 6, the piston exception) and stops when a claim mod cancels it.
 */
public final class SmallPlatePressBlock extends Block {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public SmallPlatePressBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos position, Block neighbour, BlockPos from,
                                boolean moving) {
        super.neighborChanged(state, level, position, neighbour, from, moving);
        if (level.isClientSide()) {
            return;
        }
        boolean powered = level.hasNeighborSignal(position);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(position, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && level instanceof ServerLevel serverLevel) {
            press(serverLevel, position);
        }
    }

    /** One operation on a rising edge; the result says what happened, for tests and diagnostics. */
    public static PressResult press(ServerLevel level, BlockPos pressPosition) {
        if (!CommonConfig.smallPlatePressEnabled()) {
            return PressResult.DISABLED;
        }
        BlockPos target = pressPosition.below();
        BlockPos anvil = target.below();
        // The press, its target and the anvil share one chunk column, so the target's chunk is the press's own.
        if (!level.isLoaded(target) || !level.isLoaded(anvil)) {
            return PressResult.UNLOADED;
        }
        if (!level.getBlockState(anvil).is(Blocks.OBSIDIAN)) {
            return PressResult.NO_OBSIDIAN;
        }
        BlockState input = level.getBlockState(target);
        if (input.isAir() || input.hasBlockEntity() || input.getDestroySpeed(level, target) < 0) {
            return PressResult.NO_INPUT;
        }
        ItemStack form = new ItemStack(input.getBlock().asItem());
        if (form.isEmpty()) {
            return PressResult.NO_INPUT;
        }
        SimpleContainer container = new SimpleContainer(form);
        SmallPlatePressRecipe recipe = null;
        for (SmallPlatePressRecipe candidate : level.getRecipeManager().getAllRecipesFor(
                MaterialContent.SMALL_PLATE_PRESS_TYPE.get())) {
            if (candidate.matches(container, level)) {
                if (recipe != null) {
                    // Two data-pack recipes claim the same block; refuse rather than pick by map order.
                    return PressResult.AMBIGUOUS;
                }
                recipe = candidate;
            }
        }
        if (recipe == null) {
            return PressResult.NO_RECIPE;
        }
        if (MinecraftForge.EVENT_BUS.post(new PistonEvent.Pre(level, pressPosition, Direction.DOWN,
                PistonEvent.PistonMoveType.EXTEND))) {
            return PressResult.CANCELLED;
        }
        level.removeBlock(target, false);
        ItemStack output = recipe.assemble(container, level.registryAccess());
        ItemEntity entity = new ItemEntity(level, target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D,
                output);
        entity.setDeltaMovement(0.0D, 0.0D, 0.0D);
        level.addFreshEntity(entity);
        return PressResult.PRESSED;
    }

    public enum PressResult {
        PRESSED,
        DISABLED,
        UNLOADED,
        NO_OBSIDIAN,
        NO_INPUT,
        NO_RECIPE,
        AMBIGUOUS,
        CANCELLED
    }
}

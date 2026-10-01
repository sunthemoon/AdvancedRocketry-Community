package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * ADR-054 section 2 block rules shared by endgame devices: placement creates the device identity and ownership;
 * breaking drops the plain block and the device's local buffers (energy is lost); a quarantined device keeps its
 * buffers and only operators can break it.
 */
public abstract class EndgameDeviceBlock extends BaseEntityBlock {
    protected EndgameDeviceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos position, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, position, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(position) instanceof EndgameDeviceBlockEntity device) {
            device.placedBy(placer);
        }
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos position, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide && level.getBlockEntity(position) instanceof EndgameDeviceBlockEntity device
                && device.quarantined() && !player.hasPermissions(2)) {
            return false;
        }
        return super.onDestroyedByPlayer(state, level, position, player, willHarvest, fluid);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos position, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(position) instanceof EndgameDeviceBlockEntity device && !device.quarantined()) {
            dropLocalBuffers(level, position, device);
        }
        super.onRemove(state, level, position, newState, moved);
    }

    /** Drops the device's input, output, receive and drop buffers; energy is lost. */
    protected abstract void dropLocalBuffers(Level level, BlockPos position, EndgameDeviceBlockEntity device);
}

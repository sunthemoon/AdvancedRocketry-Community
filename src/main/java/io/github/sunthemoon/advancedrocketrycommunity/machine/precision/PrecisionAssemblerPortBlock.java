package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Typed physical port; breaking it drops only its own Item resource once. */
public final class PrecisionAssemblerPortBlock extends BaseEntityBlock {
    private final PrecisionAssemblerPortType portType;

    public PrecisionAssemblerPortBlock(PrecisionAssemblerPortType portType, Properties properties) {
        super(properties);
        this.portType = Objects.requireNonNull(portType, "portType");
    }

    public PrecisionAssemblerPortType portType() {
        return portType;
    }

    @Override
    public void onRemove(
            BlockState state,
            Level level,
            BlockPos position,
            BlockState newState,
            boolean moved
    ) {
        if (level instanceof ServerLevel serverLevel && !state.is(newState.getBlock())) {
            if (!moved && level.getBlockEntity(position) instanceof PrecisionAssemblerPortBlockEntity port) {
                ItemStack stored = port.storedItemCopy();
                if (!stored.isEmpty()) {
                    Containers.dropItemStack(
                            level,
                            position.getX() + 0.5,
                            position.getY() + 0.5,
                            position.getZ() + 0.5,
                            stored
                    );
                }
            }
            PrecisionAssemblerRuntime.markDirty(serverLevel, position);
        }
        super.onRemove(state, level, position, newState, moved);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos position, BlockState state) {
        return new PrecisionAssemblerPortBlockEntity(position, state);
    }
}

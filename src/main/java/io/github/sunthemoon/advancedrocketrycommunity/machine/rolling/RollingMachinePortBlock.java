package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** One of four fixed Rolling Machine port roles. */
public final class RollingMachinePortBlock extends BaseEntityBlock {
    private final RollingMachinePortType portType;

    public RollingMachinePortBlock(RollingMachinePortType portType, Properties properties) {
        super(properties);
        this.portType = java.util.Objects.requireNonNull(portType, "portType");
    }

    public RollingMachinePortType portType() {
        return portType;
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos position,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (!(blockEntity instanceof RollingMachinePortBlockEntity port)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            player.displayClientMessage(
                    Component.translatable(
                            "message.advancedrocketrycommunity.rolling_machine.port",
                            portType.channel(),
                            port.bindingStatusText()
                    ),
                    true
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
            RollingMachineRuntime.markDirty(serverLevel, position);
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
        return new RollingMachinePortBlockEntity(position, state);
    }
}

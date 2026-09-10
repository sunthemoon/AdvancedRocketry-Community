package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.ControllerBindingView;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockLifecycleCoordinator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockLifecycleResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockControllerNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtLoadResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.LoadedPartBindingGateway;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.ServerLevelPatternWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Thin Minecraft lifecycle adapter around the immutable multiblock controller state. */
public final class RollingMachineBlockEntity extends BlockEntity {
    private MultiblockControllerState controllerState;
    private final RollingMachineProcessController process;
    private MultiblockNbtStatus persistenceStatus = MultiblockNbtStatus.SUPPORTED;
    @Nullable
    private Tag preservedControllerRoot;
    @Nullable
    private PatternValidationResult lastValidation;

    public RollingMachineBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ROLLING_MACHINE.get(), position, state);
        controllerState = freshState(state);
        process = new RollingMachineProcessController(this::setChanged);
        process.initialize(controllerState.machineInstanceId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            RollingMachineRuntime.observeController(serverLevel, this);
        }
    }

    void revalidate(ServerLevel level, MultiblockPatternDefinition definition) {
        if (persistenceStatus == MultiblockNbtStatus.UNSUPPORTED_SCHEMA
                || persistenceStatus == MultiblockNbtStatus.INVALID_DATA) {
            return;
        }
        MultiblockLifecycleResult result = MultiblockLifecycleCoordinator.revalidate(
                controllerState,
                definition,
                level.dimension(),
                worldPosition,
                new ServerLevelPatternWorldView(level, RollingMachinePatternRoles.INSTANCE),
                new LoadedPartBindingGateway(new RollingMachineBindingAccess(level))
        );
        lastValidation = result.validation();
        if (!controllerState.equals(result.controllerState())) {
            controllerState = result.controllerState();
            setChanged();
        }
    }

    void invalidateMissingDefinition(ServerLevel level) {
        if (persistenceStatus == MultiblockNbtStatus.UNSUPPORTED_SCHEMA
                || persistenceStatus == MultiblockNbtStatus.INVALID_DATA) {
            return;
        }
        LoadedPartBindingGateway bindings = new LoadedPartBindingGateway(
                new RollingMachineBindingAccess(level)
        );
        MultiblockLifecycleCoordinator.controllerRemoved(
                controllerState,
                level.dimension(),
                worldPosition,
                bindings
        );
        MultiblockControllerState invalid = controllerState.withState(
                MultiblockFormationState.INVALID_DEFINITION,
                Set.of()
        );
        if (!invalid.equals(controllerState)) {
            controllerState = invalid;
            setChanged();
        }
    }

    void markWaitingForUnload() {
        if (persistenceStatus != MultiblockNbtStatus.SUPPORTED
                || controllerState.generation() == 0
                || controllerState.partPositions().isEmpty()) {
            return;
        }
        MultiblockControllerState waiting = controllerState.withState(
                MultiblockFormationState.WAITING_UNLOADED,
                controllerState.partPositions()
        );
        if (!waiting.equals(controllerState)) {
            controllerState = waiting;
            setChanged();
        }
    }

    void unbindForRemoval(ServerLevel level) {
        MultiblockLifecycleCoordinator.controllerRemoved(
                controllerState,
                level.dimension(),
                worldPosition,
                new LoadedPartBindingGateway(new RollingMachineBindingAccess(level))
        );
    }

    public ControllerBindingView bindingView() {
        return new ControllerBindingView(
                controllerState.machineInstanceId(),
                controllerState.generation(),
                controllerState.formationState()
        );
    }

    public MultiblockControllerState controllerState() {
        return controllerState;
    }

    public MultiblockFormationState formationState() {
        return controllerState.formationState();
    }

    public long generation() {
        return controllerState.generation();
    }

    public Optional<PatternValidationResult> lastValidation() {
        return Optional.ofNullable(lastValidation);
    }

    boolean processLocked() {
        return process.locked();
    }

    boolean acceptsResourceAccess() {
        return process.acceptsResourceAccess();
    }

    boolean permitsExternalResourceOperations() {
        return process.permitsExternalResourceOperations();
    }

    boolean tickProcess(ServerLevel level) {
        return process.tick(level, this);
    }

    void recordExternalResourceMutation(ServerLevel level) {
        process.recordExternalMutation(level, this);
    }

    void requireRecoveryAfterUnexpectedTickFailure() {
        process.requireRecoveryAfterUnexpectedTickFailure();
    }

    public ProcessMachineState processState() {
        return process.state();
    }

    public ProcessFailure processFailure() {
        return process.failure();
    }

    public Optional<ProcessProgress> processProgress() {
        return process.progress();
    }

    public long resourceRevision() {
        return process.resourceRevision();
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if (preservedControllerRoot != null) {
            parent.put(MultiblockControllerNbtCodec.ROOT, preservedControllerRoot.copy());
        } else {
            parent.put(
                    MultiblockControllerNbtCodec.ROOT,
                    MultiblockControllerNbtCodec.encode(controllerState)
            );
        }
        process.save(parent);
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        lastValidation = null;
        preservedControllerRoot = null;
        MultiblockNbtLoadResult<MultiblockControllerState> decoded =
                MultiblockControllerNbtCodec.decode(parent);
        persistenceStatus = decoded.status();
        if (decoded.status() == MultiblockNbtStatus.SUPPORTED) {
            controllerState = decoded.value().orElseThrow();
        } else {
            controllerState = freshState(getBlockState());
            if (decoded.status() == MultiblockNbtStatus.EMPTY) {
                persistenceStatus = MultiblockNbtStatus.SUPPORTED;
            } else {
                preservedControllerRoot = decoded.preservedRoot().orElseThrow();
                controllerState = controllerState.withState(
                        MultiblockFormationState.UNSUPPORTED_DATA,
                        Set.of()
                );
            }
        }
        process.load(parent, controllerState.machineInstanceId());
    }

    private static MultiblockControllerState freshState(BlockState state) {
        return MultiblockControllerState.initial(
                UUID.randomUUID(),
                RollingMachineTransforms.forFacing(state.getValue(RollingMachineBlock.FACING))
        );
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.ControllerBindingView;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockControllerState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockLifecycleCoordinator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockLifecycleResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.LoadedPartBindingGateway;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockControllerNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtLoadResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternValidationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternValidator;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
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
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Minecraft lifecycle adapter around the structure and process state owners. */
public final class PrecisionAssemblerBlockEntity extends BlockEntity {
    private MultiblockControllerState controllerState;
    private final PrecisionAssemblerProcessController process;
    private MultiblockNbtStatus persistenceStatus = MultiblockNbtStatus.SUPPORTED;
    @Nullable
    private Tag preservedControllerRoot;
    @Nullable
    private PatternValidationResult lastValidation;

    public PrecisionAssemblerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.PRECISION_ASSEMBLER.get(), position, state);
        controllerState = freshState(state);
        process = new PrecisionAssemblerProcessController(this::setChanged);
        process.initialize(controllerState.machineInstanceId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            PrecisionAssemblerRuntime.observeController(serverLevel, this);
        }
    }

    void revalidate(ServerLevel level, MultiblockPatternDefinition definition) {
        if (persistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            return;
        }
        ServerLevelPatternWorldView world = new ServerLevelPatternWorldView(
                level, PrecisionAssemblerPatternRoles.INSTANCE
        );
        if (controllerState.partPositions().isEmpty()
                && controllerState.formationState() != MultiblockFormationState.FORMED) {
            PatternPosition worldPosition = new PatternPosition(
                    this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ()
            );
            PatternValidationResult selected = MultiblockPatternValidator.validate(
                    definition, controllerState.selectedTransform(), worldPosition, world
            );
            if (!selected.formed()) {
                PatternTransform alternative = new PatternTransform(
                        controllerState.selectedTransform().rotation(),
                        !controllerState.selectedTransform().mirroredLocalX()
                );
                if (MultiblockPatternValidator.validate(definition, alternative, worldPosition, world).formed()) {
                    controllerState = controllerState.selectTransform(alternative);
                    setChanged();
                }
            }
        }
        MultiblockLifecycleResult result = MultiblockLifecycleCoordinator.revalidate(
                controllerState,
                definition,
                level.dimension(),
                worldPosition,
                world,
                new LoadedPartBindingGateway(new PrecisionAssemblerBindingAccess(level))
        );
        lastValidation = result.validation();
        updateState(level, result.controllerState());
    }

    void invalidateMissingDefinition(ServerLevel level) {
        if (persistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            return;
        }
        MultiblockLifecycleCoordinator.controllerRemoved(
                controllerState,
                level.dimension(),
                worldPosition,
                new LoadedPartBindingGateway(new PrecisionAssemblerBindingAccess(level))
        );
        updateState(level, controllerState.withState(MultiblockFormationState.INVALID_DEFINITION, Set.of()));
    }

    void markWaitingForUnload(ServerLevel level) {
        if (persistenceStatus == MultiblockNbtStatus.SUPPORTED
                && controllerState.generation() > 0
                && !controllerState.partPositions().isEmpty()) {
            updateState(level, controllerState.withState(
                    MultiblockFormationState.WAITING_UNLOADED,
                    controllerState.partPositions()
            ));
        }
    }

    void unbindForRemoval(ServerLevel level) {
        MultiblockLifecycleCoordinator.controllerRemoved(
                controllerState,
                level.dimension(),
                worldPosition,
                new LoadedPartBindingGateway(new PrecisionAssemblerBindingAccess(level))
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

    boolean acceptsResourceAccess() {
        return persistenceStatus == MultiblockNbtStatus.SUPPORTED
                && process.acceptsResourceAccess()
                && controllerState.formationState() == MultiblockFormationState.FORMED;
    }

    boolean processLocked() {
        return process.locked();
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

    private void updateState(ServerLevel level, MultiblockControllerState replacement) {
        if (controllerState.equals(replacement)) {
            return;
        }
        Set<BlockPos> oldParts = controllerState.partPositions();
        controllerState = replacement;
        setChanged();
        for (BlockPos position : oldParts) {
            notifyLoadedPort(level, position);
        }
        for (BlockPos position : replacement.partPositions()) {
            if (!oldParts.contains(position)) {
                notifyLoadedPort(level, position);
            }
        }
    }

    private static void notifyLoadedPort(ServerLevel level, BlockPos position) {
        if (level.hasChunkAt(position)
                && level.getBlockEntity(position) instanceof PrecisionAssemblerPortBlockEntity port) {
            port.controllerLifecycleChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        parent.put(
                MultiblockControllerNbtCodec.ROOT,
                preservedControllerRoot != null
                        ? preservedControllerRoot.copy()
                        : MultiblockControllerNbtCodec.encode(controllerState)
        );
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
        Direction facing = state.getValue(PrecisionAssemblerBlock.FACING);
        PatternRotation rotation = switch (facing) {
            case NORTH -> PatternRotation.ZERO;
            case EAST -> PatternRotation.CLOCKWISE_90;
            case SOUTH -> PatternRotation.CLOCKWISE_180;
            case WEST -> PatternRotation.CLOCKWISE_270;
            default -> throw new IllegalArgumentException("precision assembler facing must be horizontal");
        };
        return MultiblockControllerState.initial(UUID.randomUUID(), new PatternTransform(rotation, false));
    }
}

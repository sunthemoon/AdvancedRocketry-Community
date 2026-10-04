package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.ControllerBindingView;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockBindingGateway;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Minecraft lifecycle adapter around the structure and process state owners. */
public final class PrecisionAssemblerBlockEntity extends BlockEntity implements MenuProvider,
        io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureProtected {
    private MultiblockControllerState controllerState;
    private final PrecisionAssemblerProcessController process;
    private final PrecisionAssemblerItemBank items;
    private MultiblockNbtStatus persistenceStatus = MultiblockNbtStatus.SUPPORTED;
    private MultiblockNbtStatus resourcePersistenceStatus = MultiblockNbtStatus.SUPPORTED;
    private PrecisionAssemblerResourcePersistence.Phase resourcePhase =
            PrecisionAssemblerResourcePersistence.Phase.ACTIVE;
    @Nullable
    private Tag preservedControllerRoot;
    @Nullable
    private Tag preservedResourceRoot;
    @Nullable
    private PatternValidationResult lastValidation;

    public PrecisionAssemblerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.PRECISION_ASSEMBLER.get(), position, state);
        controllerState = freshState(state);
        process = new PrecisionAssemblerProcessController(this::setChanged);
        process.initialize(controllerState.machineInstanceId());
        items = new PrecisionAssemblerItemBank(this::setChanged);
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
        MultiblockBindingGateway bindings = new LoadedPartBindingGateway(new PrecisionAssemblerBindingAccess(level));
        if (blocksResourceRemoval()) {
            if (resourcePersistenceStatus == MultiblockNbtStatus.SUPPORTED
                    && resourcePhase == PrecisionAssemblerResourcePersistence.Phase.PREPARING) {
                PrecisionAssemblerMigrationBindings.restorePendingBindings(level, this);
            }
            bindings = new PrecisionAssemblerMigrationBindings(bindings);
        }
        MultiblockLifecycleResult result = MultiblockLifecycleCoordinator.revalidate(
                controllerState,
                definition,
                level.dimension(),
                worldPosition,
                world,
                bindings
        );
        lastValidation = result.validation();
        updateState(level, blocksResourceRemoval()
                ? controllerState.withState(result.validation().formed()
                        ? result.controllerState().formationState()
                        : result.controllerState().formationState() == MultiblockFormationState.WAITING_UNLOADED
                                ? MultiblockFormationState.WAITING_UNLOADED
                                : MultiblockFormationState.BINDING_CONFLICT, controllerState.partPositions())
                : result.controllerState());
    }

    void invalidateMissingDefinition(ServerLevel level) {
        if (persistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            return;
        }
        if (blocksResourceRemoval()) {
            updateState(level, controllerState.withState(
                    MultiblockFormationState.BINDING_CONFLICT, controllerState.partPositions()));
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
        return acceptsPortBindingAccess()
                && resourcePersistenceStatus == MultiblockNbtStatus.SUPPORTED
                && resourcePhase == PrecisionAssemblerResourcePersistence.Phase.ACTIVE;
    }

    boolean acceptsPortBindingAccess() {
        return persistenceStatus == MultiblockNbtStatus.SUPPORTED
                && process.acceptsResourceAccess()
                && controllerState.formationState() == MultiblockFormationState.FORMED;
    }

    boolean ownsItems() {
        return resourcePersistenceStatus == MultiblockNbtStatus.SUPPORTED
                && resourcePhase == PrecisionAssemblerResourcePersistence.Phase.ACTIVE;
    }

    boolean blocksResourceRemoval() {
        return process.preservesRecipeInput() || persistenceStatus != MultiblockNbtStatus.SUPPORTED
                || (resourcePersistenceStatus != MultiblockNbtStatus.EMPTY && !ownsItems());
    }

    ItemStack storedItemCopy(int slot) {
        return items.storedCopy(slot);
    }

    PrecisionAssemblerItemBank itemBank() {
        return items;
    }

    void replaceStoredItemInternal(int slot, ItemStack replacement) {
        if (!ownsItems()) {
            throw new IllegalStateException("Precision Assembler controller Item resources are unavailable");
        }
        items.replaceStored(slot, replacement);
    }

    void dropAllItemsForRemoval() {
        if (!ownsItems() || level == null) {
            return;
        }
        for (int slot = 0; slot < PrecisionAssemblerResourcePersistence.ITEM_COUNT; slot++) {
            ItemStack stored = items.storedCopy(slot);
            if (!stored.isEmpty()) {
                items.replaceStored(slot, ItemStack.EMPTY);
                Containers.dropItemStack(level,
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D,
                        stored);
            }
        }
    }

    void dropItemFromPort(int slot) {
        if (!acceptsResourceAccess() || level == null) {
            return;
        }
        ItemStack stored = items.storedCopy(slot);
        if (!stored.isEmpty()) {
            items.replaceStored(slot, ItemStack.EMPTY);
            Containers.dropItemStack(level,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D,
                    stored);
        }
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

    boolean needsResourceMigration() {
        return resourcePersistenceStatus == MultiblockNbtStatus.EMPTY
                || (resourcePersistenceStatus == MultiblockNbtStatus.SUPPORTED
                && resourcePhase == PrecisionAssemblerResourcePersistence.Phase.PREPARING);
    }

    boolean prepareLegacyItems(ServerLevel level) {
        if (!needsResourceMigration() || !acceptsPortBindingAccess()) {
            return false;
        }
        Optional<PrecisionAssemblerPortSet> found = PrecisionAssemblerPortSet.resolveForMigration(level, this);
        if (found.isEmpty()) {
            return false;
        }
        PrecisionAssemblerPortSet ports = found.orElseThrow();
        if (resourcePersistenceStatus == MultiblockNbtStatus.EMPTY) {
            if (ports.inputs().stream().anyMatch(port -> port.migrationMarker().isPresent())
                    || ports.outputs().stream().anyMatch(port -> port.migrationMarker().isPresent())) {
                return false;
            }
            List<ItemStack> legacy = new ArrayList<>(PrecisionAssemblerResourcePersistence.ITEM_COUNT);
            ports.inputs().forEach(port -> legacy.add(port.legacyStoredItemCopy()));
            ports.outputs().forEach(port -> legacy.add(port.legacyStoredItemCopy()));
            items.loadStored(legacy);
            resourcePhase = PrecisionAssemblerResourcePersistence.Phase.PREPARING;
            resourcePersistenceStatus = MultiblockNbtStatus.SUPPORTED;
            setChanged();
        }
        return legacyPortsMatch(ports);
    }

    boolean markLegacyPorts(ServerLevel level) {
        if (resourcePersistenceStatus != MultiblockNbtStatus.SUPPORTED
                || resourcePhase != PrecisionAssemblerResourcePersistence.Phase.PREPARING) {
            return false;
        }
        Optional<PrecisionAssemblerPortSet> found = PrecisionAssemblerPortSet.resolveForMigration(level, this);
        if (found.isEmpty() || !legacyPortsMatch(found.orElseThrow())) {
            return false;
        }
        PrecisionAssemblerPortSet ports = found.orElseThrow();
        UUID machineId = controllerState.machineInstanceId();
        for (int index = 0; index < ports.inputs().size(); index++) {
            ports.inputs().get(index).markLegacyMigrated(machineId, PrecisionAssemblerChannels.input(index));
        }
        for (int index = 0; index < ports.outputs().size(); index++) {
            ports.outputs().get(index).markLegacyMigrated(machineId, PrecisionAssemblerChannels.output(index));
        }
        // Binding repair also touches Energy. A failed first flush can clear
        // its dirty flag even when no Item port shares that chunk.
        ports.energy().setChanged();
        return allLegacyMarkersPresent(ports);
    }

    boolean activateMigratedItems(ServerLevel level) {
        if (resourcePersistenceStatus != MultiblockNbtStatus.SUPPORTED
                || resourcePhase != PrecisionAssemblerResourcePersistence.Phase.PREPARING) {
            return false;
        }
        Optional<PrecisionAssemblerPortSet> found = PrecisionAssemblerPortSet.resolveForMigration(level, this);
        if (found.isEmpty() || !legacyPortsMatch(found.orElseThrow())
                || !allLegacyMarkersPresent(found.orElseThrow())) {
            return false;
        }
        resourcePhase = PrecisionAssemblerResourcePersistence.Phase.ACTIVE;
        setChanged();
        found.orElseThrow().inputs().forEach(PrecisionAssemblerPortBlockEntity::controllerLifecycleChanged);
        found.orElseThrow().outputs().forEach(PrecisionAssemblerPortBlockEntity::controllerLifecycleChanged);
        return true;
    }

    private boolean legacyPortsMatch(PrecisionAssemblerPortSet ports) {
        for (int index = 0; index < ports.inputs().size(); index++) {
            if (!ItemStack.matches(items.storedCopy(index), ports.inputs().get(index).legacyStoredItemCopy())
                    || !markerMatches(ports.inputs().get(index), PrecisionAssemblerChannels.input(index))) {
                return false;
            }
        }
        for (int index = 0; index < ports.outputs().size(); index++) {
            if (!ItemStack.matches(items.storedCopy(PrecisionAssemblerChannels.INPUT_COUNT + index),
                    ports.outputs().get(index).legacyStoredItemCopy())
                    || !markerMatches(ports.outputs().get(index), PrecisionAssemblerChannels.output(index))) {
                return false;
            }
        }
        return true;
    }

    private boolean markerMatches(PrecisionAssemblerPortBlockEntity port, String channel) {
        return port.migrationMarker().map(marker ->
                marker.machineId().equals(controllerState.machineInstanceId())
                        && marker.channel().equals(channel)).orElse(true);
    }

    private boolean allLegacyMarkersPresent(PrecisionAssemblerPortSet ports) {
        return ports.inputs().stream().allMatch(port -> port.migrationMarker().isPresent())
                && ports.outputs().stream().allMatch(port -> port.migrationMarker().isPresent());
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

    int totalProcessingTicks() {
        return level instanceof ServerLevel serverLevel
                ? process.totalProcessingTicks(serverLevel) : 0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.advancedrocketrycommunity.precision_assembler");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return PrecisionAssemblerMenu.canOpen(this, player)
                ? new PrecisionAssemblerMenu(containerId, inventory, this)
                : null;
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
        if (preservedResourceRoot != null) {
            parent.put(PrecisionAssemblerResourcePersistence.ROOT, preservedResourceRoot.copy());
        } else if (resourcePersistenceStatus == MultiblockNbtStatus.EMPTY) {
            parent.remove(PrecisionAssemblerResourcePersistence.ROOT);
        } else {
            parent.put(PrecisionAssemblerResourcePersistence.ROOT,
                    PrecisionAssemblerResourcePersistence.encode(
                            new PrecisionAssemblerResourcePersistence.ResourceData(
                                    controllerState.machineInstanceId(), resourcePhase, items.storedCopies())));
        }
    }

    @Override
    public boolean preservesRecipeInput() { return blocksResourceRemoval(); }

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
        loadResources(parent, decoded.status());
    }

    private void loadResources(CompoundTag parent, MultiblockNbtStatus controllerLoadStatus) {
        items.loadStored(java.util.Collections.nCopies(
                PrecisionAssemblerResourcePersistence.ITEM_COUNT, ItemStack.EMPTY));
        preservedResourceRoot = null;
        var decoded = PrecisionAssemblerResourcePersistence.decode(
                parent, controllerState.machineInstanceId());
        resourcePersistenceStatus = decoded.status();
        if (decoded.status() == MultiblockNbtStatus.SUPPORTED) {
            var data = decoded.value().orElseThrow();
            resourcePhase = data.phase();
            items.loadStored(data.items());
        } else if (decoded.status() == MultiblockNbtStatus.EMPTY
                && controllerLoadStatus == MultiblockNbtStatus.EMPTY) {
            resourcePersistenceStatus = MultiblockNbtStatus.SUPPORTED;
            resourcePhase = PrecisionAssemblerResourcePersistence.Phase.ACTIVE;
        } else if (decoded.status() != MultiblockNbtStatus.EMPTY) {
            preservedResourceRoot = decoded.preservedRoot().orElseThrow();
        }
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

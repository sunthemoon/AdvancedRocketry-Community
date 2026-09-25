package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.ControllerBindingView;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingVerifier;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.MultiblockPartBindingTarget;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtLoadResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockPartBindingNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortFilter;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRange;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortSide;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessCapabilityCache;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessEnergyPortStorage;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessItemPortHandler;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

/** Per-port resources and generation-scoped, loaded-only automation access. */
public final class PrecisionAssemblerPortBlockEntity extends BlockEntity
        implements MultiblockPartBindingTarget {
    private static final Set<ProcessPortSide> ALL_SIDES = Set.of(
            ProcessPortSide.FRONT,
            ProcessPortSide.BACK,
            ProcessPortSide.LEFT,
            ProcessPortSide.RIGHT,
            ProcessPortSide.TOP,
            ProcessPortSide.BOTTOM
    );

    private final PrecisionAssemblerItemStorage itemStorage = new PrecisionAssemblerItemStorage(this::setChanged);
    private final PrecisionAssemblerEnergyStorage energyStorage = new PrecisionAssemblerEnergyStorage(this::setChanged);
    private final ProcessPortRevision externalRevision = new ProcessPortRevision(
            0,
            this::recordExternalMutation
    );

    private Optional<MultiblockPartBinding> binding = Optional.empty();
    private MultiblockNbtStatus bindingPersistenceStatus = MultiblockNbtStatus.SUPPORTED;
    private MultiblockNbtStatus resourcePersistenceStatus = MultiblockNbtStatus.SUPPORTED;
    private ProcessCapabilityCache capabilityCache;
    @Nullable
    private IItemHandler menuItemView;
    private long capabilityEpoch;
    @Nullable
    private Tag preservedBindingRoot;
    @Nullable
    private Tag preservedResourceRoot;

    public PrecisionAssemblerPortBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.PRECISION_ASSEMBLER_PORT.get(), position, state);
        createCapabilityViews();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            PrecisionAssemblerRuntime.markDirty(serverLevel, worldPosition);
        }
    }

    public PrecisionAssemblerPortType portType() {
        if (getBlockState().getBlock() instanceof PrecisionAssemblerPortBlock port) {
            return port.portType();
        }
        throw new IllegalStateException("precision port BlockEntity has an incompatible block state");
    }

    boolean acceptsBindingMutations() {
        return bindingPersistenceStatus == MultiblockNbtStatus.SUPPORTED
                && resourcePersistenceStatus == MultiblockNbtStatus.SUPPORTED;
    }

    @Override
    public Optional<MultiblockPartBinding> multiblockBinding() {
        return binding;
    }

    @Override
    public void setMultiblockBinding(Optional<MultiblockPartBinding> replacement) {
        if (!acceptsBindingMutations()) {
            throw new IllegalStateException("precision port persisted data is blocked");
        }
        Optional<MultiblockPartBinding> checked = java.util.Objects.requireNonNull(replacement, "replacement");
        if (!binding.equals(checked)) {
            binding = checked;
            setChanged();
            refreshCapabilityViews();
            notifyCapabilityNeighbors();
        }
    }

    public Optional<PartBindingValidationStatus> bindingStatus() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Optional.empty();
        }
        return binding.map(value -> PartBindingVerifier.verify(
                value,
                serverLevel.dimension(),
                serverLevel::hasChunkAt,
                position -> loadedControllerView(serverLevel, position)
        ));
    }

    public MultiblockNbtStatus resourcePersistenceStatus() {
        return resourcePersistenceStatus;
    }

    public Optional<String> assignedChannel() {
        return loadedAssignment().map(PrecisionAssemblerPortLayout.Assignment::channel);
    }

    ItemStack storedItemCopy() {
        return itemStorage.storedCopy();
    }

    int storedEnergy() {
        return energyStorage.getEnergyStored();
    }

    Optional<IItemHandler> menuItemView() {
        return Optional.ofNullable(menuItemView);
    }

    void replaceStoredItemInternal(ItemStack replacement) {
        if (portType().kind() != ProcessPortKind.ITEM
                || resourcePersistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            throw new IllegalStateException("precision port cannot accept internal Item replacement");
        }
        itemStorage.replaceStored(replacement);
    }

    boolean consumeEnergyInternal(int amount) {
        return portType() == PrecisionAssemblerPortType.ENERGY_INPUT
                && resourcePersistenceStatus == MultiblockNbtStatus.SUPPORTED
                && energyStorage.consumeInternal(amount);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(
            @Nonnull Capability<T> capability,
            @Nullable Direction side
    ) {
        if (capabilityMatchesPort(capability) && capabilityAccessAllowed()) {
            return capabilityCache.get(capability, side);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        invalidateCapabilityViews();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        if (!capabilityCache.isValid()) {
            createCapabilityViews();
        }
    }

    void controllerLifecycleChanged() {
        refreshCapabilityViews();
        notifyCapabilityNeighbors();
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if (preservedBindingRoot != null) {
            parent.put(MultiblockPartBindingNbtCodec.ROOT, preservedBindingRoot.copy());
        } else if (binding.isPresent()) {
            parent.put(MultiblockPartBindingNbtCodec.ROOT,
                    MultiblockPartBindingNbtCodec.encode(binding.orElseThrow()));
        } else {
            parent.remove(MultiblockPartBindingNbtCodec.ROOT);
        }
        parent.put(
                PrecisionAssemblerPortPersistence.ROOT,
                preservedResourceRoot != null
                        ? preservedResourceRoot.copy()
                        : PrecisionAssemblerPortPersistence.encode(
                                portType(), itemStorage.storedCopy(), energyStorage.getEnergyStored())
        );
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        loadBinding(parent);
        loadResources(parent);
        refreshCapabilityViews();
    }

    private void loadBinding(CompoundTag parent) {
        binding = Optional.empty();
        preservedBindingRoot = null;
        MultiblockNbtLoadResult<MultiblockPartBinding> decoded = MultiblockPartBindingNbtCodec.decode(parent);
        bindingPersistenceStatus = normalizeEmpty(decoded.status());
        if (decoded.status() == MultiblockNbtStatus.SUPPORTED) {
            binding = decoded.value();
        } else if (decoded.status() != MultiblockNbtStatus.EMPTY) {
            preservedBindingRoot = decoded.preservedRoot().orElseThrow();
        }
    }

    private void loadResources(CompoundTag parent) {
        itemStorage.loadStored(ItemStack.EMPTY);
        energyStorage.loadStored(0);
        preservedResourceRoot = null;
        PrecisionAssemblerPortPersistence.DecodeResult decoded =
                PrecisionAssemblerPortPersistence.decode(parent, portType());
        resourcePersistenceStatus = normalizeEmpty(decoded.status());
        if (decoded.status() == MultiblockNbtStatus.SUPPORTED) {
            PrecisionAssemblerPortPersistence.PortData data = decoded.value().orElseThrow();
            itemStorage.loadStored(data.item());
            energyStorage.loadStored(data.energy());
        } else if (decoded.status() != MultiblockNbtStatus.EMPTY) {
            preservedResourceRoot = decoded.preservedRoot().orElseThrow();
        }
    }

    private boolean capabilityMatchesPort(Capability<?> capability) {
        return switch (portType().kind()) {
            case ITEM -> capability == ForgeCapabilities.ITEM_HANDLER;
            case ENERGY -> capability == ForgeCapabilities.ENERGY;
            case FLUID -> false;
        };
    }

    private boolean capabilityAccessAllowed() {
        return !isRemoved() && acceptsBindingMutations() && loadedAssignment().isPresent();
    }

    private boolean capabilityOperationAllowed() {
        return capabilityAccessAllowed() && loadedFormedController()
                .map(PrecisionAssemblerBlockEntity::permitsExternalResourceOperations)
                .orElse(false);
    }

    private boolean processLocked() {
        return loadedFormedController().map(PrecisionAssemblerBlockEntity::processLocked)
                .orElse(true);
    }

    private Optional<PrecisionAssemblerPortLayout.Assignment> loadedAssignment() {
        return loadedFormedController().flatMap(controller -> PrecisionAssemblerPortLayout.atWorld(
                controller.controllerState().selectedTransform(),
                patternPosition(controller.getBlockPos()),
                patternPosition(worldPosition)
        )).filter(portType()::accepts);
    }

    private Optional<PrecisionAssemblerBlockEntity> loadedFormedController() {
        if (!(level instanceof ServerLevel serverLevel) || binding.isEmpty()) {
            return Optional.empty();
        }
        MultiblockPartBinding expected = binding.orElseThrow();
        if (!expected.controllerLevel().equals(serverLevel.dimension())
                || !serverLevel.hasChunkAt(expected.controllerPosition())) {
            return Optional.empty();
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(expected.controllerPosition());
        if (!(blockEntity instanceof PrecisionAssemblerBlockEntity controller)
                || !controller.controllerState().machineInstanceId().equals(expected.machineInstanceId())
                || controller.generation() != expected.generation()
                || !controller.controllerState().partPositions().contains(worldPosition)
                || !controller.acceptsResourceAccess()) {
            return Optional.empty();
        }
        return Optional.of(controller);
    }

    private void createCapabilityViews() {
        capabilityCache = new ProcessCapabilityCache();
        long viewEpoch = capabilityEpoch;
        ProcessPortDefinition definition = new ProcessPortDefinition(
                portType().patternChannel(),
                portType().kind(),
                portType().mode(),
                ALL_SIDES,
                new ProcessPortRange(0, 1),
                ProcessPortFilter.any()
        );
        if (portType().kind() == ProcessPortKind.ITEM) {
            registerEverySide(ForgeCapabilities.ITEM_HANDLER, new ProcessItemPortHandler(
                    itemStorage,
                    definition,
                    this::processLocked,
                    () -> viewEpoch == capabilityEpoch && capabilityOperationAllowed(),
                    externalRevision
            ));
            ProcessPortMode menuMode = portType() == PrecisionAssemblerPortType.ITEM_INPUT
                    ? ProcessPortMode.BIDIRECTIONAL : ProcessPortMode.OUTPUT;
            menuItemView = new ProcessItemPortHandler(
                    itemStorage,
                    new ProcessPortDefinition(
                            portType().patternChannel(),
                            ProcessPortKind.ITEM,
                            menuMode,
                            ALL_SIDES,
                            new ProcessPortRange(0, 1),
                            ProcessPortFilter.any()
                    ),
                    this::processLocked,
                    () -> viewEpoch == capabilityEpoch && capabilityOperationAllowed(),
                    externalRevision
            );
        } else {
            registerEverySide(ForgeCapabilities.ENERGY, new ProcessEnergyPortStorage(
                    energyStorage,
                    definition,
                    () -> false,
                    () -> viewEpoch == capabilityEpoch && capabilityOperationAllowed(),
                    externalRevision
            ));
        }
    }

    private <T> void registerEverySide(Capability<T> capability, T view) {
        capabilityCache.register(capability, null, () -> view);
        for (Direction side : Direction.values()) {
            capabilityCache.register(capability, side, () -> view);
        }
    }

    private void refreshCapabilityViews() {
        invalidateCapabilityViews();
        if (!isRemoved()) {
            createCapabilityViews();
        }
    }

    private void invalidateCapabilityViews() {
        capabilityEpoch = Math.incrementExact(capabilityEpoch);
        menuItemView = null;
        if (capabilityCache != null) {
            capabilityCache.invalidate();
        }
    }

    private void notifyCapabilityNeighbors() {
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private void recordExternalMutation() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            loadedFormedController().ifPresent(controller ->
                    controller.recordExternalResourceMutation(serverLevel));
        }
    }

    private static MultiblockNbtStatus normalizeEmpty(MultiblockNbtStatus status) {
        return status == MultiblockNbtStatus.EMPTY ? MultiblockNbtStatus.SUPPORTED : status;
    }

    private static Optional<ControllerBindingView> loadedControllerView(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof PrecisionAssemblerBlockEntity controller
                ? Optional.of(controller.bindingView())
                : Optional.empty();
    }

    private static PatternPosition patternPosition(BlockPos position) {
        return new PatternPosition(position.getX(), position.getY(), position.getZ());
    }

}

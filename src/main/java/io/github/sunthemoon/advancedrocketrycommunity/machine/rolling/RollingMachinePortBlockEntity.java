package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.ControllerBindingView;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingVerifier;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.forge.MultiblockPartBindingTarget;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtLoadResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockPartBindingNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortFilter;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortMode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRange;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortRevision;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortSide;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessCapabilityCache;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessEnergyPortStorage;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessFluidPortHandler;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge.ProcessItemPortHandler;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.List;
import java.util.Locale;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;

/** Typed resource port with generation-aware, loaded-only controller access. */
public final class RollingMachinePortBlockEntity extends BlockEntity
        implements MultiblockPartBindingTarget,
        io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureProtected {
    public static final int FLUID_CAPACITY = 4_000;
    public static final int ENERGY_CAPACITY = 20_000;
    public static final int ENERGY_RECEIVE_LIMIT = 1_000;

    private final RollingMachineItemStorage itemStorage = new RollingMachineItemStorage(
            this::markResourceStorageChanged
    );
    private final RollingMachineFluidStorage fluidStorage = new RollingMachineFluidStorage(
            this::markResourceStorageChanged
    );
    private final RollingMachineEnergyStorage energyStorage = new RollingMachineEnergyStorage(
            this::markResourceStorageChanged
    );
    private final ProcessPortRevision externalRevision = new ProcessPortRevision(
            0,
            this::recordExternalResourceMutation
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

    public RollingMachinePortBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ROLLING_MACHINE_PORT.get(), position, state);
        createCapabilityViews();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            RollingMachineRuntime.markDirty(serverLevel, worldPosition);
        }
    }

    public RollingMachinePortType portType() {
        if (getBlockState().getBlock() instanceof RollingMachinePortBlock port) {
            return port.portType();
        }
        throw new IllegalStateException("rolling port BlockEntity has an incompatible block state");
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
            throw new IllegalStateException("rolling port persisted data is blocked");
        }
        Optional<MultiblockPartBinding> checked = java.util.Objects.requireNonNull(
                replacement,
                "replacement"
        );
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

    String bindingStatusText() {
        if (resourcePersistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            return "resource_" + resourcePersistenceStatus.name().toLowerCase(Locale.ROOT);
        }
        if (bindingPersistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            return bindingPersistenceStatus.name().toLowerCase(Locale.ROOT);
        }
        return bindingStatus()
                .map(status -> status.name().toLowerCase(Locale.ROOT))
                .orElse("unbound");
    }

    ItemStack storedItemCopy() {
        return itemStorage.storedCopy();
    }

    FluidStack storedFluidCopy() {
        return fluidStorage.storedCopy();
    }

    int storedEnergy() {
        return energyStorage.getEnergyStored();
    }

    Optional<IItemHandler> menuItemView() {
        return Optional.ofNullable(menuItemView);
    }

    void replaceStoredItemInternal(ItemStack replacement) {
        if (portType().kind() != io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind.ITEM
                || resourcePersistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            throw new IllegalStateException("Rolling Machine port cannot accept an internal Item replacement");
        }
        itemStorage.replaceStored(replacement);
    }

    void replaceStoredFluidInternal(FluidStack replacement) {
        if (portType() != RollingMachinePortType.FLUID_INPUT
                || resourcePersistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            throw new IllegalStateException("Rolling Machine port cannot accept an internal Fluid replacement");
        }
        fluidStorage.replaceStored(replacement);
    }

    boolean consumeEnergyInternal(int amount) {
        if (portType() != RollingMachinePortType.ENERGY_INPUT
                || resourcePersistenceStatus != MultiblockNbtStatus.SUPPORTED) {
            return false;
        }
        return energyStorage.consumeInternal(amount);
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

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        writeBinding(parent);
        if (preservedResourceRoot != null) {
            parent.put(RollingMachinePortPersistence.ROOT, preservedResourceRoot.copy());
        } else {
            parent.put(
                    RollingMachinePortPersistence.ROOT,
                    RollingMachinePortPersistence.encode(
                            portType(),
                            itemStorage.storedCopy(),
                            fluidStorage.storedCopy(),
                            energyStorage.getEnergyStored()
                    )
            );
        }
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        loadBinding(parent);
        loadResources(parent);
        refreshCapabilityViews();
    }

    private void writeBinding(CompoundTag parent) {
        if (preservedBindingRoot != null) {
            parent.put(MultiblockPartBindingNbtCodec.ROOT, preservedBindingRoot.copy());
        } else if (binding.isPresent()) {
            parent.put(
                    MultiblockPartBindingNbtCodec.ROOT,
                    MultiblockPartBindingNbtCodec.encode(binding.orElseThrow())
            );
        } else {
            parent.remove(MultiblockPartBindingNbtCodec.ROOT);
        }
    }

    private void loadBinding(CompoundTag parent) {
        binding = Optional.empty();
        preservedBindingRoot = null;
        MultiblockNbtLoadResult<MultiblockPartBinding> decoded =
                MultiblockPartBindingNbtCodec.decode(parent);
        bindingPersistenceStatus = normalizeEmpty(decoded.status());
        if (decoded.status() == MultiblockNbtStatus.SUPPORTED) {
            binding = decoded.value();
        } else if (decoded.status() != MultiblockNbtStatus.EMPTY) {
            preservedBindingRoot = decoded.preservedRoot().orElseThrow();
        }
    }

    private void loadResources(CompoundTag parent) {
        itemStorage.loadStored(ItemStack.EMPTY);
        fluidStorage.loadStored(FluidStack.EMPTY);
        energyStorage.loadStored(0);
        preservedResourceRoot = null;

        RollingMachinePortPersistence.DecodeResult decoded =
                RollingMachinePortPersistence.decode(parent, portType());
        resourcePersistenceStatus = normalizeEmpty(decoded.status());
        if (decoded.status() == MultiblockNbtStatus.SUPPORTED) {
            RollingMachinePortPersistence.PortData data = decoded.value().orElseThrow();
            itemStorage.loadStored(data.item());
            fluidStorage.loadStored(data.fluid());
            energyStorage.loadStored(data.energy());
        } else if (decoded.status() != MultiblockNbtStatus.EMPTY) {
            preservedResourceRoot = decoded.preservedRoot().orElseThrow();
        }
    }

    private boolean capabilityMatchesPort(Capability<?> capability) {
        return switch (portType().kind()) {
            case ITEM -> capability == ForgeCapabilities.ITEM_HANDLER;
            case FLUID -> capability == ForgeCapabilities.FLUID_HANDLER;
            case ENERGY -> capability == ForgeCapabilities.ENERGY;
        };
    }

    private boolean capabilityAccessAllowed() {
        return acceptsBindingMutations() && loadedFormedController().isPresent();
    }

    private boolean capabilityOperationAllowed() {
        return acceptsBindingMutations() && loadedFormedController()
                .map(RollingMachineBlockEntity::permitsExternalResourceOperations)
                .orElse(false);
    }

    private boolean processLocked() {
        return loadedFormedController()
                .map(RollingMachineBlockEntity::processLocked)
                .orElse(true);
    }

    @Override
    public boolean preservesRecipeInput() {
        if (!acceptsBindingMutations()) { return true; }
        if (!(level instanceof ServerLevel serverLevel) || binding.isEmpty()) { return false; }
        MultiblockPartBinding expected = binding.orElseThrow();
        if (!expected.controllerLevel().equals(serverLevel.dimension())
                || !serverLevel.hasChunkAt(expected.controllerPosition())) { return true; }
        BlockEntity owner = serverLevel.getBlockEntity(expected.controllerPosition());
        return !(owner instanceof RollingMachineBlockEntity controller)
                || !controller.controllerState().machineInstanceId().equals(expected.machineInstanceId())
                || controller.preservesRecipeInput();
    }

    private Optional<RollingMachineBlockEntity> loadedFormedController() {
        if (!(level instanceof ServerLevel serverLevel) || binding.isEmpty()) {
            return Optional.empty();
        }
        MultiblockPartBinding expected = binding.orElseThrow();
        if (!expected.controllerLevel().equals(serverLevel.dimension())
                || !serverLevel.hasChunkAt(expected.controllerPosition())) {
            return Optional.empty();
        }
        BlockEntity blockEntity = serverLevel.getBlockEntity(expected.controllerPosition());
        if (!(blockEntity instanceof RollingMachineBlockEntity controller)
                || !controller.controllerState().machineInstanceId().equals(expected.machineInstanceId())
                || controller.generation() != expected.generation()
                || !controller.controllerState().partPositions().contains(worldPosition)
                || !controller.acceptsResourceAccess()
                || controller.formationState() != MultiblockFormationState.FORMED) {
            return Optional.empty();
        }
        return Optional.of(controller);
    }

    private void createCapabilityViews() {
        capabilityCache = new ProcessCapabilityCache();
        ProcessPortDefinition definition = definition();
        long viewEpoch = capabilityEpoch;
        switch (portType().kind()) {
            case ITEM -> {
                registerEverySide(
                        ForgeCapabilities.ITEM_HANDLER,
                        new ProcessItemPortHandler(
                                itemStorage,
                                definition,
                                this::processLocked,
                                () -> viewEpoch == capabilityEpoch && capabilityOperationAllowed(),
                                externalRevision
                        )
                );
                menuItemView = new ProcessItemPortHandler(
                            itemStorage,
                            definition(menuMode()),
                            this::processLocked,
                            () -> viewEpoch == capabilityEpoch && capabilityOperationAllowed(),
                            externalRevision
                );
            }
            case FLUID -> registerEverySide(
                    ForgeCapabilities.FLUID_HANDLER,
                    new ProcessFluidPortHandler(
                            List.of(fluidStorage),
                            definition,
                            this::processLocked,
                            () -> viewEpoch == capabilityEpoch && capabilityOperationAllowed(),
                            externalRevision
                    )
            );
            case ENERGY -> registerEverySide(
                    ForgeCapabilities.ENERGY,
                    new ProcessEnergyPortStorage(
                            energyStorage,
                            definition,
                            this::processLocked,
                            () -> viewEpoch == capabilityEpoch && capabilityOperationAllowed(),
                            externalRevision
                    )
            );
        }
    }

    private ProcessPortDefinition definition() {
        return definition(portType().mode());
    }

    private ProcessPortDefinition definition(ProcessPortMode mode) {
        ProcessPortFilter filter = portType() == RollingMachinePortType.FLUID_INPUT
                ? ProcessPortFilter.exact(Set.of("minecraft:water"))
                : ProcessPortFilter.any();
        return new ProcessPortDefinition(
                portType().channel(),
                portType().kind(),
                mode,
                Set.of(
                        ProcessPortSide.FRONT,
                        ProcessPortSide.BACK,
                        ProcessPortSide.LEFT,
                        ProcessPortSide.RIGHT,
                        ProcessPortSide.TOP,
                        ProcessPortSide.BOTTOM
                ),
                new ProcessPortRange(0, 1),
                filter
        );
    }

    private ProcessPortMode menuMode() {
        return portType() == RollingMachinePortType.ITEM_INPUT
                ? ProcessPortMode.BIDIRECTIONAL
                : ProcessPortMode.OUTPUT;
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

    private void markResourceStorageChanged() {
        setChanged();
    }

    private void recordExternalResourceMutation() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            loadedFormedController().ifPresent(controller ->
                    controller.recordExternalResourceMutation(serverLevel));
        }
    }

    private static MultiblockNbtStatus normalizeEmpty(MultiblockNbtStatus status) {
        return status == MultiblockNbtStatus.EMPTY ? MultiblockNbtStatus.SUPPORTED : status;
    }

    private static Optional<ControllerBindingView> loadedControllerView(
            ServerLevel level,
            BlockPos position
    ) {
        if (!level.hasChunkAt(position)) {
            return Optional.empty();
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof RollingMachineBlockEntity controller
                ? Optional.of(controller.bindingView())
                : Optional.empty();
    }
}

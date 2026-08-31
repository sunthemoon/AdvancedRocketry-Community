package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.RangedWrapper;

public final class ElectrolyzerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_CHARGE = 1;
    public static final int SLOT_HYDROGEN = 2;
    public static final int SLOT_OXYGEN = 3;
    public static final int SLOT_COUNT = 4;
    public static final int ENERGY_CAPACITY = 20_000;
    public static final int REDSTONE_ENERGY = 2_000;
    public static final int WATER_CAPACITY = 4_000;
    public static final int MENU_DATA_COUNT = 7;

    private final ElectrolyzerInventory inventory = new ElectrolyzerInventory(
            this::isInputLocked,
            this::isInternalMutation,
            this::setChanged,
            this::onInputChanged
    );
    private final ElectrolyzerFluidTank waterTank = new ElectrolyzerFluidTank(
            this::isInputLocked,
            this::isInternalMutation,
            this::setChanged
    );
    private final ElectrolyzerEnergyStorage energyStorage = new ElectrolyzerEnergyStorage(this::setChanged);
    private final ElectrolyzerMenuData menuData = new ElectrolyzerMenuData(
            this::progress,
            this::totalProcessingTicks,
            this::energyStored,
            this::waterAmount,
            this::statusNetworkId
    );

    private LazyOptional<IItemHandler> fullItems;
    private LazyOptional<IItemHandler> topItems;
    private LazyOptional<IItemHandler> sideItems;
    private LazyOptional<IItemHandler> bottomItems;
    private LazyOptional<IFluidHandler> sideFluid;
    private LazyOptional<IEnergyStorage> sideEnergy;

    private int progress;
    private ElectrolyzerStatus status = ElectrolyzerStatus.IDLE;
    @Nullable
    private ResourceLocation activeRecipeId;
    @Nullable
    private ElectrolyzerRecipe cachedRecipe;
    private boolean recipeDirty = true;
    private boolean recipeAmbiguous;
    private boolean ambiguityLogged;
    private boolean internalMutation;
    private boolean futureSchemaBlocked;
    private boolean invalidDataBlocked;
    @Nullable
    private CompoundTag preservedFutureData;
    private long recipeLookupCount;

    public ElectrolyzerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ELECTROLYZER.get(), position, state);
        createCapabilityViews();
    }

    public static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            ElectrolyzerBlockEntity machine
    ) {
        machine.tickServer(level, state);
    }

    private void tickServer(Level level, BlockState state) {
        if (futureSchemaBlocked) {
            updateStatusAndLit(level, state, ElectrolyzerStatus.UNSUPPORTED_DATA);
            return;
        }
        if (invalidDataBlocked) {
            updateStatusAndLit(level, state, ElectrolyzerStatus.INVALID_RECIPE);
            return;
        }

        chargeFromRedstone();
        if (level.getGameTime() % 20L == 0L) {
            recipeDirty = true;
        }

        ItemStack input = inventory.getStackInSlot(SLOT_INPUT);
        if (input.isEmpty()) {
            boolean lostActiveRecipe = progress > 0 || activeRecipeId != null;
            resetProcess();
            updateStatusAndLit(
                    level,
                    state,
                    lostActiveRecipe ? ElectrolyzerStatus.INVALID_RECIPE : ElectrolyzerStatus.IDLE
            );
            return;
        }

        boolean wasActive = progress > 0 || activeRecipeId != null;
        ElectrolyzerRecipe recipe = resolveRecipe(level, input);
        if (recipe == null) {
            resetProcess();
            updateStatusAndLit(
                    level,
                    state,
                    wasActive || recipeAmbiguous
                            ? ElectrolyzerStatus.INVALID_RECIPE
                            : ElectrolyzerStatus.NO_RECIPE
            );
            return;
        }

        ElectrolyzerRecipeSpec spec = recipe.spec();
        boolean waterAvailable = waterTank.getFluid().getFluid() == recipe.fluid()
                && waterTank.getFluidAmount() >= spec.waterAmount();
        boolean outputSpace = canInsertResult(SLOT_HYDROGEN, recipe.hydrogenResult())
                && canInsertResult(SLOT_OXYGEN, recipe.oxygenResult());
        boolean enabled = !state.getValue(ElectrolyzerBlock.POWERED);
        ElectrolyzerTickResult result = ElectrolyzerProcessEngine.tick(
                progress,
                spec.processingTicks(),
                new ElectrolyzerTickInput(
                        true,
                        enabled,
                        waterAvailable,
                        outputSpace,
                        energyStorage.getEnergyStored(),
                        spec.energyPerTick()
                )
        );

        int previousProgress = progress;
        ResourceLocation previousActiveRecipe = activeRecipeId;
        if (result.energyConsumed() > 0) {
            energyStorage.consume(result.energyConsumed());
        }
        progress = result.progress();
        if (result.completed()) {
            completeBatch(recipe);
            activeRecipeId = null;
            recipeDirty = true;
        } else if (progress > 0) {
            activeRecipeId = recipe.getId();
        }
        if (progress != previousProgress || !java.util.Objects.equals(activeRecipeId, previousActiveRecipe)) {
            setChanged();
        }
        updateStatusAndLit(level, state, result.status());
    }

    @Nullable
    private ElectrolyzerRecipe resolveRecipe(Level level, ItemStack input) {
        SimpleContainer container = new SimpleContainer(input.copy());
        if (activeRecipeId != null) {
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> active =
                    level.getRecipeManager().byKey(activeRecipeId);
            recipeLookupCount++;
            if (active.isPresent()
                    && active.get() instanceof ElectrolyzerRecipe recipe
                    && recipe.matches(container, level)) {
                cachedRecipe = recipe;
                recipeDirty = false;
                recipeAmbiguous = false;
                return recipe;
            }
            return null;
        }

        if (!recipeDirty && cachedRecipe != null && cachedRecipe.matches(container, level)) {
            Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> registered =
                    level.getRecipeManager().byKey(cachedRecipe.getId());
            recipeLookupCount++;
            if (registered.isPresent() && registered.get() == cachedRecipe) {
                return cachedRecipe;
            }
            cachedRecipe = null;
            recipeDirty = true;
        }
        if (!recipeDirty) {
            return null;
        }

        List<ElectrolyzerRecipe> matches = level.getRecipeManager().getRecipesFor(
                ModRecipes.ELECTROLYZING_TYPE.get(),
                container,
                level
        );
        recipeLookupCount++;
        recipeDirty = false;
        recipeAmbiguous = matches.size() > 1;
        cachedRecipe = matches.size() == 1 ? matches.get(0) : null;
        if (recipeAmbiguous && !ambiguityLogged) {
            AdvancedRocketryCommunity.LOGGER.warn(
                    "Refusing ambiguous Electrolyzer input at {}: {} recipes match",
                    worldPosition,
                    matches.size()
            );
            ambiguityLogged = true;
        } else if (!recipeAmbiguous) {
            ambiguityLogged = false;
        }
        return cachedRecipe;
    }

    private void chargeFromRedstone() {
        ItemStack charge = inventory.getStackInSlot(SLOT_CHARGE);
        if (!charge.is(Items.REDSTONE) || energyStorage.getEnergyStored() > ENERGY_CAPACITY - REDSTONE_ENERGY) {
            return;
        }
        internalMutation = true;
        try {
            inventory.extractItem(SLOT_CHARGE, 1, false);
            energyStorage.addInternal(REDSTONE_ENERGY);
        } finally {
            internalMutation = false;
        }
    }

    private boolean canInsertResult(int slot, ItemStack result) {
        ItemStack existing = inventory.getStackInSlot(slot);
        if (existing.isEmpty()) {
            return result.getCount() <= Math.min(result.getMaxStackSize(), inventory.getSlotLimit(slot));
        }
        return ItemHandlerHelper.canItemStacksStack(existing, result)
                && existing.getCount() + result.getCount()
                <= Math.min(existing.getMaxStackSize(), inventory.getSlotLimit(slot));
    }

    private void completeBatch(ElectrolyzerRecipe recipe) {
        ElectrolyzerRecipeSpec spec = recipe.spec();
        internalMutation = true;
        try {
            ItemStack consumed = inventory.extractItem(SLOT_INPUT, spec.inputCount(), false);
            if (consumed.getCount() != spec.inputCount()) {
                throw new IllegalStateException("Electrolyzer input changed during an atomic completion");
            }
            FluidStack drained = waterTank.drainInternal(spec.waterAmount());
            if (drained.getAmount() != spec.waterAmount()) {
                throw new IllegalStateException("Electrolyzer water changed during an atomic completion");
            }
            insertResultInternal(SLOT_HYDROGEN, recipe.hydrogenResult());
            insertResultInternal(SLOT_OXYGEN, recipe.oxygenResult());
        } finally {
            internalMutation = false;
        }
    }

    private void insertResultInternal(int slot, ItemStack result) {
        ItemStack existing = inventory.getStackInSlot(slot);
        if (existing.isEmpty()) {
            inventory.setStackInSlot(slot, result.copy());
            return;
        }
        ItemStack combined = existing.copy();
        combined.grow(result.getCount());
        inventory.setStackInSlot(slot, combined);
    }

    private void resetProcess() {
        if (progress != 0 || activeRecipeId != null) {
            progress = 0;
            activeRecipeId = null;
            setChanged();
        }
        cachedRecipe = null;
        recipeDirty = true;
    }

    private void updateStatusAndLit(Level level, BlockState state, ElectrolyzerStatus newStatus) {
        boolean changed = status != newStatus;
        status = newStatus;
        boolean lit = newStatus == ElectrolyzerStatus.RUNNING;
        if (state.getValue(ElectrolyzerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(ElectrolyzerBlock.LIT, lit), Block.UPDATE_CLIENTS);
            changed = true;
        }
        if (changed) {
            setChanged();
        }
    }

    public boolean fillFromPlayer(Player player, InteractionHand hand, Direction side) {
        if (side.getAxis().isVertical() || progress > 0) {
            return false;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, sideFluid.orElseThrow(IllegalStateException::new));
    }

    public void copyInventoryTo(Container target) {
        int count = Math.min(target.getContainerSize(), SLOT_COUNT);
        for (int slot = 0; slot < count; slot++) {
            target.setItem(slot, inventory.getStackInSlot(slot).copy());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.advancedrocketrycommunity.electrolyzer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ElectrolyzerMenu(containerId, playerInventory, this, menuData);
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if (futureSchemaBlocked && preservedFutureData != null) {
            parent.put(ElectrolyzerPersistence.DATA_KEY, preservedFutureData.copy());
            return;
        }
        parent.put(
                ElectrolyzerPersistence.DATA_KEY,
                ElectrolyzerPersistence.encode(inventory, waterTank, energyStorage, progress, activeRecipeId)
        );
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        resetLoadedState();
        ElectrolyzerPersistence.DecodeResult decoded = ElectrolyzerPersistence.decode(parent);
        if (decoded.future()) {
            futureSchemaBlocked = true;
            preservedFutureData = decoded.preservedFutureData();
            status = ElectrolyzerStatus.UNSUPPORTED_DATA;
            return;
        }
        internalMutation = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                inventory.setStackInSlot(slot, decoded.inventory()[slot]);
            }
            waterTank.setFluidInternal(decoded.water());
            energyStorage.setStored(decoded.energy());
        } finally {
            internalMutation = false;
        }
        progress = decoded.progress();
        activeRecipeId = decoded.activeRecipeId();
        invalidDataBlocked = decoded.blockingInvalid();
        status = decoded.invalid() ? ElectrolyzerStatus.INVALID_RECIPE : ElectrolyzerStatus.IDLE;
        recipeDirty = true;
    }

    private void resetLoadedState() {
        internalMutation = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
            waterTank.setFluidInternal(FluidStack.EMPTY);
            energyStorage.setStored(0);
        } finally {
            internalMutation = false;
        }
        progress = 0;
        status = ElectrolyzerStatus.IDLE;
        activeRecipeId = null;
        cachedRecipe = null;
        recipeDirty = true;
        recipeAmbiguous = false;
        ambiguityLogged = false;
        futureSchemaBlocked = false;
        invalidDataBlocked = false;
        preservedFutureData = null;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            if (side == null) {
                return fullItems.cast();
            }
            if (side == Direction.UP) {
                return topItems.cast();
            }
            if (side == Direction.DOWN) {
                return bottomItems.cast();
            }
            return sideItems.cast();
        }
        if (capability == ForgeCapabilities.FLUID_HANDLER && (side == null || side.getAxis().isHorizontal())) {
            return sideFluid.cast();
        }
        if (capability == ForgeCapabilities.ENERGY && (side == null || side.getAxis().isHorizontal())) {
            return sideEnergy.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fullItems.invalidate();
        topItems.invalidate();
        sideItems.invalidate();
        bottomItems.invalidate();
        sideFluid.invalidate();
        sideEnergy.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilityViews();
    }

    private void createCapabilityViews() {
        fullItems = LazyOptional.of(() -> inventory);
        topItems = LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_INPUT, SLOT_INPUT + 1));
        sideItems = LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_CHARGE, SLOT_CHARGE + 1));
        bottomItems = LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_HYDROGEN, SLOT_COUNT));
        sideFluid = LazyOptional.of(() -> new FillOnlyFluidHandler(waterTank));
        sideEnergy = LazyOptional.of(() -> energyStorage);
    }

    public IItemHandler menuInventory() {
        return inventory;
    }

    public int progress() {
        return progress;
    }

    public int totalProcessingTicks() {
        return cachedRecipe == null ? 0 : cachedRecipe.spec().processingTicks();
    }

    public int energyStored() {
        return energyStorage.getEnergyStored();
    }

    public int waterAmount() {
        return waterTank.getFluidAmount();
    }

    public ElectrolyzerStatus status() {
        return status;
    }

    private int statusNetworkId() {
        return status.networkId();
    }

    public long recipeLookupCount() {
        return recipeLookupCount;
    }

    private void onInputChanged() {
        recipeDirty = true;
        cachedRecipe = null;
        ambiguityLogged = false;
    }

    private boolean isInputLocked() {
        return progress > 0;
    }

    private boolean isInternalMutation() {
        return internalMutation;
    }

}

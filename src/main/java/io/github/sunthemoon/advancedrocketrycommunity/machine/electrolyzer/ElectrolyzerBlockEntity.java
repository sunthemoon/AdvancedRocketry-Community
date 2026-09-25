package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;

/** Forge lifecycle adapter for the single-block Electrolyzer process owner and storage. */
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

    private final ElectrolyzerProcessController process = new ElectrolyzerProcessController(this::setChanged);
    private final ElectrolyzerInventory inventory = new ElectrolyzerInventory(
            process::inputLocked,
            this::isInternalMutation,
            this::setChanged,
            process::inputChanged
    );
    private final ElectrolyzerFluidTank waterTank = new ElectrolyzerFluidTank(
            process::inputLocked,
            this::isInternalMutation,
            this::setChanged
    );
    private final ElectrolyzerEnergyStorage energyStorage = new ElectrolyzerEnergyStorage(this::setChanged);
    private final ElectrolyzerCapabilityAdapter capabilities = new ElectrolyzerCapabilityAdapter(
            inventory,
            waterTank,
            energyStorage,
            process::inputLocked,
            process::permitsExternalResourceOperations,
            process::recordExternalMutation
    );
    private final ElectrolyzerMenuItemHandler menuItems = new ElectrolyzerMenuItemHandler(
            inventory,
            capabilities::menuItems
    );
    private final ElectrolyzerMenuData menuData = new ElectrolyzerMenuData(
            this::progress,
            this::totalProcessingTicks,
            this::energyStored,
            this::waterAmount,
            this::statusNetworkId
    );

    private boolean internalMutation;
    @Nullable
    private Tag preservedLegacyData;

    public ElectrolyzerBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.ELECTROLYZER.get(), position, state);
    }

    public static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            ElectrolyzerBlockEntity machine
    ) {
        if (level instanceof ServerLevel serverLevel) {
            machine.tickServer(serverLevel, state);
        }
    }

    private void tickServer(ServerLevel level, BlockState state) {
        process.tick(level, this, !state.getValue(ElectrolyzerBlock.POWERED));
        updateLit(level, state);
    }

    private void updateLit(Level level, BlockState state) {
        boolean lit = status() == ElectrolyzerStatus.RUNNING;
        if (state.getValue(ElectrolyzerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(ElectrolyzerBlock.LIT, lit), Block.UPDATE_CLIENTS);
            setChanged();
        }
    }

    boolean chargeFromRedstoneInternal() {
        ItemStack charge = inventory.getStackInSlot(SLOT_CHARGE);
        if (!charge.is(Items.REDSTONE)
                || energyStorage.getEnergyStored() > ENERGY_CAPACITY - REDSTONE_ENERGY) {
            return false;
        }
        internalMutation = true;
        try {
            ItemStack consumed = inventory.extractItem(SLOT_CHARGE, 1, false);
            if (consumed.getCount() != 1) {
                throw new IllegalStateException("Electrolyzer charge slot changed during conversion");
            }
            energyStorage.addInternal(REDSTONE_ENERGY);
            return true;
        } finally {
            internalMutation = false;
        }
    }

    boolean consumeEnergyInternal(int amount) {
        if (amount < 0 || energyStorage.getEnergyStored() < amount) {
            return false;
        }
        energyStorage.consume(amount);
        return true;
    }

    void replaceProcessResources(
            ItemStack input,
            FluidStack water,
            ItemStack hydrogen,
            ItemStack oxygen
    ) {
        internalMutation = true;
        try {
            inventory.setStackInSlot(SLOT_INPUT, input.copy());
            waterTank.setFluidInternal(water.copy());
            inventory.setStackInSlot(SLOT_HYDROGEN, hydrogen.copy());
            inventory.setStackInSlot(SLOT_OXYGEN, oxygen.copy());
            if (!ItemStack.matches(input, inventory.getStackInSlot(SLOT_INPUT))
                    || !water.isFluidStackIdentical(waterTank.getFluid())
                    || !ItemStack.matches(hydrogen, inventory.getStackInSlot(SLOT_HYDROGEN))
                    || !ItemStack.matches(oxygen, inventory.getStackInSlot(SLOT_OXYGEN))) {
                throw new IllegalStateException("Electrolyzer rejected an atomic resource replacement");
            }
        } finally {
            internalMutation = false;
        }
    }

    ItemStack storedItemCopy(int slot) {
        return inventory.getStackInSlot(slot).copy();
    }

    FluidStack storedWaterCopy() {
        return waterTank.getFluid().copy();
    }

    int slotLimit(int slot) {
        return inventory.getSlotLimit(slot);
    }

    public boolean fillFromPlayer(Player player, InteractionHand hand, Direction side) {
        if (side.getAxis().isVertical() || process.inputLocked()) {
            return false;
        }
        return capabilities.get(ForgeCapabilities.FLUID_HANDLER, side)
                .map(handler -> FluidUtil.interactWithFluidHandler(player, hand, handler))
                .orElse(false);
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
        if (preservedLegacyData != null) {
            parent.put(ElectrolyzerPersistence.DATA_KEY, preservedLegacyData.copy());
        } else {
            parent.put(
                    ElectrolyzerPersistence.DATA_KEY,
                    ElectrolyzerPersistence.encode(
                            inventory,
                            waterTank,
                            energyStorage,
                            progress(),
                            process.activeRecipeId()
                    )
            );
        }
        process.save(parent);
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        resetLoadedStorage();
        ElectrolyzerPersistence.DecodeResult decoded = ElectrolyzerPersistence.decode(parent);
        preservedLegacyData = decoded.preservedData();
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
        process.load(parent, decoded);
    }

    private void resetLoadedStorage() {
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
        preservedLegacyData = null;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(
            @Nonnull Capability<T> capability,
            @Nullable Direction side
    ) {
        LazyOptional<T> view = capabilities.get(capability, side);
        return view.isPresent() ? view : super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        capabilities.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        capabilities.revive();
    }

    public IItemHandler menuInventory() {
        return menuItems;
    }

    public int progress() {
        return process.progressTicks();
    }

    public int totalProcessingTicks() {
        return process.totalProcessingTicks();
    }

    public int energyStored() {
        return energyStorage.getEnergyStored();
    }

    public int waterAmount() {
        return waterTank.getFluidAmount();
    }

    public ElectrolyzerStatus status() {
        return process.displayStatus();
    }

    public long recipeLookupCount() {
        return process.recipeLookupCount();
    }

    long resourceRevision() {
        return process.resourceRevision();
    }

    boolean migrationPerformed() {
        return process.migrationPerformed();
    }

    private int statusNetworkId() {
        return status().networkId();
    }

    private boolean isInternalMutation() {
        return internalMutation;
    }
}

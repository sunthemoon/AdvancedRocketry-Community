package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransfer;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentSupplyEngine;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentSupplyInput;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentSupplyResult;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RangedWrapper;

public final class OxygenVentBlockEntity extends BlockEntity {
    public static final int SLOT_OXYGEN = 0;
    public static final int SLOT_EMPTY = 1;
    public static final int SLOT_COUNT = 2;
    public static final int REDSTONE_ENERGY = 2_000;

    private final VentInventory inventory = new VentInventory();
    private final VentEnergyStorage energy = new VentEnergyStorage();
    private LazyOptional<IItemHandler> fullItems;
    private LazyOptional<IItemHandler> topItems;
    private LazyOptional<IItemHandler> bottomItems;
    private LazyOptional<IEnergyStorage> energyCapability;

    private int oxygenUnits;
    private int oxygenPhase;
    private VentOperatingStatus status = VentOperatingStatus.SCANNING;
    private boolean futureSchemaBlocked;
    private boolean invalidDataBlocked;
    private CompoundTag preservedBlockedData;

    public OxygenVentBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.OXYGEN_VENT.get(), position, state);
        createCapabilityViews();
    }

    public static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            OxygenVentBlockEntity vent
    ) {
        vent.processAutomationCanister();
    }

    public VentSupplyResult applySupply(VolumeScanOutcome scanOutcome, boolean electedProvider) {
        if (futureSchemaBlocked) {
            setStatus(VentOperatingStatus.UNSUPPORTED_DATA);
            return inactiveResult(VentOperatingStatus.UNSUPPORTED_DATA);
        }
        if (invalidDataBlocked) {
            setStatus(VentOperatingStatus.INVALID_DATA);
            return inactiveResult(VentOperatingStatus.INVALID_DATA);
        }
        VentSupplyResult result = VentSupplyEngine.tick(new VentSupplyInput(
                scanOutcome,
                electedProvider,
                oxygenUnits,
                energy.getEnergyStored(),
                oxygenPhase
        ));
        oxygenUnits = result.oxygenUnits();
        oxygenPhase = result.oxygenPhase();
        energy.setStored(result.energyStored());
        setStatus(result.status());
        if (result.energyConsumed() > 0 || result.oxygenConsumed() > 0) {
            setChanged();
        }
        return result;
    }

    public boolean fillFromPlayer(Player player, InteractionHand hand) {
        if (futureSchemaBlocked || invalidDataBlocked) {
            return false;
        }
        OxygenTransferResult transfer = OxygenTransfer.fillOneCanister(
                oxygenUnits,
                AtmosphereLimits.VENT_OXYGEN_CAPACITY
        );
        if (!transfer.accepted()) {
            return false;
        }
        oxygenUnits = transfer.oxygenUnits();
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).shrink(1);
            ItemStack empty = new ItemStack(ModItems.EMPTY_CANISTER.get());
            if (!player.getInventory().add(empty)) {
                player.drop(empty, false);
            }
        }
        setChanged();
        return true;
    }

    public boolean chargeFromPlayer(Player player, InteractionHand hand) {
        if (futureSchemaBlocked || invalidDataBlocked
                || energy.getEnergyStored() > AtmosphereLimits.VENT_ENERGY_CAPACITY - REDSTONE_ENERGY) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).shrink(1);
        }
        energy.addInternal(REDSTONE_ENERGY);
        return true;
    }

    private void processAutomationCanister() {
        if (futureSchemaBlocked || invalidDataBlocked) {
            return;
        }
        ItemStack input = inventory.getStackInSlot(SLOT_OXYGEN);
        ItemStack output = inventory.getStackInSlot(SLOT_EMPTY);
        if (!input.is(ModItems.OXYGEN_CANISTER.get())
                || (!output.isEmpty() && (!output.is(ModItems.EMPTY_CANISTER.get())
                || output.getCount() >= output.getMaxStackSize()))) {
            return;
        }
        OxygenTransferResult transfer = OxygenTransfer.fillOneCanister(
                oxygenUnits,
                AtmosphereLimits.VENT_OXYGEN_CAPACITY
        );
        if (!transfer.accepted()) {
            return;
        }
        oxygenUnits = transfer.oxygenUnits();
        inventory.extractInternal(SLOT_OXYGEN, 1);
        inventory.addEmptyInternal();
        setChanged();
    }

    public void copyInventoryTo(Container target) {
        int count = Math.min(target.getContainerSize(), SLOT_COUNT);
        for (int slot = 0; slot < count; slot++) {
            target.setItem(slot, inventory.getStackInSlot(slot).copy());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if ((futureSchemaBlocked || invalidDataBlocked) && preservedBlockedData != null) {
            parent.put(OxygenVentPersistence.DATA_KEY, preservedBlockedData.copy());
            return;
        }
        parent.put(OxygenVentPersistence.DATA_KEY, OxygenVentPersistence.encode(
                inventory.getStackInSlot(SLOT_OXYGEN).getCount(),
                inventory.getStackInSlot(SLOT_EMPTY).getCount(),
                oxygenUnits,
                energy.getEnergyStored(),
                oxygenPhase
        ));
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        resetLoadedState();
        OxygenVentPersistence.DecodeResult decoded = OxygenVentPersistence.decode(parent);
        if (decoded.status() == OxygenVentPersistence.DecodeStatus.FUTURE) {
            futureSchemaBlocked = true;
            preservedBlockedData = decoded.preservedFutureData();
            status = VentOperatingStatus.UNSUPPORTED_DATA;
            return;
        }
        if (decoded.status() == OxygenVentPersistence.DecodeStatus.INVALID) {
            invalidDataBlocked = true;
            preservedBlockedData = decoded.preservedFutureData();
            status = VentOperatingStatus.INVALID_DATA;
            return;
        }
        inventory.setCounts(decoded.inputCount(), decoded.outputCount());
        oxygenUnits = decoded.oxygenUnits();
        energy.setStored(decoded.energy());
        oxygenPhase = decoded.oxygenPhase();
    }

    private void resetLoadedState() {
        inventory.setCounts(0, 0);
        oxygenUnits = 0;
        oxygenPhase = 0;
        energy.setStored(0);
        status = VentOperatingStatus.SCANNING;
        futureSchemaBlocked = false;
        invalidDataBlocked = false;
        preservedBlockedData = null;
    }

    private VentSupplyResult inactiveResult(VentOperatingStatus inactiveStatus) {
        return new VentSupplyResult(
                inactiveStatus,
                false,
                oxygenUnits,
                0,
                energy.getEnergyStored(),
                0,
                0
        );
    }

    private void setStatus(VentOperatingStatus newStatus) {
        boolean changed = status != newStatus;
        status = newStatus;
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            boolean lit = newStatus == VentOperatingStatus.ACTIVE;
            if (state.hasProperty(OxygenVentBlock.LIT)
                    && state.getValue(OxygenVentBlock.LIT) != lit) {
                level.setBlock(worldPosition, state.setValue(OxygenVentBlock.LIT, lit), Block.UPDATE_CLIENTS);
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            if (side == Direction.UP) {
                return topItems.cast();
            }
            if (side == Direction.DOWN) {
                return bottomItems.cast();
            }
            return fullItems.cast();
        }
        if (capability == ForgeCapabilities.ENERGY && side != Direction.UP && side != Direction.DOWN) {
            return energyCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fullItems.invalidate();
        topItems.invalidate();
        bottomItems.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilityViews();
    }

    private void createCapabilityViews() {
        fullItems = LazyOptional.of(() -> inventory);
        topItems = LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_OXYGEN, SLOT_OXYGEN + 1));
        bottomItems = LazyOptional.of(() -> new RangedWrapper(inventory, SLOT_EMPTY, SLOT_EMPTY + 1));
        energyCapability = LazyOptional.of(() -> energy);
    }

    public int oxygenUnits() {
        return oxygenUnits;
    }

    public int energyStored() {
        return energy.getEnergyStored();
    }

    public VentOperatingStatus status() {
        return status;
    }

    public IItemHandler itemHandler() {
        return inventory;
    }

    private final class VentInventory extends ItemStackHandler {
        private boolean internal;

        private VentInventory() {
            super(SLOT_COUNT);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == SLOT_OXYGEN && stack.is(ModItems.OXYGEN_CANISTER.get());
        }

        @Override
        public int getSlotLimit(int slot) {
            return OxygenVentPersistence.MAX_STACK_COUNT;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (!internal && slot == SLOT_EMPTY) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            OxygenVentBlockEntity.this.setChanged();
        }

        private void extractInternal(int slot, int amount) {
            internal = true;
            try {
                extractItem(slot, amount, false);
            } finally {
                internal = false;
            }
        }

        private void addEmptyInternal() {
            internal = true;
            try {
                ItemStack output = getStackInSlot(SLOT_EMPTY);
                if (output.isEmpty()) {
                    setStackInSlot(SLOT_EMPTY, new ItemStack(ModItems.EMPTY_CANISTER.get()));
                } else {
                    ItemStack grown = output.copy();
                    grown.grow(1);
                    setStackInSlot(SLOT_EMPTY, grown);
                }
            } finally {
                internal = false;
            }
        }

        private void setCounts(int input, int output) {
            internal = true;
            try {
                setStackInSlot(
                        SLOT_OXYGEN,
                        input == 0 ? ItemStack.EMPTY : new ItemStack(ModItems.OXYGEN_CANISTER.get(), input)
                );
                setStackInSlot(
                        SLOT_EMPTY,
                        output == 0 ? ItemStack.EMPTY : new ItemStack(ModItems.EMPTY_CANISTER.get(), output)
                );
            } finally {
                internal = false;
            }
        }
    }

    private final class VentEnergyStorage extends EnergyStorage {
        private VentEnergyStorage() {
            super(AtmosphereLimits.VENT_ENERGY_CAPACITY, AtmosphereLimits.VENT_ENERGY_CAPACITY, 0);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (received > 0 && !simulate) {
                OxygenVentBlockEntity.this.setChanged();
            }
            return received;
        }

        private void addInternal(int amount) {
            energy = Math.min(capacity, energy + amount);
            OxygenVentBlockEntity.this.setChanged();
        }

        private void setStored(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }
}

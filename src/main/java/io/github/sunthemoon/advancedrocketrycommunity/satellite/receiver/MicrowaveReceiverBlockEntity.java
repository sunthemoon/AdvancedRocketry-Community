package io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ReceiverDirectory;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SolarLinks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

/**
 * Microwave receiver (ADR-049 section 9): four chip slots and a {@code receiver_id}. While loaded it checks its
 * links every 20 ticks and produces their rate into a 100,000 FE buffer that pushes to neighbours. Nothing is
 * banked while unloaded. Root schema 1 with the ADR-029 bounds, quarantine and exactly-once carry.
 */
public final class MicrowaveReceiverBlockEntity extends BlockEntity implements MenuProvider, ReceiverDirectory.Host {
    public static final int SLOT_COUNT = 4;
    public static final int ENERGY_CAPACITY = 100_000;
    public static final int CHECK_INTERVAL_TICKS = 20;
    public static final int MENU_DATA_COUNT = 3 + SLOT_COUNT;

    static final String DATA_KEY = "MicrowaveReceiver";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_RECEIVER_NBT_BYTES = 64 * 1024;
    private static final Set<String> ROOT_KEYS = Set.of("schema_version", "inventory", "energy", "receiver_id");

    private final MicrowaveReceiverInventory inventory = new MicrowaveReceiverInventory(
            () -> blocked() || isRemoved(), this::inventoryChanged);
    private final ReceiverEnergyStorage energyStorage = new ReceiverEnergyStorage();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();

    @Nullable
    private UUID receiverId;
    private boolean registered;
    private int output;
    private final SolarLinks.LinkStatus[] statuses = new SolarLinks.LinkStatus[SLOT_COUNT];
    private boolean futureSchemaBlocked;
    private boolean invalidDataBlocked;
    @Nullable
    private Tag preservedBlockedData;

    public MicrowaveReceiverBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.MICROWAVE_RECEIVER.get(), position, state);
        Arrays.fill(statuses, SolarLinks.LinkStatus.EMPTY);
        createCapabilities();
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, MicrowaveReceiverBlockEntity receiver) {
        MinecraftServer server = level.getServer();
        if (receiver.blocked() || server == null) {
            return;
        }
        if (receiver.receiverId == null) {
            receiver.receiverId = UUID.randomUUID();
            receiver.setChanged();
        }
        if (!receiver.registered) {
            SatelliteRuntime.registerReceiver(receiver.receiverId, level.dimension(), position);
            receiver.registered = true;
        }
        // Links change only at the 20-tick check (ADR-049 section 9), so moving chips cannot churn the registry.
        if (Math.floorMod(level.getGameTime() + position.hashCode(), CHECK_INTERVAL_TICKS) == 0) {
            receiver.check(server);
        }
        if (receiver.output > 0) {
            receiver.energyStorage.add(receiver.output);
        }
        receiver.push(level, position);
    }

    private void check(MinecraftServer server) {
        List<Optional<SatelliteIdentity>> chips = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            chips.add(MicrowaveReceiverInventory.identity(inventory.getStackInSlot(slot)));
        }
        Optional<SolarLinks.ReceiverCheck> result = SatelliteRuntime.checkReceiver(server, receiverId, chips);
        output = result.map(SolarLinks.ReceiverCheck::output).orElse(0);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int index = slot;
            SolarLinks.LinkStatus status = result.map(check -> check.slots().get(index)).orElse(SolarLinks.LinkStatus.EMPTY);
            boolean unreadable = !inventory.getStackInSlot(slot).isEmpty() && chips.get(slot).isEmpty();
            statuses[slot] = unreadable ? SolarLinks.LinkStatus.UNAVAILABLE : status;
        }
    }

    /** Offers the buffer to every neighbour; receivers never accept energy, so two never feed each other. */
    private void push(Level level, BlockPos position) {
        for (Direction direction : Direction.values()) {
            int stored = energyStorage.getEnergyStored();
            if (stored <= 0) {
                return;
            }
            BlockEntity neighbour = level.getBlockEntity(position.relative(direction));
            if (neighbour == null) {
                continue;
            }
            neighbour.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite()).ifPresent(storage -> {
                int accepted = storage.receiveEnergy(Math.min(stored, SolarLinks.MAX_OUTPUT_PER_TICK), false);
                if (accepted > 0) {
                    energyStorage.consume(Math.min(accepted, stored));
                }
            });
        }
    }

    /** Called when the block is broken or replaced: every link this receiver holds is cleared. */
    void released() {
        if (!blocked() && receiverId != null && level != null && level.getServer() != null) {
            SatelliteRuntime.releaseReceiver(level.getServer(), receiverId);
        }
    }

    private void inventoryChanged() {
        setChanged();
    }

    @Override
    public UUID receiverId() {
        return receiverId;
    }

    public int energyStored() {
        return energyStorage.getEnergyStored();
    }

    public int output() {
        return output;
    }

    public SolarLinks.LinkStatus status(int slot) {
        return statuses[slot];
    }

    public IItemHandler menuInventory() {
        return inventory;
    }

    public void copyInventoryTo(Container target) {
        for (int slot = 0; slot < Math.min(target.getContainerSize(), SLOT_COUNT); slot++) {
            target.setItem(slot, inventory.getStackInSlot(slot).copy());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.advancedrocketrycommunity.microwave_receiver");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new MicrowaveReceiverMenu(id, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag parent) {
        super.saveAdditional(parent);
        if (preservedBlockedData != null) {
            parent.put(DATA_KEY, boundedRoot(preservedBlockedData) ? preservedBlockedData.copy() : preservedBlockedData);
            return;
        }
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", SCHEMA_VERSION);
        data.put("inventory", inventory.serializeNBT());
        data.putInt("energy", energyStorage.getEnergyStored());
        if (receiverId != null) {
            data.putUUID("receiver_id", receiverId);
        }
        if (!boundedRoot(data)) {
            throw new IllegalStateException("Microwave receiver exceeds its fixed NBT bound");
        }
        parent.put(DATA_KEY, data);
    }

    @Override
    public void load(CompoundTag parent) {
        super.load(parent);
        resetLoadedState();
        if (!parent.contains(DATA_KEY)) {
            return;
        }
        Tag raw = parent.get(DATA_KEY);
        if (!boundedRoot(raw) || !(raw instanceof CompoundTag data)) {
            block(false, raw);
            return;
        }
        if (!data.contains("schema_version", Tag.TAG_INT)) {
            block(false, data);
            return;
        }
        int schema = data.getInt("schema_version");
        if (schema > SCHEMA_VERSION) {
            block(true, data);
            return;
        }
        try {
            if (schema != SCHEMA_VERSION || !ROOT_KEYS.containsAll(data.getAllKeys())
                    || !data.contains("inventory", Tag.TAG_COMPOUND)
                    || !data.contains("energy", Tag.TAG_INT)
                    || data.contains("receiver_id") && !data.hasUUID("receiver_id")) {
                throw new IllegalArgumentException("Microwave receiver data is incomplete");
            }
            int energy = data.getInt("energy");
            if (energy < 0 || energy > ENERGY_CAPACITY) {
                throw new IllegalArgumentException("Microwave receiver energy is outside its bound");
            }
            inventory.loadValidated(data.getCompound("inventory"));
            energyStorage.set(energy);
            receiverId = data.hasUUID("receiver_id") ? data.getUUID("receiver_id") : null;
        } catch (RuntimeException exception) {
            resetLoadedState();
            block(false, data);
        }
    }

    private void block(boolean future, Tag raw) {
        futureSchemaBlocked = future;
        invalidDataBlocked = !future;
        preservedBlockedData = boundedRoot(raw) ? raw.copy() : raw;
    }

    private void resetLoadedState() {
        inventory.setSize(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        energyStorage.set(0);
        receiverId = null;
        registered = false;
        output = 0;
        Arrays.fill(statuses, SolarLinks.LinkStatus.EMPTY);
        futureSchemaBlocked = false;
        invalidDataBlocked = false;
        preservedBlockedData = null;
    }

    boolean blocked() {
        return futureSchemaBlocked || invalidDataBlocked;
    }

    static boolean boundedRoot(Tag tag) {
        return BoundedNbt.fits(tag, MAX_RECEIVER_NBT_BYTES, 20, 2048);
    }

    boolean canCarryData() {
        return preservedBlockedData == null || boundedRoot(preservedBlockedData);
    }

    CompoundTag carriedData() {
        if (!canCarryData()) {
            throw new IllegalStateException("Receiver root cannot be carried safely");
        }
        CompoundTag data = new CompoundTag();
        saveAdditional(data);
        return data;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        if (capability == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        energyCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        createCapabilities();
    }

    private void createCapabilities() {
        itemCapability = LazyOptional.of(() -> inventory);
        energyCapability = LazyOptional.of(() -> energyStorage);
    }

    /** Output-only buffer: neighbours may extract, nothing may insert. */
    private final class ReceiverEnergyStorage implements IEnergyStorage {
        private int stored;

        @Override
        public int receiveEnergy(int maximum, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maximum, boolean simulate) {
            if (blocked()) {
                return 0;
            }
            int extracted = Math.min(Math.max(0, maximum), stored);
            if (!simulate && extracted > 0) {
                stored -= extracted;
                setChanged();
            }
            return extracted;
        }

        @Override
        public int getEnergyStored() {
            return stored;
        }

        @Override
        public int getMaxEnergyStored() {
            return ENERGY_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return !blocked();
        }

        @Override
        public boolean canReceive() {
            return false;
        }

        private void add(int amount) {
            int next = (int) Math.min(ENERGY_CAPACITY, (long) stored + amount);
            if (next != stored) {
                stored = next;
                setChanged();
            }
        }

        private void consume(int amount) {
            stored -= amount;
            setChanged();
        }

        private void set(int value) {
            stored = value;
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatellitePayloadRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
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
import net.minecraftforge.energy.IEnergyStorage;

public final class SatelliteTerminalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_CHASSIS = 0;
    public static final int SLOT_SOLAR_MODULE = 1;
    public static final int SLOT_DATA_STORAGE = 2;
    public static final int SLOT_CONTROL_CHIP = 3;
    public static final int SLOT_PACKAGE = 4;
    public static final int SLOT_CHARGE = 5;
    public static final int SLOT_COUNT = 6;
    public static final int MENU_DATA_COUNT = 12;
    public static final int ENERGY_CAPACITY = 10_000;
    public static final int REDSTONE_ENERGY = 2_000;
    public static final int ASSEMBLY_ENERGY = 1_000;
    public static final int LAUNCH_POWER_THRESHOLD = 2_000;

    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_TERMINAL_NBT_BYTES = 64 * 1024;
    static final String DATA_KEY = "SatelliteTerminal";

    private final SatelliteTerminalInventory inventory = new SatelliteTerminalInventory(() -> blocked() || isRemoved(), this::inventoryChanged);
    private final TerminalEnergyStorage energyStorage = new TerminalEnergyStorage();
    private LazyOptional<net.minecraftforge.items.IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();

    private int selectedTargetIndex;
    /** The definition the target index was chosen for; runtime only (C7-L6). */
    @Nullable
    private ResourceLocation targetDefinition;
    private SatelliteOperationCode lastResult = SatelliteOperationCode.SUCCESS;
    @Nullable
    private UUID ownerId;
    private boolean futureSchemaBlocked;
    private boolean invalidDataBlocked;
    @Nullable
    private Tag preservedBlockedData;

    public SatelliteTerminalBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.SATELLITE_TERMINAL.get(), position, state);
        createCapabilities();
    }

    public static void serverTick(
            Level level,
            BlockPos position,
            BlockState state,
            SatelliteTerminalBlockEntity terminal
    ) {
        terminal.chargeFromRedstone();
        boolean lit = !terminal.blocked()
                && terminal.energyStorage.getEnergyStored() >= LAUNCH_POWER_THRESHOLD;
        if (state.getValue(SatelliteTerminalBlock.LIT) != lit) {
            level.setBlock(position, state.setValue(SatelliteTerminalBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    public void setOwner(UUID owner) {
        if (!blocked() && ownerId == null && owner != null) {
            ownerId = owner;
            setChanged();
        }
    }

    /** Format 2 (ADR-049 section 10): the unchanged format-1 content, then a flag that a terminal view follows. */
    public void writeMenuOpenData(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(worldPosition);
        SatelliteTerminalTargets.current().write(buffer);
        buffer.writeBoolean(true);
    }

    public boolean handleButton(ServerPlayer player, int buttonId) {
        if (!isLoadedNearby(player)) {
            updateResult(player, SatelliteOperationCode.OUT_OF_RANGE);
            return false;
        }
        if (blocked()) {
            updateResult(player, SatelliteOperationCode.UNSUPPORTED_DATA);
            return false;
        }
        boolean ownerMismatch = ownerId != null && !ownerId.equals(player.getUUID());
        boolean operatorCancellation = player.hasPermissions(2)
                && buttonId == SatelliteTerminalMenu.BUTTON_CANCEL;
        if (ownerMismatch && !operatorCancellation) {
            updateResult(player, SatelliteOperationCode.UNAUTHORIZED);
            return false;
        }
        switch (buttonId) {
            case SatelliteTerminalMenu.BUTTON_PREVIOUS -> selectTarget(-1);
            case SatelliteTerminalMenu.BUTTON_NEXT -> selectTarget(1);
            case SatelliteTerminalMenu.BUTTON_ASSEMBLE -> assemble(player);
            case SatelliteTerminalMenu.BUTTON_LAUNCH -> launchOrStart(player);
            case SatelliteTerminalMenu.BUTTON_CLAIM -> claim(player);
            case SatelliteTerminalMenu.BUTTON_CANCEL -> cancel(player);
            case SatelliteTerminalMenu.BUTTON_DECOMMISSION -> decommission(player);
            case SatelliteTerminalMenu.BUTTON_UNLINK -> unlink(player);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** ADR-049 section 10: an intent that arrived too soon after the last one changes nothing. */
    void reportRateLimited(ServerPlayer player) {
        updateResult(player, SatelliteOperationCode.RATE_LIMITED);
    }

    public boolean canAccess(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID()) || player.hasPermissions(2);
    }

    private void selectTarget(int delta) {
        List<ResourceLocation> targets = targets();
        if (targets.isEmpty()) {
            lastResult = SatelliteOperationCode.CATALOG_UNAVAILABLE;
            return;
        }
        selectedTargetIndex = Math.floorMod(selectedTargetIndex + delta, targets.size());
        lastResult = SatelliteOperationCode.SUCCESS;
        setChanged();
    }

    private void assemble(ServerPlayer player) {
        if (ownerId == null) {
            ownerId = player.getUUID();
        }
        if (!ownerId.equals(player.getUUID()) && !player.hasPermissions(2)) {
            updateResult(player, SatelliteOperationCode.UNAUTHORIZED);
            return;
        }
        if (energyStorage.getEnergyStored() < ASSEMBLY_ENERGY) {
            updateResult(player, SatelliteOperationCode.NO_POWER);
            return;
        }
        ItemStack chip = inventory.getStackInSlot(SLOT_CONTROL_CHIP);
        ResourceLocation definition = SatellitePayloadRuntime.definitionFor(inventory.getStackInSlot(SLOT_DATA_STORAGE));
        if (!inventory.isItemValid(SLOT_CHASSIS, inventory.getStackInSlot(SLOT_CHASSIS))
                || !inventory.isItemValid(SLOT_SOLAR_MODULE, inventory.getStackInSlot(SLOT_SOLAR_MODULE))
                || definition == null
                || !chip.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                || SatelliteItemData.read(chip).status() != SatelliteItemData.DecodeStatus.EMPTY) {
            updateResult(player, SatelliteOperationCode.INVALID_COMPONENTS);
            return;
        }
        if (!inventory.getStackInSlot(SLOT_PACKAGE).isEmpty()) {
            updateResult(player, SatelliteOperationCode.OUTPUT_BLOCKED);
            return;
        }
        if (SatelliteRuntime.targets(definition).isEmpty()) {
            updateResult(player, SatelliteOperationCode.CATALOG_UNAVAILABLE);
            return;
        }

        SatelliteIdentity identity = new SatelliteIdentity(
                UUID.randomUUID(),
                player.getUUID(),
                definition
        );
        ItemStack boundChip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get());
        SatelliteItemData.write(boundChip, identity);
        ItemStack satellitePackage = new ItemStack(ModItems.DATA_SATELLITE_PACKAGE.get());
        SatelliteItemData.write(satellitePackage, identity);

        // R2-L1: the bound chip is written first, so the selected definition never changes during assembly
        // and the chosen target survives it.
        inventory.setStackInSlot(SLOT_CONTROL_CHIP, boundChip);
        inventory.extractItem(SLOT_CHASSIS, 1, false);
        inventory.extractItem(SLOT_SOLAR_MODULE, 1, false);
        inventory.extractItem(SLOT_DATA_STORAGE, 1, false);
        inventory.setStackInSlot(SLOT_PACKAGE, satellitePackage);
        energyStorage.consume(ASSEMBLY_ENERGY);
        updateResult(player, SatelliteOperationCode.SUCCESS);
    }

    private void launchOrStart(ServerPlayer player) {
        if (energyStorage.getEnergyStored() < LAUNCH_POWER_THRESHOLD) {
            updateResult(player, SatelliteOperationCode.NO_POWER);
            return;
        }
        SatelliteIdentity chip = validOwnedIdentity(
                player,
                inventory.getStackInSlot(SLOT_CONTROL_CHIP),
                ModItems.SATELLITE_CONTROL_CHIP.get()
        ).orElse(null);
        if (chip == null) {
            return;
        }
        ResourceLocation target = selectedTarget();
        if (target == null) {
            updateResult(player, SatelliteOperationCode.TARGET_NOT_ALLOWED);
            return;
        }

        ItemStack packageStack = inventory.getStackInSlot(SLOT_PACKAGE);
        SatelliteOperationResult result;
        if (!packageStack.isEmpty()) {
            if (!SatelliteTerminalInventory.validPackage(packageStack)) {
                updateResult(player, SatelliteOperationCode.RECEIVER_REQUIRED);
                return;
            }
            SatelliteIdentity satellitePackage = validOwnedIdentity(
                    player,
                    packageStack,
                    packageStack.getItem()
            ).orElse(null);
            if (satellitePackage == null || !satellitePackage.equals(chip)) {
                updateResult(player, SatelliteOperationCode.RECEIVER_REQUIRED);
                return;
            }
            result = SatelliteRuntime.launch(player, chip, target);
            if (result.success()) {
                inventory.extractItem(SLOT_PACKAGE, 1, false);
            }
        } else {
            result = SatelliteRuntime.startMission(player, chip, target);
        }
        updateResult(player, result.code());
    }

    private void claim(ServerPlayer player) {
        SatelliteIdentity chip = validOwnedIdentity(
                player,
                inventory.getStackInSlot(SLOT_CONTROL_CHIP),
                ModItems.SATELLITE_CONTROL_CHIP.get()
        ).orElse(null);
        if (chip == null) {
            return;
        }
        SatelliteOperationResult result = SatelliteRuntime.claim(player, chip);
        updateResult(player, result.code());
    }

    private void cancel(ServerPlayer player) {
        SatelliteIdentity chip = validOwnedIdentity(
                player,
                inventory.getStackInSlot(SLOT_CONTROL_CHIP),
                ModItems.SATELLITE_CONTROL_CHIP.get()
        ).orElse(null);
        if (chip == null) {
            return;
        }
        SatelliteOperationResult result = SatelliteRuntime.cancel(player, chip);
        updateResult(player, result.code());
    }

    /**
     * ADR-049 section 7: the registry removes the idle satellite with a barrier flush, and only then is
     * the chip blanked. A crash in between leaves an inert chip, never a second satellite.
     */
    private void decommission(ServerPlayer player) {
        SatelliteIdentity chip = validOwnedIdentity(
                player,
                inventory.getStackInSlot(SLOT_CONTROL_CHIP),
                ModItems.SATELLITE_CONTROL_CHIP.get()
        ).orElse(null);
        if (chip == null) {
            return;
        }
        SatelliteOperationResult result = SatelliteRuntime.decommission(player, chip);
        if (result.success()) {
            inventory.setStackInSlot(SLOT_CONTROL_CHIP, new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()));
        }
        updateResult(player, result.code());
    }

    private void unlink(ServerPlayer player) {
        SatelliteIdentity chip = validOwnedIdentity(
                player,
                inventory.getStackInSlot(SLOT_CONTROL_CHIP),
                ModItems.SATELLITE_CONTROL_CHIP.get()
        ).orElse(null);
        if (chip != null) {
            updateResult(player, SatelliteRuntime.unlink(player, chip).code());
        }
    }

    private Optional<SatelliteIdentity> validOwnedIdentity(
            ServerPlayer player,
            ItemStack stack,
            net.minecraft.world.item.Item requiredItem
    ) {
        if (!stack.is(requiredItem)) {
            updateResult(player, SatelliteOperationCode.RECEIVER_REQUIRED);
            return Optional.empty();
        }
        SatelliteItemData.DecodeResult decoded = SatelliteItemData.read(stack);
        if (decoded.status() != SatelliteItemData.DecodeStatus.VALID) {
            updateResult(player, decoded.status() == SatelliteItemData.DecodeStatus.FUTURE
                    ? SatelliteOperationCode.UNSUPPORTED_DATA
                    : SatelliteOperationCode.RECEIVER_REQUIRED);
            return Optional.empty();
        }
        SatelliteIdentity identity = decoded.identity().orElseThrow();
        if (!identity.ownerId().equals(player.getUUID()) && !player.hasPermissions(2)) {
            updateResult(player, SatelliteOperationCode.UNAUTHORIZED);
            return Optional.empty();
        }
        return Optional.of(identity);
    }

    /** C7-L6: when the chip or payload selects another definition, its targets start from the first. */
    private void inventoryChanged(int slot) {
        if (slot == SLOT_CONTROL_CHIP || slot == SLOT_DATA_STORAGE) {
            ResourceLocation definition = selectedDefinition();
            if (!java.util.Objects.equals(definition, targetDefinition)) {
                targetDefinition = definition;
                selectedTargetIndex = 0;
            }
        }
        setChanged();
    }

    private void chargeFromRedstone() {
        if (blocked()) {
            return;
        }
        ItemStack charge = inventory.getStackInSlot(SLOT_CHARGE);
        if (!charge.is(Items.REDSTONE)
                || energyStorage.getEnergyStored() > ENERGY_CAPACITY - REDSTONE_ENERGY) {
            return;
        }
        inventory.extractItem(SLOT_CHARGE, 1, false);
        energyStorage.add(REDSTONE_ENERGY);
    }

    private boolean isLoadedNearby(ServerPlayer player) {
        return level != null
                && level.hasChunkAt(worldPosition)
                && player.level() == level
                && player.distanceToSqr(
                        worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D
                ) <= 64.0D
                && level.getBlockEntity(worldPosition) == this;
    }

    private void updateResult(ServerPlayer player, SatelliteOperationCode code) {
        lastResult = code;
        setChanged();
        player.displayClientMessage(Component.translatable(code.translationKey()), true);
    }

    List<ResourceLocation> targetList() {
        return targets();
    }

    private List<ResourceLocation> targets() {
        ResourceLocation definition = selectedDefinition();
        return definition == null ? List.of() : SatelliteRuntime.targets(definition);
    }

    ResourceLocation selectedDefinition() {
        ItemStack chip = inventory.getStackInSlot(SLOT_CONTROL_CHIP);
        var decoded = SatelliteItemData.read(chip);
        if (decoded.identity().isPresent()) { return decoded.identity().orElseThrow().definitionId(); }
        if (decoded.status() != SatelliteItemData.DecodeStatus.EMPTY) { return null; }
        ItemStack payload = inventory.getStackInSlot(SLOT_DATA_STORAGE);
        return payload.isEmpty() ? SatelliteIds.DATA_SATELLITE : SatellitePayloadRuntime.definitionFor(payload);
    }

    @Nullable
    private ResourceLocation selectedTarget() {
        List<ResourceLocation> targets = targets();
        if (targets.isEmpty()) {
            return null;
        }
        selectedTargetIndex = Math.floorMod(selectedTargetIndex, targets.size());
        return targets.get(selectedTargetIndex);
    }

    Optional<SatelliteIdentity> chipIdentity() {
        return SatelliteItemData.read(inventory.getStackInSlot(SLOT_CONTROL_CHIP)).identity();
    }

    int energyStored() {
        return energyStorage.getEnergyStored();
    }

    int selectedTargetIndex() {
        List<ResourceLocation> targets = targets();
        return targets.isEmpty() ? 0 : Math.floorMod(selectedTargetIndex, targets.size());
    }

    int lastResultId() {
        return lastResult.ordinal();
    }

    int researchBalance(UUID viewerId) {
        if (level == null || level.isClientSide || level.getServer() == null) {
            return 0;
        }
        UUID account = ownerId == null ? viewerId : ownerId;
        try {
            return SatelliteRuntime.researchBalance(level.getServer(), account);
        } catch (RuntimeException exception) {
            return 0;
        }
    }

    int researchBalanceLow(UUID viewerId) {
        return researchBalance(viewerId) & 0xFFFF;
    }

    int researchBalanceHigh(UUID viewerId) {
        return researchBalance(viewerId) >>> 16;
    }

    int missionStatusId() {
        return currentMission().map(mission -> mission.status().ordinal() + 1).orElse(0);
    }

    int missionDurationSeconds() {
        MissionState mission = currentMission().orElse(null);
        if (mission == null) {
            return 0;
        }
        long durationTicks = mission.completesAtLogicalTime() - mission.startedAtLogicalTime();
        return (int) Math.min(Integer.MAX_VALUE, (durationTicks + 19L) / 20L);
    }

    int remainingSeconds() {
        if (level == null || level.getServer() == null) {
            return 0;
        }
        MissionState mission = currentMission().orElse(null);
        if (mission == null || mission.status() != io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus.ACTIVE) {
            return 0;
        }
        long remainingTicks = Math.max(
                0L,
                mission.completesAtLogicalTime() - level.getServer().overworld().getGameTime()
        );
        return (int) Math.min(Integer.MAX_VALUE, (remainingTicks + 19L) / 20L);
    }

    int selectedTargetDiscovered() {
        if (level == null || level.isClientSide || level.getServer() == null || selectedTarget() == null) {
            return 0;
        }
        return SatelliteRuntime.discovered(level.getServer(), selectedTarget()) ? 1 : 0;
    }

    int ownedBy(UUID viewerId) {
        return ownerId == null || ownerId.equals(viewerId) ? 1 : 0;
    }

    private Optional<MissionState> currentMission() {
        if (level == null || level.isClientSide || level.getServer() == null) {
            return Optional.empty();
        }
        return chipIdentity().flatMap(identity -> SatelliteRuntime.currentMission(level.getServer(), identity));
    }

    public net.minecraftforge.items.IItemHandler menuInventory() {
        return inventory;
    }

    public void copyInventoryTo(Container target) {
        for (int slot = 0; slot < Math.min(target.getContainerSize(), SLOT_COUNT); slot++) {
            target.setItem(slot, inventory.getStackInSlot(slot).copy());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.advancedrocketrycommunity.satellite_terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SatelliteTerminalMenu(id, playerInventory, this, SatelliteTerminalTargets.current());
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
        // Persist the raw bounded choice. Runtime catalogs are deliberately
        // unavailable during part of integrated-server shutdown.
        data.putInt("selected_target", selectedTargetIndex);
        data.putInt("last_result", lastResult.ordinal());
        if (ownerId != null) {
            data.putUUID("owner_id", ownerId);
        }
        if (!boundedRoot(data)) {
            throw new IllegalStateException("Satellite terminal exceeds its fixed NBT bound");
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
        if (!boundedRoot(raw) || !(raw instanceof CompoundTag)) {
            invalidDataBlocked = true;
            preservedBlockedData = boundedRoot(raw) ? raw.copy() : raw;
            lastResult = SatelliteOperationCode.UNSUPPORTED_DATA;
            return;
        }
        CompoundTag data = parent.getCompound(DATA_KEY);
        if (!data.contains("schema_version", Tag.TAG_INT)) {
            invalidDataBlocked = true;
            preservedBlockedData = data.copy();
            lastResult = SatelliteOperationCode.UNSUPPORTED_DATA;
            return;
        }
        int schema = data.getInt("schema_version");
        if (schema > SCHEMA_VERSION) {
            futureSchemaBlocked = true;
            preservedBlockedData = data.copy();
            lastResult = SatelliteOperationCode.UNSUPPORTED_DATA;
            return;
        }
        try {
            if (schema != SCHEMA_VERSION
                    || !java.util.Set.of("schema_version", "inventory", "energy", "selected_target", "last_result", "owner_id")
                            .containsAll(data.getAllKeys())
                    || data.contains("owner_id") && !data.hasUUID("owner_id")
                    || !data.contains("inventory", Tag.TAG_COMPOUND)
                    || !data.contains("energy", Tag.TAG_INT)
                    || !data.contains("selected_target", Tag.TAG_INT)
                    || !data.contains("last_result", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Satellite terminal data is incomplete");
            }
            int energy = data.getInt("energy");
            int target = data.getInt("selected_target");
            int result = data.getInt("last_result");
            if (energy < 0 || energy > ENERGY_CAPACITY
                    || target < 0 || target >= SatelliteLimits.MAX_TARGETS_PER_DEFINITION
                    || result < 0 || result >= SatelliteOperationCode.values().length) {
                throw new IllegalArgumentException("Satellite terminal scalar is outside fixed bounds");
            }
            inventory.loadValidated(data.getCompound("inventory"));
            energyStorage.set(energy);
            targetDefinition = selectedDefinition();
            selectedTargetIndex = target;
            lastResult = SatelliteOperationCode.values()[result];
            if (data.hasUUID("owner_id")) {
                ownerId = data.getUUID("owner_id");
            }
        } catch (RuntimeException exception) {
            resetLoadedState();
            invalidDataBlocked = true;
            preservedBlockedData = data.copy();
            lastResult = SatelliteOperationCode.UNSUPPORTED_DATA;
        }
    }

    private void resetLoadedState() {
        inventory.setSize(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        energyStorage.set(0);
        selectedTargetIndex = 0;
        targetDefinition = null;
        lastResult = SatelliteOperationCode.SUCCESS;
        ownerId = null;
        futureSchemaBlocked = false;
        invalidDataBlocked = false;
        preservedBlockedData = null;
    }

    boolean blocked() {
        return futureSchemaBlocked || invalidDataBlocked;
    }

    static boolean boundedRoot(Tag tag) { return BoundedNbt.fits(tag, MAX_TERMINAL_NBT_BYTES, 20, 2048); }

    boolean canCarryData() { return preservedBlockedData == null || boundedRoot(preservedBlockedData); }

    CompoundTag carriedData() {
        if (!canCarryData()) { throw new IllegalStateException("Terminal root cannot be carried safely"); }
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

    private final class TerminalEnergyStorage implements IEnergyStorage {
        private int stored;

        @Override
        public int receiveEnergy(int maximum, boolean simulate) {
            if (blocked()) {
                return 0;
            }
            int received = Math.min(Math.max(0, maximum), ENERGY_CAPACITY - stored);
            if (!simulate && received > 0) {
                stored += received;
                setChanged();
            }
            return received;
        }

        @Override
        public int extractEnergy(int maximum, boolean simulate) {
            return 0;
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
            return false;
        }

        @Override
        public boolean canReceive() {
            return !blocked();
        }

        private void add(int amount) {
            stored = Math.min(ENERGY_CAPACITY, Math.addExact(stored, amount));
            setChanged();
        }

        private void consume(int amount) {
            if (amount < 0 || amount > stored) {
                throw new IllegalArgumentException("Invalid terminal energy consumption");
            }
            stored -= amount;
            setChanged();
        }

        private void set(int value) {
            stored = value;
        }
    }
}

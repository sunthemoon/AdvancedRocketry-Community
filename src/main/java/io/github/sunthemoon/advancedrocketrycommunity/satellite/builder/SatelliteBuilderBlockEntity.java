package io.github.sunthemoon.advancedrocketrycommunity.satellite.builder;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteBlueprints;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.component.SatelliteComponentDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.ArrayList;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Satellite Builder (ADR-049 section 5): assembles the four non-data kinds from components in one tick of one
 * block entity. It never touches the registry; the identity binds chip and package, and stats are
 * re-derived at launch.
 */
public final class SatelliteBuilderBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_CHASSIS = 0;
    public static final int SLOT_PRIMARY = 1;
    public static final int SLOT_MODULE_FIRST = 2;
    public static final int SLOT_MODULE_LAST = 7;
    public static final int SLOT_CHIP = 8;
    public static final int SLOT_OUTPUT = 9;
    /** Revision-4 amendment: the builder charges from redstone like the terminal (no FE source exists yet). */
    public static final int SLOT_CHARGE = 10;
    public static final int SLOT_COUNT = 11;
    public static final int MENU_DATA_COUNT = 12;
    public static final int ENERGY_CAPACITY = 10_000;
    public static final int REDSTONE_ENERGY = 2_000;
    public static final int ASSEMBLY_ENERGY = 1_000;
    public static final int ASSEMBLY_COOLDOWN_TICKS = 20;

    static final String DATA_KEY = "SatelliteBuilder";
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_BUILDER_NBT_BYTES = 64 * 1024;
    private static final Set<String> ROOT_KEYS = Set.of("schema_version", "inventory", "energy", "last_result");

    private final SatelliteBuilderInventory inventory =
            new SatelliteBuilderInventory(() -> blocked() || isRemoved(), this::inventoryChanged);
    private final BuilderEnergyStorage energyStorage = new BuilderEnergyStorage();
    private final IItemHandler automation = new AutomationHandler();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();

    private SatelliteOperationCode lastResult = SatelliteOperationCode.SUCCESS;
    private long lastAssemblyTick = Long.MIN_VALUE;
    private int contentVersion;
    private Preview cachedPreview;
    private int cachedContentVersion;
    private long cachedGeneration;
    private int cachedEnergy;
    private boolean futureSchemaBlocked;
    private boolean invalidDataBlocked;
    @Nullable
    private Tag preservedBlockedData;

    public SatelliteBuilderBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.SATELLITE_BUILDER.get(), position, state);
        createCapabilities();
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, SatelliteBuilderBlockEntity builder) {
        builder.chargeFromRedstone();
    }

    /** The only builder intent (ADR-049 section 5); carries no item, stat or kind. */
    public boolean assemble(ServerPlayer player) {
        if (!isLoadedNearby(player)) {
            updateResult(player, SatelliteOperationCode.OUT_OF_RANGE);
            return false;
        }
        if (blocked()) {
            updateResult(player, SatelliteOperationCode.UNSUPPORTED_DATA);
            return false;
        }
        long now = level.getGameTime();
        if (lastAssemblyTick != Long.MIN_VALUE && now - lastAssemblyTick < ASSEMBLY_COOLDOWN_TICKS) {
            updateResult(player, SatelliteOperationCode.RATE_LIMITED);
            return false;
        }
        Preview preview = evaluate(player.getUUID(), true);
        if (preview.code() != SatelliteOperationCode.SUCCESS) {
            updateResult(player, preview.code());
            return false;
        }
        SatelliteKindDefinition definition = preview.definition().orElseThrow();
        List<ResourceLocation> components = preview.components();
        SatelliteIdentity identity = new SatelliteIdentity(
                UUID.randomUUID(), player.getUUID(), definition.id(), definition.kind(), components);
        ItemStack chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get());
        SatelliteItemData.write(chip, identity);
        ItemStack satellitePackage = new ItemStack(ModItems.SATELLITE_PACKAGE.get());
        SatelliteItemData.write(satellitePackage, identity);
        for (int slot = SLOT_CHASSIS; slot <= SLOT_MODULE_LAST; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                inventory.extractItem(slot, 1, false);
            }
        }
        inventory.setStackInSlot(SLOT_CHIP, chip);
        inventory.setStackInSlot(SLOT_OUTPUT, satellitePackage);
        energyStorage.consume(ASSEMBLY_ENERGY);
        lastAssemblyTick = now;
        updateResult(player, SatelliteOperationCode.SUCCESS);
        return true;
    }

    /**
     * The frozen refusal order of ADR-049 section 4: output, layout, definition, stat limit, requirement,
     * research, energy. {@code withResearch} is false for the menu preview of another viewer.
     */
    Preview evaluate(UUID playerId, boolean withResearch) {
        if (!inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            return Preview.refused(SatelliteOperationCode.OUTPUT_BLOCKED);
        }
        SatelliteCatalog catalog = SatelliteRuntime.catalog().orElse(null);
        if (catalog == null) {
            return Preview.refused(SatelliteOperationCode.CATALOG_UNAVAILABLE);
        }
        SatelliteComponentCatalog components = catalog.components();
        ItemStack chip = inventory.getStackInSlot(SLOT_CHIP);
        Optional<ResourceLocation> chassis = itemId(inventory.getStackInSlot(SLOT_CHASSIS));
        Optional<ResourceLocation> primary = itemId(inventory.getStackInSlot(SLOT_PRIMARY));
        List<ResourceLocation> modules = new ArrayList<>();
        for (int slot = SLOT_MODULE_FIRST; slot <= SLOT_MODULE_LAST; slot++) {
            itemId(inventory.getStackInSlot(slot)).ifPresent(modules::add);
        }
        Optional<SatelliteKind> kind = primary.flatMap(components::get).flatMap(SatelliteComponentDefinition::kind);
        if (!chip.is(ModItems.SATELLITE_CONTROL_CHIP.get())
                || SatelliteItemData.read(chip).status() != SatelliteItemData.DecodeStatus.EMPTY
                || kind.isEmpty()) {
            return Preview.refused(SatelliteOperationCode.INVALID_COMPONENTS);
        }
        SatelliteBlueprints.Evaluation layout =
                SatelliteBlueprints.evaluate(chassis, primary, modules, kind.get(), 0, components);
        if (layout.code() == SatelliteOperationCode.INVALID_COMPONENTS) {
            return Preview.refused(SatelliteOperationCode.INVALID_COMPONENTS);
        }
        SatelliteKindDefinition definition = catalog.kindDefinitionForPrimary(primary.get()).orElse(null);
        if (definition == null) {
            return Preview.refused(SatelliteOperationCode.DEFINITION_NOT_FOUND);
        }
        SatelliteBlueprints.Evaluation evaluation = SatelliteBlueprints.evaluate(
                chassis, primary, modules, kind.get(), definition.scanEnergy(), components);
        if (!evaluation.accepted()) {
            return new Preview(evaluation.code(), Optional.of(definition), Optional.empty(), List.of());
        }
        SatelliteStats stats = evaluation.stats().orElseThrow();
        List<ResourceLocation> identityComponents = SatelliteBlueprints.components(chassis.get(), primary.get(), modules);
        if (withResearch && level != null && level.getServer() != null
                && SatelliteRuntime.lifetimeResearch(level.getServer(), playerId) < definition.requiredLifetimeResearch()) {
            return new Preview(SatelliteOperationCode.RESEARCH_LOCKED, Optional.of(definition), Optional.of(stats),
                    identityComponents);
        }
        if (energyStorage.getEnergyStored() < ASSEMBLY_ENERGY) {
            return new Preview(SatelliteOperationCode.NO_POWER, Optional.of(definition), Optional.of(stats),
                    identityComponents);
        }
        return new Preview(SatelliteOperationCode.SUCCESS, Optional.of(definition), Optional.of(stats), identityComponents);
    }

    /** Menu preview, recomputed only when the inventory, energy or catalog changed. */
    Preview preview() {
        long generation = SatelliteRuntime.catalogGeneration();
        int energy = energyStorage.getEnergyStored();
        if (cachedPreview == null || cachedContentVersion != contentVersion || cachedGeneration != generation
                || cachedEnergy != energy) {
            cachedPreview = blocked() ? Preview.refused(SatelliteOperationCode.UNSUPPORTED_DATA) : evaluate(new UUID(0L, 0L), false);
            cachedContentVersion = contentVersion;
            cachedGeneration = generation;
            cachedEnergy = energy;
        }
        return cachedPreview;
    }

    private static Optional<ResourceLocation> itemId(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.ofNullable(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    private void chargeFromRedstone() {
        if (blocked()) {
            return;
        }
        ItemStack charge = inventory.getStackInSlot(SLOT_CHARGE);
        if (!charge.is(Items.REDSTONE) || energyStorage.getEnergyStored() > ENERGY_CAPACITY - REDSTONE_ENERGY) {
            return;
        }
        inventory.extractItem(SLOT_CHARGE, 1, false);
        energyStorage.add(REDSTONE_ENERGY);
    }

    private boolean isLoadedNearby(ServerPlayer player) {
        return level != null
                && player.isAlive()
                && level.hasChunkAt(worldPosition)
                && player.level() == level
                && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                        worldPosition.getZ() + 0.5D) <= 64.0D
                && level.getBlockEntity(worldPosition) == this;
    }

    /** ADR-049 section 10: an intent that arrived too soon after the last one changes nothing. */
    void reportRateLimited(ServerPlayer player) {
        updateResult(player, SatelliteOperationCode.RATE_LIMITED);
    }

    private void updateResult(ServerPlayer player, SatelliteOperationCode code) {
        lastResult = code;
        setChanged();
        player.displayClientMessage(Component.translatable(code.translationKey()), true);
    }

    private void inventoryChanged() {
        contentVersion++;
        setChanged();
    }

    int energyStored() {
        return energyStorage.getEnergyStored();
    }

    int lastResultId() {
        return lastResult.ordinal();
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
        return Component.translatable("menu.advancedrocketrycommunity.satellite_builder");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new SatelliteBuilderMenu(id, playerInventory, this, SatelliteRuntime.catalogGeneration());
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
        data.putInt("last_result", lastResult.ordinal());
        if (!boundedRoot(data)) {
            throw new IllegalStateException("Satellite builder exceeds its fixed NBT bound");
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
            if (schema != SCHEMA_VERSION || !ROOT_KEYS.equals(data.getAllKeys())
                    || !data.contains("inventory", Tag.TAG_COMPOUND)
                    || !data.contains("energy", Tag.TAG_INT)
                    || !data.contains("last_result", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Satellite builder data is incomplete");
            }
            int energy = data.getInt("energy");
            int result = data.getInt("last_result");
            if (energy < 0 || energy > ENERGY_CAPACITY || result < 0 || result >= SatelliteOperationCode.values().length) {
                throw new IllegalArgumentException("Satellite builder scalar is outside fixed bounds");
            }
            inventory.loadValidated(data.getCompound("inventory"));
            energyStorage.set(energy);
            lastResult = SatelliteOperationCode.values()[result];
        } catch (RuntimeException exception) {
            resetLoadedState();
            block(false, data);
        }
    }

    private void block(boolean future, Tag raw) {
        futureSchemaBlocked = future;
        invalidDataBlocked = !future;
        preservedBlockedData = boundedRoot(raw) ? raw.copy() : raw;
        lastResult = SatelliteOperationCode.UNSUPPORTED_DATA;
    }

    private void resetLoadedState() {
        inventory.setSize(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        energyStorage.set(0);
        lastResult = SatelliteOperationCode.SUCCESS;
        futureSchemaBlocked = false;
        invalidDataBlocked = false;
        preservedBlockedData = null;
    }

    boolean blocked() {
        return futureSchemaBlocked || invalidDataBlocked;
    }

    static boolean boundedRoot(Tag tag) {
        return BoundedNbt.fits(tag, MAX_BUILDER_NBT_BYTES, 20, 2048);
    }

    boolean canCarryData() {
        return preservedBlockedData == null || boundedRoot(preservedBlockedData);
    }

    CompoundTag carriedData() {
        if (!canCarryData()) {
            throw new IllegalStateException("Builder root cannot be carried safely");
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
        itemCapability = LazyOptional.of(() -> automation);
        energyCapability = LazyOptional.of(() -> energyStorage);
    }

    /** Automation inserts only matching components and extracts only the package (ADR-049 section 5). */
    private final class AutomationHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            // C7-L11: automation inserts only components and redstone, never the chip.
            return slot == SLOT_CHIP ? stack : inventory.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == SLOT_OUTPUT ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot != SLOT_CHIP && inventory.isItemValid(slot, stack);
        }
    }

    private final class BuilderEnergyStorage implements IEnergyStorage {
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
                throw new IllegalArgumentException("Invalid builder energy consumption");
            }
            stored -= amount;
            setChanged();
        }

        private void set(int value) {
            stored = value;
        }
    }

    /** Evaluation result for the intent and the menu preview. */
    record Preview(
            SatelliteOperationCode code,
            Optional<SatelliteKindDefinition> definition,
            Optional<SatelliteStats> stats,
            List<ResourceLocation> components
    ) {
        static Preview refused(SatelliteOperationCode code) {
            return new Preview(code, Optional.empty(), Optional.empty(), List.of());
        }
    }
}

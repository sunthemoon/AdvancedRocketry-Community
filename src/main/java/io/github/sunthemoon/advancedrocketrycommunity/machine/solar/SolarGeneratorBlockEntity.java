package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/** Single bounded buffer and lifecycle bridge. Context failure does not disable export of retained energy. */
public final class SolarGeneratorBlockEntity extends BlockEntity implements MenuProvider {
    private final Supplier<MenuType<SolarGeneratorMenu>> menuType;
    private final BooleanSupplier enabled;
    private final IntSupplier multiplier;
    private final Supplier<SolarExposure> exposure;
    private int energy;
    private Tag refusedRoot;
    private boolean nonAdmissible;
    private boolean operationLock;
    private long capEpoch;
    private long outputTick = Long.MIN_VALUE;
    private int outputThisTick;
    private SolarGeneration.Step feedback = SolarGeneration.tick(0, 1, true, false, null);
    private LazyOptional<IEnergyStorage> energyCap;

    public SolarGeneratorBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state,
            Supplier<MenuType<SolarGeneratorMenu>> menuType, BooleanSupplier enabled,
            IntSupplier multiplier, Supplier<SolarExposure> exposure) {
        super(type, position, state);
        this.menuType = Objects.requireNonNull(menuType, "menuType");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.multiplier = Objects.requireNonNull(multiplier, "multiplier");
        this.exposure = Objects.requireNonNull(exposure, "exposure");
        createCaps();
    }

    public static void serverTick(ServerLevel level, BlockPos position, BlockState state,
                                  SolarGeneratorBlockEntity generator) {
        if (generator.isRemoved() || generator.getLevel() != level || !position.equals(generator.getBlockPos())
                || !level.getServer().isSameThread() || generator.operationLock) { return; }
        boolean enabled = generator.enabled();
        // Repair and disable precede every exposure, catalog, chunk and neighbor read.
        SolarGeneration.Environment environment = SolarGeneration.Environment.unavailable();
        if (!generator.repairRequired() && enabled) {
            SolarExposure handle = generator.exposure.get();
            if (handle != null) { environment = handle.read(level, position, generator); }
        }
        generator.feedback = SolarGeneration.tick(generator.energy, generator.multiplier.getAsInt(), enabled,
                generator.repairRequired(), environment);
        if (generator.feedback.energy() != generator.energy) {
            generator.energy = generator.feedback.energy();
            generator.setChanged();
        }
        if (!generator.repairRequired() && enabled) { generator.push(level); }
    }

    public int energyStored() { return energy; }
    public int actualCredit() { return feedback.credit(); }
    public SolarGeneration.Reason reason() { return repairRequired() ? SolarGeneration.Reason.REPAIR_REQUIRED : feedback.reason(); }
    public SolarGeneration.Environment environment() { return feedback.environment(); }
    public boolean repairRequired() { return refusedRoot != null; }
    boolean enabled() { return enabled.getAsBoolean(); }

    /** Public only inside the mod's adapters/tests, not an exported API. Does not create a missing BE. */
    public boolean available() {
        if (repairRequired() || operationLock || isRemoved() || !(level instanceof ServerLevel serverLevel)
                || !serverLevel.getServer().isSameThread()
                || serverLevel.getServer().getLevel(serverLevel.dimension()) != serverLevel
                || !serverLevel.isInWorldBounds(worldPosition)) { return false; }
        SolarExposure handle = exposure.get();
        if (handle == null || !handle.owns(serverLevel.getServer())) { return false; }
        var chunk = serverLevel.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntities().get(worldPosition) == this;
    }
    boolean currentPort(long epoch) { return enabled() && epoch == capEpoch && available(); }

    int extractEnergy(long epoch, int maximum, boolean simulate) {
        if (maximum <= 0 || !currentPort(epoch)) { return 0; }
        int amount = Math.min(maximum, Math.min(energy, remainingOutput()));
        if (!simulate && amount > 0) { debit(amount); }
        return amount;
    }

    /** Simulation reads an epoch-relative allowance without resetting any quota state. */
    private int remainingOutput() {
        return level == null ? 0 : outputTick == level.getGameTime()
                ? SolarGeneration.OUTPUT_PER_TICK - outputThisTick : SolarGeneration.OUTPUT_PER_TICK;
    }
    private void debit(int amount) {
        long tick = level.getGameTime();
        if (outputTick != tick) { outputTick = tick; outputThisTick = 0; }
        energy -= amount;
        outputThisTick += amount;
        setChanged();
    }
    private void push(ServerLevel serverLevel) {
        if (!available()) { return; }
        for (Direction direction : Direction.values()) {
            int offer = Math.min(energy, remainingOutput());
            if (offer <= 0 || !enabled() || !available()) { return; }
            BlockPos neighbor = worldPosition.relative(direction);
            if (!serverLevel.isInWorldBounds(neighbor)) { continue; }
            var chunk = serverLevel.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
            BlockEntity receiver = chunk == null ? null : chunk.getBlockEntities().get(neighbor);
            if (receiver == null || receiver.isRemoved() || receiver instanceof SolarGeneratorBlockEntity) { continue; }
            operationLock = true;
            try {
                IEnergyStorage port = receiver.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite())
                        .resolve().orElse(null);
                if (port != null && port.canReceive()) {
                    int accepted = Math.max(0, Math.min(offer, port.receiveEnergy(offer, false)));
                    if (accepted > 0) { debit(accepted); }
                }
            } finally { operationLock = false; }
        }
    }

    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        // No throwing/reset fallback here: native BE exception handling would otherwise omit the whole BE.
        outer.put(SolarSave.ROOT, refusedRoot == null ? SolarSave.encode(energy)
                : nonAdmissible ? refusedRoot : refusedRoot.copy());
    }
    @Override public void load(CompoundTag outer) {
        super.load(outer);
        energy = 0;
        refusedRoot = null;
        nonAdmissible = false;
        outputTick = Long.MIN_VALUE;
        outputThisTick = 0;
        if (outer.contains(SolarSave.ROOT)) {
            Tag raw = outer.get(SolarSave.ROOT);
            nonAdmissible = !SolarSave.bounded(raw);
            if (nonAdmissible) { refusedRoot = raw; }
            else {
                try { energy = SolarSave.decode(raw); }
                catch (IllegalArgumentException refused) { refusedRoot = raw.copy(); }
            }
        }
        feedback = SolarGeneration.tick(energy, 1, true, repairRequired(), null);
    }
    private void createCaps() {
        long epoch = capEpoch;
        energyCap = LazyOptional.of(() -> new SolarPorts.Energy(this, epoch));
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        capEpoch++;
        energyCap.invalidate();
    }
    @Override public void reviveCaps() { super.reviveCaps(); createCaps(); }
    @Override @Nonnull
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (repairRequired() || isRemoved() || (level != null && level.isClientSide)) { return LazyOptional.empty(); }
        return capability == ForgeCapabilities.ENERGY ? energyCap.cast() : super.getCapability(capability, side);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.advancedrocketrycommunity.solar_generator"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return available() && SolarGeneratorMenu.admitted(player, this)
                ? new SolarGeneratorMenu(menuType.get(), id, this) : null;
    }
}

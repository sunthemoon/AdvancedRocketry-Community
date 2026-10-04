package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Lifecycle bridge. Search/frontier and output throttling are transient; resources are schema 1. */
public final class PumpBlockEntity extends BlockEntity {
    private UUID owner;
    private int energy;
    private int cooldown;
    private FluidStack tank = FluidStack.EMPTY;
    private Tag refusedRoot;
    private boolean oversized;
    private boolean operationLock;
    private long capEpoch;
    private long outputTick = Long.MIN_VALUE;
    private int outputThisTick;
    private long lastTick = Long.MIN_VALUE;
    private PumpSearch search;
    private boolean wake = true;
    private PumpCode code = PumpCode.NO_OWNER;
    private LazyOptional<IEnergyStorage> energyCap;
    private LazyOptional<IFluidHandler> fluidCap;

    public PumpBlockEntity(BlockPos position, BlockState state) {
        super(PumpContent.ENTITY.get(), position, state);
        createCaps();
    }
    public static void serverTick(ServerLevel level, BlockPos position, BlockState state, PumpBlockEntity pump) {
        if (!pump.available() || pump.lastTick == level.getGameTime()) { return; }
        pump.lastTick = level.getGameTime();
        if (!PumpContent.enabled()) { pump.code = PumpCode.DISABLED; return; }
        if (pump.cooldown > 0) { pump.cooldown--; pump.setChanged(); }
        pump.push(level);
        if (pump.owner == null) { pump.code = PumpCode.NO_OWNER; return; }
        if (pump.energy < PumpBudget.COST) { pump.code = PumpCode.NO_ENERGY; return; }
        if (pump.tank.getAmount() > PumpBudget.TANK - PumpBudget.SOURCE) { pump.code = PumpCode.TANK_FULL; return; }
        if (pump.cooldown > 0) { pump.code = PumpCode.COOLDOWN; return; }
        if (pump.search == null) {
            if (!pump.wake) { return; }
            pump.wake = false;
            pump.search = new PumpSearch(new PumpSearch.Point(position.getX(), position.getY(), position.getZ()));
        }
        PumpSearch.Result found = pump.search.advance(point -> inspect(level, point));
        pump.code = found.code();
        if (found.candidate() != null) { pump.removeSource(level, found.candidate()); }
        else if (found.code() != PumpCode.SEARCHING) { pump.stop(found.code()); }
    }
    private static PumpSearch.Cell inspect(ServerLevel level, PumpSearch.Point point) {
        BlockPos position = new BlockPos(point.x(), point.y(), point.z());
        var chunk = level.getChunkSource().getChunkNow(point.x() >> 4, point.z() >> 4);
        if (chunk == null) { return PumpSearch.Cell.of(PumpSearch.Kind.UNLOADED); }
        if (!level.isInWorldBounds(position) || !level.getWorldBorder().isWithinBounds(position)) {
            return PumpSearch.Cell.of(PumpSearch.Kind.OUT_OF_BOUNDS);
        }
        BlockState state = chunk.getBlockState(position);
        if (state.isAir()) { return PumpSearch.Cell.of(PumpSearch.Kind.AIR); }
        if (state.getFluidState().isEmpty()) { return PumpSearch.Cell.of(PumpSearch.Kind.OTHER); }
        Fluid fluid = PumpSources.fluid(state);
        return fluid == null ? PumpSearch.Cell.of(PumpSearch.Kind.UNSUPPORTED)
                : new PumpSearch.Cell(PumpSearch.Kind.FLUID, ForgeRegistries.FLUIDS.getKey(fluid).toString(), state.getFluidState().isSource());
    }
    private void removeSource(ServerLevel level, PumpSearch.Point point) {
        BlockPos target = new BlockPos(point.x(), point.y(), point.z());
        var chunk = level.getChunkSource().getChunkNow(point.x() >> 4, point.z() >> 4);
        if (chunk == null) { stop(PumpCode.TARGET_UNLOADED); return; }
        BlockState before = chunk.getBlockState(target);
        Fluid fluid = PumpSources.fluid(before);
        if (fluid == null || !before.getFluidState().isSource()
                || !ForgeRegistries.FLUIDS.getKey(fluid).toString().equals(search.fluid())) {
            stop(PumpCode.SOURCE_CHANGED); return;
        }
        FluidStack unit = new FluidStack(fluid, PumpBudget.SOURCE);
        PumpBudget.Plan plan = PumpBudget.drain(new PumpBudget.State(energy, tank.getAmount(), cooldown), fluidMatches(unit));
        if (plan.code() != PumpCode.DRAINED) { stop(plan.code()); return; }
        FluidStack after = tank.isEmpty() ? unit : tank.copy();
        after.setAmount(plan.state().amount());
        if (!PumpSave.fits(owner, plan.state().energy(), plan.state().cooldown(), after)) { stop(PumpCode.TANK_INCOMPATIBLE); return; }
        PumpSearch activeSearch = search;
        UUID expectedOwner = owner;
        int expectedEnergy = energy;
        int expectedCooldown = cooldown;
        FluidStack expectedTank = tank.copy();
        operationLock = true;
        try {
            PumpCode protectedResult = PumpProtection.source(level, owner, target, before);
            if (protectedResult != PumpCode.SOURCE_READY) { stop(protectedResult); return; }
            protectedResult = PumpProtection.recheck(level, expectedOwner, target);
            if (protectedResult != PumpCode.SOURCE_READY) { stop(protectedResult); return; }
            // Events may unload/replace blocks. Revalidate without loading and before any resource change.
            chunk = level.getChunkSource().getChunkNow(point.x() >> 4, point.z() >> 4);
            if (chunk == null) { stop(PumpCode.TARGET_UNLOADED); return; }
            if (!live() || !java.util.Objects.equals(owner, expectedOwner) || energy != expectedEnergy
                    || cooldown != expectedCooldown || tank.getAmount() != expectedTank.getAmount()
                    || !tank.isFluidEqual(expectedTank) || !chunk.getBlockState(target).equals(before)) {
                stop(PumpCode.SOURCE_CHANGED); return;
            }
            // Suppress neighbour callbacks until source removal and both resource fields are published.
            if (!level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)) {
                stop(PumpCode.SOURCE_CHANGED); return;
            }
            energy = plan.state().energy(); tank = after; cooldown = plan.state().cooldown();
            code = PumpCode.DRAINED;
            activeSearch.drained();
            setChanged();
            scheduleFlow(level, target);
        } finally { operationLock = false; }
    }
    /** Six already-loaded liquid neighbours only; no Level block lookup or custom neighbour callback. */
    private static void scheduleFlow(ServerLevel server, BlockPos source) {
        for (Direction direction : Direction.values()) {
            BlockPos next = source.relative(direction);
            if (!server.isInWorldBounds(next)) { continue; }
            var chunk = server.getChunkSource().getChunkNow(next.getX() >> 4, next.getZ() >> 4);
            if (chunk == null) { continue; }
            var state = chunk.getBlockState(next);
            if (PumpSources.fluid(state) != null) {
                Fluid fluid = state.getFluidState().getType();
                server.scheduleTick(next, fluid, fluid.getTickDelay(server));
            }
        }
    }
    private void stop(PumpCode refusal) {
        search = null; wake = false; code = refusal;
        if ((refusal == PumpCode.TARGET_PROTECTED || refusal == PumpCode.TARGET_UNLOADED
                || refusal == PumpCode.TARGET_OUT_OF_BOUNDS || refusal == PumpCode.REPAIR_REQUIRED)
                && level instanceof ServerLevel server) {
            UUID device = UUID.nameUUIDFromBytes(("pump|" + server.dimension().location() + "|" + worldPosition.asLong())
                    .getBytes(StandardCharsets.UTF_8));
            EndgameCode auditCode = refusal == PumpCode.REPAIR_REQUIRED ? EndgameCode.ROOT_UNAVAILABLE : EndgameCode.valueOf(refusal.name());
            EndgameRuntime.service().filter(service -> service.server().orElse(null) == server.getServer()).ifPresent(service -> {
                if (service.audit().protectionNoticeDue(server.getGameTime(), device, auditCode)) {
                    service.audit().line(server.getGameTime(), "pump", "protection", refusal.name(), device, owner, null,
                            "level=" + server.dimension().location() + " x=" + worldPosition.getX()
                                    + " y=" + worldPosition.getY() + " z=" + worldPosition.getZ());
                }
            });
        }
    }
    /** Only a server-derived real placer calls this; an absent owner stays absent. */
    public void placed(@Nullable UUID placer, @Nullable Tag itemRoot) {
        if (!(level instanceof ServerLevel) || operationLock) { return; }
        if (itemRoot != null) { CompoundTag outer = new CompoundTag(); outer.put(PumpSave.ROOT, itemRoot); load(outer); }
        if (!repairRequired()) { owner = placer; retry(); setChanged(); }
    }
    public void retry() { if (!repairRequired() && !operationLock) { search = null; wake = true; } }
    public void neighbourWake() { if (!operationLock) { resourceWake(); } }
    private void resourceWake() { if (!repairRequired() && search == null) { wake = true; } }
    public UUID owner() { return owner; }
    public PumpCode status() { return repairRequired() ? PumpCode.REPAIR_REQUIRED : code; }
    public int energy() { return repairRequired() ? 0 : energy; }
    public int cooldown() { return cooldown; }
    public FluidStack fluid() { return repairRequired() ? FluidStack.EMPTY : tank.copy(); }
    public boolean repairRequired() { return refusedRoot != null; }
    boolean removalProtected() { return repairRequired() || operationLock; }
    private boolean live() {
        if (repairRequired() || isRemoved() || !(level instanceof ServerLevel server)) { return false; }
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this;
    }
    boolean available() { return !operationLock && live(); }
    boolean currentPort(long epoch) { return epoch == capEpoch && available(); }
    int receiveEnergy(int maximum, boolean simulate) {
        if (!available() || maximum <= 0) { return 0; }
        int received = Math.min(maximum, PumpBudget.ENERGY - energy);
        if (!simulate && received > 0) { energy += received; resourceWake(); setChanged(); }
        return received;
    }
    boolean fluidMatches(FluidStack stack) { return tank.isEmpty() || tank.isFluidEqual(stack); }
    boolean validFluid(FluidStack stack) {
        if (!available()) { return false; }
        operationLock = true;
        try { return validPayload(stack); }
        finally { operationLock = false; }
    }
    /** Caller input can execute virtual serialization/copy methods; every such call is inside the lock. */
    private boolean validPayload(FluidStack stack) {
        return stack != null && !stack.isEmpty() && stack.getAmount() > 0
                && (!stack.hasTag() || PumpSave.bounded(stack.getTag()))
                && PumpSave.fits(owner, energy, cooldown, amountCopy(stack, 1));
    }
    int fill(FluidStack stack, boolean execute) {
        if (!available()) { return 0; }
        operationLock = true;
        try {
            if (!validPayload(stack) || !fluidMatches(stack)) { return 0; }
            int amount = Math.min(stack.getAmount(), PumpBudget.TANK - tank.getAmount());
            if (amount <= 0) { return 0; }
            FluidStack after = amountCopy(stack, tank.getAmount() + amount);
            if (!PumpSave.fits(owner, energy, cooldown, after)) { return 0; }
            if (execute) { tank = after; resourceWake(); setChanged(); }
            return amount;
        } finally { operationLock = false; }
    }
    FluidStack drain(int maximum, boolean execute) {
        if (!available() || !PumpContent.enabled() || maximum <= 0 || tank.isEmpty()) { return FluidStack.EMPTY; }
        operationLock = true;
        try { return drainLocked(maximum, execute); }
        finally { operationLock = false; }
    }
    FluidStack drain(FluidStack matching, boolean execute) {
        if (!available() || !PumpContent.enabled() || tank.isEmpty()) { return FluidStack.EMPTY; }
        operationLock = true;
        try {
            return validPayload(matching) && fluidMatches(matching)
                    ? drainLocked(matching.getAmount(), execute) : FluidStack.EMPTY;
        } finally { operationLock = false; }
    }
    private FluidStack drainLocked(int maximum, boolean execute) {
        int amount = Math.min(maximum, Math.min(tank.getAmount(), remainingOutput()));
        if (amount <= 0) { return FluidStack.EMPTY; }
        FluidStack result = amountCopy(tank, amount);
        if (execute) { debit(amount); }
        return result;
    }
    private int remainingOutput() {
        if (level == null) { return 0; }
        if (outputTick != level.getGameTime()) { outputTick = level.getGameTime(); outputThisTick = 0; }
        return PumpBudget.OUTPUT - outputThisTick;
    }
    private void debit(int amount) {
        tank.shrink(amount); outputThisTick += amount; resourceWake(); setChanged();
    }
    private void push(ServerLevel server) {
        for (Direction direction : Direction.values()) {
            int offer = Math.min(tank.getAmount(), remainingOutput());
            if (offer <= 0) { return; }
            BlockPos neighbour = worldPosition.relative(direction);
            if (!server.isInWorldBounds(neighbour)) { continue; }
            var chunk = server.getChunkSource().getChunkNow(neighbour.getX() >> 4, neighbour.getZ() >> 4);
            BlockEntity receiver = chunk == null ? null : chunk.getBlockEntity(neighbour);
            if (receiver == null || receiver.isRemoved()) { continue; }
            operationLock = true;
            try {
                IFluidHandler port = receiver.getCapability(ForgeCapabilities.FLUID_HANDLER, direction.getOpposite()).resolve().orElse(null);
                if (port != null) {
                    int accepted = Math.max(0, Math.min(offer, port.fill(amountCopy(tank, offer), IFluidHandler.FluidAction.EXECUTE)));
                    if (accepted > 0) { debit(accepted); }
                }
            } finally { operationLock = false; }
        }
    }
    private static FluidStack amountCopy(FluidStack stack, int amount) {
        FluidStack copy = stack.copy(); copy.setAmount(amount); return copy;
    }
    @Override protected void saveAdditional(CompoundTag outer) {
        super.saveAdditional(outer);
        outer.put(PumpSave.ROOT, refusedRoot != null ? (oversized ? refusedRoot : refusedRoot.copy())
                : PumpSave.encode(owner, energy, cooldown, tank));
    }
    public CompoundTag carriedRoot() {
        if (removalProtected()) { throw new IllegalStateException("Pump resources require repair or are busy"); }
        return PumpSave.encode(owner, energy, cooldown, tank);
    }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public void load(CompoundTag outer) {
        if (operationLock) { throw new IllegalStateException("Cannot load pump resources during an external operation"); }
        super.load(outer);
        owner = null; energy = 0; cooldown = 0; tank = FluidStack.EMPTY; refusedRoot = null; oversized = false;
        search = null; wake = true; code = PumpCode.NO_OWNER;
        if (outer.contains(PumpSave.ROOT)) {
            Tag raw = outer.get(PumpSave.ROOT);
            oversized = !PumpSave.bounded(raw);
            if (oversized) { refusedRoot = raw; }
            else {
                try {
                    PumpSave.Snapshot saved = PumpSave.decode(raw);
                    owner = saved.owner(); energy = saved.energy(); cooldown = saved.cooldown(); tank = saved.fluid();
                } catch (IllegalArgumentException refused) { refusedRoot = raw.copy(); }
            }
        }
    }
    private void createCaps() {
        long epoch = capEpoch;
        energyCap = LazyOptional.of(() -> new PumpPorts.Energy(this, epoch));
        fluidCap = LazyOptional.of(() -> new PumpPorts.Fluid(this, epoch));
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); capEpoch++; energyCap.invalidate(); fluidCap.invalidate(); search = null; wake = true;
    }
    @Override public void reviveCaps() { super.reviveCaps(); createCaps(); }
    @Override public void onChunkUnloaded() { search = null; wake = true; super.onChunkUnloaded(); }
    @Override @Nonnull public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (!available()) { return LazyOptional.empty(); }
        if (capability == ForgeCapabilities.ENERGY) { return energyCap.cast(); }
        if (capability == ForgeCapabilities.FLUID_HANDLER) { return fluidCap.cast(); }
        return super.getCapability(capability, side);
    }
}

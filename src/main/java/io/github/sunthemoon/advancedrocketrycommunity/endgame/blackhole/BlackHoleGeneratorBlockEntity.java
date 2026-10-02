package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructure;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameTimings;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * ADR-057 sections 3 to 6: the black-hole generator controller. Fuel (9 slots, plain items only), the burn state
 * ({@code remaining_ticks}, {@code burn_rate}) and a 2,000,000 FE buffer live in this one block entity, so one chunk
 * save covers consumption and generation. It works only in a committed station whose live orbit body has a
 * singularity profile; it pushes at most 20,000 FE per tick to loaded neighbours and never loads a chunk.
 */
public final class BlackHoleGeneratorBlockEntity extends EndgameDeviceBlockEntity implements MenuProvider {
    public static final int ROOT_SCHEMA = 1;
    public static final String PATTERN_ID = "advancedrocketrycommunity:black_hole_generator";
    public static final int FUEL_SLOTS = 9;
    public static final int ENERGY_CAPACITY = 2_000_000;
    public static final int MAX_OUTPUT_PER_TICK = 20_000;
    private static final Set<String> STATE_KEYS = Set.of("fuel", "remaining_ticks", "burn_rate", "energy");

    private final ItemStackHandler fuel = new ItemStackHandler(FUEL_SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return plain(stack);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private final IEnergyStorage output = new Output();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.empty();
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();
    private int energy;
    private int remaining;
    private int rate;

    private final EndgameStructure structure = new EndgameStructure(PATTERN_ID, ModBlocks.BLACK_HOLE_GENERATOR.get());
    private EndgameCode status = EndgameCode.NO_FUEL;
    private boolean admitted;
    private boolean generating;
    private long outputTick = Long.MIN_VALUE;
    private int outputThisTick;
    private Optional<ResourceLocation> orbitBody = Optional.empty();
    private long summaryStart = Long.MIN_VALUE;
    private long summaryItems;
    private long summaryEnergy;

    public BlackHoleGeneratorBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.BLACK_HOLE_GENERATOR.get(), position, state);
    }

    /** Only plain items burn: no tag, so a container, a named tool or a written book is never destroyed. */
    public static boolean plain(ItemStack stack) {
        return !stack.isEmpty() && !stack.hasTag();
    }

    @Override
    public EndgameSystem system() {
        return EndgameSystem.BLACK_HOLE_GENERATOR;
    }

    @Override
    protected int rootSchema() {
        return ROOT_SCHEMA;
    }

    @Override
    protected Set<String> stateKeys() {
        return STATE_KEYS;
    }

    @Override
    protected void resetState() {
        for (int slot = 0; slot < FUEL_SLOTS; slot++) {
            fuel.setStackInSlot(slot, ItemStack.EMPTY);
        }
        energy = 0;
        remaining = 0;
        rate = 0;
    }

    @Override
    protected void readState(CompoundTag root) {
        CompoundTag inventory = EndgameNbt.requireCompound(root, "fuel");
        EndgameNbt.requireKeys(inventory, Set.of("Items", "Size"), "Black-hole fuel");
        if (EndgameNbt.requireInt(inventory, "Size") != FUEL_SLOTS) {
            throw new IllegalArgumentException("The fuel buffer has the wrong size");
        }
        ListTag items = EndgameNbt.requireList(inventory, "Items", Tag.TAG_COMPOUND, FUEL_SLOTS);
        boolean[] seen = new boolean[FUEL_SLOTS];
        for (int i = 0; i < items.size(); i++) {
            CompoundTag item = items.getCompound(i);
            int slot = EndgameNbt.requireInt(item, "Slot");
            if (slot < 0 || slot >= FUEL_SLOTS || seen[slot]) {
                throw new IllegalArgumentException("A fuel slot is invalid");
            }
            seen[slot] = true;
            ItemStack stack = ItemStack.of(item);
            if (!plain(stack) || stack.getCount() > stack.getMaxStackSize()) {
                throw new IllegalArgumentException("The fuel buffer holds an unknown or non-plain item");
            }
            fuel.setStackInSlot(slot, stack);
        }
        remaining = EndgameNbt.requireInt(root, "remaining_ticks");
        rate = EndgameNbt.requireInt(root, "burn_rate");
        energy = EndgameNbt.requireInt(root, "energy");
        if (remaining < 0 || remaining > BlackHoleFuelTable.MAX_BURN_TICKS || rate < 0
                || rate > SingularityProfile.MAX_OUTPUT * BlackHoleSettings.MAX_PERCENT / 100
                || energy < 0 || energy > ENERGY_CAPACITY) {
            throw new IllegalArgumentException("A black-hole value is outside its bounds");
        }
    }

    @Override
    protected void writeState(CompoundTag root) {
        root.put("fuel", fuel.serializeNBT());
        root.putInt("remaining_ticks", remaining);
        root.putInt("burn_rate", rate);
        root.putInt("energy", energy);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  BlackHoleGeneratorBlockEntity generator) {
        if (level instanceof ServerLevel server) {
            long start = System.nanoTime();
            try {
                generator.tick(server, state);
            } finally {
                EndgameRuntime.timings().add(EndgameTimings.Place.BLACK_HOLE_GENERATOR, System.nanoTime() - start);
            }
        }
    }

    public static void clientTick(Level level, BlockPos position, BlockState state,
                                  BlackHoleGeneratorBlockEntity generator) {
        if (generator.generating && level.random.nextInt(4) == 0) {
            BlockPos center = position.relative(state.getValue(BlackHoleGeneratorBlock.FACING).getOpposite());
            level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, center.getX() + 0.5D,
                    center.getY() + 0.5D, center.getZ() + 0.5D, level.random.nextGaussian() * 0.5D,
                    level.random.nextGaussian() * 0.5D, level.random.nextGaussian() * 0.5D);
        }
    }

    private void tick(ServerLevel level, BlockState state) {
        if (quarantined()) {
            return;
        }
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        Optional<EndgameService> service = EndgameRuntime.operational();
        if (devices.isEmpty() || service.isEmpty() || deviceId().isEmpty()) {
            setStatus(level, null, EndgameCode.ROOT_UNAVAILABLE, false);
            return;
        }
        long now = level.getGameTime();
        UUID id = deviceId().get();
        structure.tick(level, worldPosition, state.getValue(BlackHoleGeneratorBlock.FACING), id, devices.get(), now);
        summarizeIfDue(service.get(), now);
        if (!structure.known()) {
            return;
        }
        BlackHoleSettings settings = devices.get().blackHoleSettings();
        Eligibility eligibility = eligibility(level, devices.get());
        if (eligibility.code() != EndgameCode.OK) {
            release(devices.get());
            setStatus(level, service.get(), eligibility.code(), false);
            return;
        }
        if (!admitted) {
            admitted = devices.get().active().admit(EndgameSystem.BLACK_HOLE_GENERATOR, id, ownerId().orElseThrow(),
                    settings.activePerOwner(), settings.activeGlobal());
            if (!admitted) {
                setStatus(level, service.get(), EndgameCode.ACTIVE_LIMIT, false);
                return;
            }
        }
        SingularityProfile profile = eligibility.profile().orElseThrow();
        BlackHoleFuelTable table = eligibility.fuel().orElseThrow();
        int slot = firstFuelSlot();
        BlackHoleBurn.Step step = BlackHoleBurn.tick(new BlackHoleBurn.State(energy, remaining, rate), slot >= 0,
                () -> table.burnTicks(ForgeRegistries.ITEMS.getKey(fuel.getStackInSlot(slot).getItem())),
                ENERGY_CAPACITY, profile.outputFePerTick(), settings.energyPercent());
        if (step.consumed()) {
            fuel.extractItem(slot, 1, false);
            summaryItems++;
        }
        if (step.generated()) {
            summaryEnergy += step.state().rate();
        }
        boolean changed = step.state().energy() != energy || step.state().remaining() != remaining
                || step.state().rate() != rate;
        energy = step.state().energy();
        remaining = step.state().remaining();
        rate = step.state().rate();
        push(level);
        if (changed) {
            setChanged();
        }
        EndgameCode code = step.generated() ? EndgameCode.GENERATING : remaining > 0 || energy >= ENERGY_CAPACITY
                ? EndgameCode.PAUSED_FULL : EndgameCode.NO_FUEL;
        setStatus(level, service.get(), code, step.generated());
    }

    private record Eligibility(EndgameCode code, Optional<SingularityProfile> profile,
                               Optional<BlackHoleFuelTable> fuel) {
        static Eligibility refused(EndgameCode code) {
            return new Eligibility(code, Optional.empty(), Optional.empty());
        }
    }

    /** Enabled, owned, in a committed station where the owner may operate, formed, orbiting a singularity. */
    private Eligibility eligibility(ServerLevel level, EndgameDevices devices) {
        if (!devices.settings().enabled(EndgameSystem.BLACK_HOLE_GENERATOR)) {
            return Eligibility.refused(EndgameCode.SYSTEM_DISABLED);
        }
        if (ownerId().isEmpty()) {
            return Eligibility.refused(EndgameCode.UNOWNED);
        }
        EndgameStations.At at = EndgameStations.at(level, worldPosition);
        if (at.station().isEmpty() || !EndgameAuthority.decide(new EndgameAuthority.Request(ownerId().get(), false,
                ownerId(), at.context(), EndgameAction.OPERATE, false)).allowed()) {
            orbitBody = Optional.empty();
            return Eligibility.refused(EndgameCode.STATION_UNAVAILABLE);
        }
        if (structure.code() != EndgameCode.OK) {
            return Eligibility.refused(structure.code());
        }
        ResourceLocation body = at.station().get().orbitBody();
        orbitBody = Optional.of(body);
        Optional<SingularityProfile> profile = devices.celestial()
                .flatMap(catalog -> devices.blackHoleData().at(body, catalog));
        Optional<BlackHoleFuelTable> table = profile.flatMap(found -> devices.blackHoleData().fuelTable(
                found.fuelTable()));
        if (profile.isEmpty() || table.isEmpty()) {
            return Eligibility.refused(EndgameCode.NO_SINGULARITY);
        }
        return new Eligibility(EndgameCode.OK, profile, table);
    }

    private int firstFuelSlot() {
        for (int slot = 0; slot < FUEL_SLOTS; slot++) {
            if (plain(fuel.getStackInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** Step 3: up to 20,000 FE per tick to adjacent receivers in a fixed face order, loaded neighbours only. */
    private void push(ServerLevel level) {
        for (Direction direction : Direction.values()) {
            int budget = Math.min(energy, MAX_OUTPUT_PER_TICK - usedOutput(level.getGameTime()));
            if (budget <= 0) {
                return;
            }
            BlockPos neighbour = worldPosition.relative(direction);
            LevelChunk chunk = level.getChunkSource().getChunkNow(neighbour.getX() >> 4, neighbour.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            BlockEntity target = chunk.getBlockEntity(neighbour);
            if (target == null || target instanceof BlackHoleGeneratorBlockEntity) {
                continue;
            }
            int accepted = target.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite())
                    .map(storage -> storage.receiveEnergy(budget, false)).orElse(0);
            if (accepted > 0) {
                take(level.getGameTime(), Math.min(accepted, budget));
            }
        }
    }

    private int usedOutput(long now) {
        return outputTick == now ? outputThisTick : 0;
    }

    private void take(long now, int amount) {
        outputThisTick = usedOutput(now) + amount;
        outputTick = now;
        energy -= amount;
        setChanged();
    }

    private void setStatus(ServerLevel level, @Nullable EndgameService service, EndgameCode code, boolean active) {
        if (active != generating) {
            generating = active;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (code == status) {
            return;
        }
        EndgameCode previous = status;
        status = code;
        if (service != null) {
            service.audit().line(level.getGameTime(), system().id(), "status", code.name(), deviceId().orElse(null),
                    ownerId().orElse(null), null, "previous=" + previous.name());
        }
    }

    /** One summary per generator every 1,200 ticks: items consumed and energy produced. */
    private void summarizeIfDue(EndgameService service, long now) {
        if (summaryStart == Long.MIN_VALUE) {
            summaryStart = now;
            return;
        }
        if (now - summaryStart < EndgameLimits.AUDIT_SUMMARY_INTERVAL_TICKS) {
            return;
        }
        if (summaryItems > 0 || summaryEnergy > 0) {
            service.audit().line(now, system().id(), "summary", "OK", deviceId().orElse(null), ownerId().orElse(null),
                    null, "items=" + summaryItems + " energy=" + summaryEnergy);
        }
        summaryStart = now;
        summaryItems = 0;
        summaryEnergy = 0;
    }

    private void release(EndgameDevices devices) {
        if (admitted) {
            admitted = false;
            deviceId().ifPresent(id -> devices.active().release(EndgameSystem.BLACK_HOLE_GENERATOR, id));
        }
    }

    private void unloadRuntime() {
        EndgameRuntime.devices().ifPresent(devices -> {
            release(devices);
            structure.untrack(devices);
        });
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide) {
            unloadRuntime();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            unloadRuntime();
        }
    }

    @Override
    protected String describeState() {
        return "status=" + status.name() + " structure=" + structure.code().name() + " energy=" + energy
                + " remaining=" + remaining + " rate=" + rate + " admitted=" + admitted;
    }

    public EndgameCode status() {
        return status;
    }

    public EndgameCode structureCode() {
        return structure.code();
    }

    public int energy() {
        return energy;
    }

    public int remaining() {
        return remaining;
    }

    public int rate() {
        return rate;
    }

    public Optional<ResourceLocation> orbitBody() {
        return orbitBody;
    }

    public ItemStackHandler fuel() {
        return fuel;
    }

    /** Test seam: no burn in progress, so the next step decides whether to take an item. */
    public void endBurnForTest() {
        remaining = 0;
        rate = 0;
        setChanged();
    }

    /** Test seam: energy within the buffer. */
    public void setEnergyForTest(int value) {
        if (value < 0 || value > ENERGY_CAPACITY) {
            throw new IllegalArgumentException("Energy is outside the buffer");
        }
        energy = value;
        setChanged();
    }

    public void dropFuel(Level world, BlockPos position) {
        for (int slot = 0; slot < FUEL_SLOTS; slot++) {
            net.minecraft.world.Containers.dropItemStack(world, position.getX(), position.getY(), position.getZ(),
                    fuel.getStackInSlot(slot));
            fuel.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    // ---- Render state: generating only (ADR-057 section 6) ---------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("generating", generating);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        generating = tag.getBoolean("generating");
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean generatingForRender() {
        return generating;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4.0D);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.black_hole_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BlackHoleGeneratorMenu(id, inventory, this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (!quarantined()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER) {
                return itemCapability.cast();
            }
            if (capability == ForgeCapabilities.ENERGY) {
                return energyCapability.cast();
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        itemCapability = LazyOptional.of(() -> fuel);
        energyCapability = LazyOptional.of(() -> output);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        energyCapability.invalidate();
    }

    /**
     * Generators expose no {@code receiveEnergy} (ADR-054 section 8). Pulling shares the 20,000 FE per tick output
     * with the push, on the server thread only.
     */
    private final class Output implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maximum, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maximum, boolean simulate) {
            if (maximum <= 0 || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()) {
                return 0;
            }
            int amount = Math.min(maximum, Math.min(energy, MAX_OUTPUT_PER_TICK - usedOutput(server.getGameTime())));
            if (amount > 0 && !simulate) {
                take(server.getGameTime(), amount);
            }
            return Math.max(0, amount);
        }

        @Override
        public int getEnergyStored() {
            return energy;
        }

        @Override
        public int getMaxEnergyStored() {
            return ENERGY_CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return false;
        }
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameEnergyBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameRedstoneMode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.EndgameProtection;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ForgeProtectionView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * ADR-058 sections 2 to 6: the area gravity field controller. It holds {@code running}, the redstone mode, radius
 * {@code r}, multiplier {@code m} and a 50,000 FE buffer (input at most 1,000 FE per tick). A field is active while
 * every condition of section 3 holds, re-checked on settings changes, activation and every 200 ticks; it pays
 * {@code 5 + 2r} FE every tick and drops out of the index in the tick a condition fails.
 */
public final class GravityFieldBlockEntity extends EndgameDeviceBlockEntity implements MenuProvider {
    public static final int ROOT_SCHEMA = 1;
    public static final int ENERGY_CAPACITY = 50_000;
    public static final int MAX_INPUT_PER_TICK = 1_000;
    private static final int MAX_PARTICLES_PER_TICK = 4;
    private static final Set<String> STATE_KEYS = Set.of("running", "redstone", "radius", "multiplier", "energy");

    private final EndgameEnergyBuffer energy = new EndgameEnergyBuffer(ENERGY_CAPACITY, MAX_INPUT_PER_TICK,
            this::setChanged, this::getLevel);
    private LazyOptional<IEnergyStorage> energyCapability = LazyOptional.empty();
    private boolean running;
    private EndgameRedstoneMode redstone = EndgameRedstoneMode.IGNORED;
    private int radius = GravityField.DEFAULT_RADIUS;
    private int multiplier = GravityField.DEFAULT_MULTIPLIER;

    private boolean active;
    private EndgameCode status = EndgameCode.STOPPED;
    private long nextCheck;
    @Nullable
    private GravityField field;

    public GravityFieldBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.GRAVITY_FIELD_CONTROLLER.get(), position, state);
    }

    @Override
    public EndgameSystem system() {
        return EndgameSystem.GRAVITY_FIELD;
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
        running = false;
        redstone = EndgameRedstoneMode.IGNORED;
        radius = GravityField.DEFAULT_RADIUS;
        multiplier = GravityField.DEFAULT_MULTIPLIER;
        energy.set(0);
    }

    @Override
    protected void readState(CompoundTag root) {
        running = EndgameNbt.requireBoolean(root, "running");
        redstone = EndgameRedstoneMode.byName(EndgameNbt.requireString(root, "redstone", 16));
        radius = EndgameNbt.requireInt(root, "radius");
        GravityField.requireRadius(radius);
        multiplier = EndgameNbt.requireInt(root, "multiplier");
        if (multiplier < GravityField.MIN_MULTIPLIER || multiplier > GravityField.MAX_MULTIPLIER
                || multiplier % GravityField.MULTIPLIER_STEP != 0) {
            throw new IllegalArgumentException("The field multiplier is outside its range");
        }
        energy.read(root, "energy");
    }

    @Override
    protected void writeState(CompoundTag root) {
        root.putBoolean("running", running);
        root.putString("redstone", redstone.name());
        root.putInt("radius", radius);
        root.putInt("multiplier", multiplier);
        energy.write(root, "energy");
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, GravityFieldBlockEntity device) {
        if (level instanceof ServerLevel server) {
            device.tick(server);
        }
    }

    public static void clientTick(Level level, BlockPos position, BlockState state, GravityFieldBlockEntity device) {
        if (!device.active) {
            return;
        }
        Player nearest = level.getNearestPlayer(position.getX() + 0.5D, position.getY() + 0.5D,
                position.getZ() + 0.5D, device.radius + 1.0D, false);
        if (nearest == null || Math.abs(nearest.getBlockX() - position.getX()) > device.radius
                || Math.abs(nearest.getBlockY() - position.getY()) > device.radius
                || Math.abs(nearest.getBlockZ() - position.getZ()) > device.radius) {
            return;
        }
        // Subtle: at most 4 per tick; the client's particle setting drops them at minimal.
        int count = level.random.nextInt(MAX_PARTICLES_PER_TICK + 1);
        for (int i = 0; i < count; i++) {
            level.addParticle(ParticleTypes.END_ROD, nearest.getX() + level.random.nextGaussian() * 1.5D,
                    nearest.getY() + level.random.nextDouble() * 2.0D,
                    nearest.getZ() + level.random.nextGaussian() * 1.5D, 0.0D, device.multiplier < 100 ? 0.02D
                            : -0.02D, 0.0D);
        }
    }

    private void tick(ServerLevel level) {
        if (quarantined()) {
            return;
        }
        Optional<EndgameDevices> devices = EndgameRuntime.devices();
        Optional<EndgameService> service = EndgameRuntime.operational();
        if (devices.isEmpty() || service.isEmpty() || deviceId().isEmpty()) {
            deactivate(level, null, null, EndgameCode.ROOT_UNAVAILABLE);
            return;
        }
        long now = level.getGameTime();
        if (active && !devices.get().fields().contains(deviceId().get())) {
            // The index was cleared (the switch turned off, or the Level unloaded).
            deactivate(level, devices.get(), service.get(), EndgameCode.SYSTEM_DISABLED);
            nextCheck = 0;
        }
        if (now >= nextCheck) {
            nextCheck = now + EndgameLimits.STRUCTURE_REVALIDATION_TICKS;
            EndgameCode code = check(level, devices.get(), service.get());
            if (code == EndgameCode.OK) {
                activate(level, devices.get(), service.get());
            } else {
                deactivate(level, devices.get(), service.get(), code);
            }
        }
        if (active && !energy.spend(GravityField.upkeep(radius))) {
            deactivate(level, devices.get(), service.get(), EndgameCode.INSUFFICIENT_ENERGY);
        }
    }

    /** Section 3 conditions 1 to 4 in order; condition 5 (the index) is the activation itself. */
    private EndgameCode check(ServerLevel level, EndgameDevices devices, EndgameService service) {
        if (!devices.settings().enabled(EndgameSystem.GRAVITY_FIELD)) {
            return EndgameCode.SYSTEM_DISABLED;
        }
        if (ownerId().isEmpty()) {
            return EndgameCode.UNOWNED;
        }
        if (!running) {
            return EndgameCode.STOPPED;
        }
        if (!redstone.satisfied(level.hasNeighborSignal(worldPosition))) {
            return EndgameCode.REDSTONE_BLOCKED;
        }
        EndgameStations.At at = EndgameStations.at(level, worldPosition);
        if (at.context().kind() == EndgameAuthority.StationKind.UNAVAILABLE) {
            return EndgameCode.STATION_UNAVAILABLE;
        }
        Optional<GravityField.Clip> clip = Optional.empty();
        if (at.station().isPresent()) {
            if (!at.station().get().ownerId().equals(ownerId().get())) {
                return EndgameCode.STATION_OWNER_REQUIRED;
            }
            StationRegion region = at.station().get().region();
            clip = Optional.of(new GravityField.Clip(region.minimumX(), region.minimumZ(), region.maximumX(),
                    region.maximumZ()));
        }
        Optional<GravityField.Box> box = GravityField.Box.of(worldPosition.getX(), worldPosition.getY(),
                worldPosition.getZ(), radius, clip);
        if (box.isEmpty()) {
            return EndgameCode.FIELD_OUTSIDE_STATION;
        }
        // Chain steps 2 to 6: no chunk is modified, so step 1 does not apply.
        EndgameCode chain = EndgameProtection.check(new EndgameProtection.Batch(EndgameSystem.GRAVITY_FIELD,
                EndgameEffect.ENTITY_GRAVITY, ownerId().get(), Optional.empty(), level.dimension(),
                new BlockPos(box.get().minX(), box.get().minY(), box.get().minZ()),
                new BlockPos(box.get().maxX(), box.get().maxY(), box.get().maxZ()), false),
                new ForgeProtectionView(level, service.root().orElseThrow().zones()));
        if (chain != EndgameCode.OK) {
            return chain;
        }
        if (energy.energy() < GravityField.upkeep(radius)) {
            return EndgameCode.INSUFFICIENT_ENERGY;
        }
        field = new GravityField(deviceId().orElseThrow(), ownerId().get(), level.dimension().location(), box.get(),
                multiplier, at.station().isPresent());
        return EndgameCode.OK;
    }

    private void activate(ServerLevel level, EndgameDevices devices, EndgameService service) {
        EndgameCode admitted = devices.fields().add(field, devices.gravityLimits());
        if (admitted != EndgameCode.OK) {
            deactivate(level, devices, service, admitted);
            return;
        }
        boolean was = active;
        active = true;
        status = EndgameCode.OK;
        if (!was) {
            audit(service, level, "activate", EndgameCode.OK, null, "r=" + radius + " m=" + multiplier
                    + " box=" + field.box().volume());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void deactivate(ServerLevel level, @Nullable EndgameDevices devices, @Nullable EndgameService service,
                            EndgameCode code) {
        if (devices != null) {
            deviceId().ifPresent(devices.fields()::remove);
        }
        boolean was = active;
        EndgameCode previous = status;
        active = false;
        status = code;
        if (service != null && (was || previous != code)) {
            audit(service, level, "deactivate", code, null, "was_active=" + was);
        }
        if (was) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void audit(EndgameService service, ServerLevel level, String action, EndgameCode code,
                       @Nullable UUID actor, String fields) {
        service.audit().line(level.getGameTime(), system().id(), action, code.name(), deviceId().orElse(null),
                ownerId().orElse(null), actor, fields);
    }

    // ---- Button effects (after the ADR-054 section 4 guard; inside stations it requires MANAGE_STATION) -------

    EndgameCode running(boolean next, UUID actor) {
        if (running == next) {
            return EndgameCode.OK;
        }
        running = next;
        return changed(actor, next ? "start" : "stop", "");
    }

    EndgameCode cycleRedstone(UUID actor) {
        redstone = redstone.next();
        return changed(actor, "redstone", "mode=" + redstone.name());
    }

    EndgameCode radius(int delta, UUID actor) {
        int next = Math.max(GravityField.MIN_RADIUS, Math.min(GravityField.MAX_RADIUS, radius + delta));
        String fields = "radius=" + radius + "->" + next;
        radius = next;
        return changed(actor, "settings", fields);
    }

    EndgameCode multiplier(int delta, UUID actor) {
        int next = Math.max(GravityField.MIN_MULTIPLIER, Math.min(GravityField.MAX_MULTIPLIER, multiplier + delta));
        String fields = "multiplier=" + multiplier + "->" + next;
        multiplier = next;
        return changed(actor, "settings", fields);
    }

    /** A settings change is audited and re-checked at once, applying immediately with no ramp. */
    private EndgameCode changed(UUID actor, String action, String fields) {
        nextCheck = 0;
        setChanged();
        if (level instanceof ServerLevel server) {
            EndgameRuntime.operational().ifPresent(service -> audit(service, server, action, EndgameCode.OK, actor,
                    fields));
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return EndgameCode.OK;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        leaveIndex();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        leaveIndex();
    }

    private void leaveIndex() {
        if (level != null && !level.isClientSide) {
            deviceId().ifPresent(id -> EndgameRuntime.devices().ifPresent(devices -> devices.fields().remove(id)));
            active = false;
        }
    }

    @Override
    protected String describeState() {
        return "status=" + status.name() + " active=" + active + " running=" + running + " redstone="
                + redstone.name() + " radius=" + radius + " multiplier=" + multiplier + " energy=" + energy.energy();
    }

    public EndgameCode status() {
        return status;
    }

    public boolean active() {
        return active;
    }

    public boolean running() {
        return running;
    }

    public EndgameRedstoneMode redstoneMode() {
        return redstone;
    }

    public int radius() {
        return radius;
    }

    public int multiplier() {
        return multiplier;
    }

    /** The current clipped box while active. */
    public Optional<GravityField.Box> activeBox() {
        return active && field != null ? Optional.of(field.box()) : Optional.empty();
    }

    public EndgameEnergyBuffer energy() {
        return energy;
    }

    // ---- Render state: active, r and m only (ADR-058 section 6) ----------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("active", active);
        tag.putInt("r", radius);
        tag.putInt("m", multiplier);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        active = tag.getBoolean("active");
        radius = Math.max(GravityField.MIN_RADIUS, Math.min(GravityField.MAX_RADIUS, tag.getInt("r")));
        multiplier = tag.getInt("m");
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.gravity_field_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new GravityFieldMenu(id, inventory, this);
    }

    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (!quarantined() && capability == ForgeCapabilities.ENERGY) {
            return energyCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        energyCapability = LazyOptional.of(() -> energy);
        nextCheck = 0;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCapability.invalidate();
    }
}

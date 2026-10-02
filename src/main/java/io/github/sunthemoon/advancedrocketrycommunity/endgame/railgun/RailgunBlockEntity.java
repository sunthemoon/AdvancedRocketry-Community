package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameTimings;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo.CargoEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructure;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.RoundRobinBudget;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ADR-056 section 2: the railgun controller, a {@code railgun} cargo endpoint ({@link CargoEndpointBlockEntity}) with
 * a 1,000,000 FE buffer taking at most 50,000 FE per tick. Its own settings are the selected destination (with the
 * operator-selection flag) and the minimum stack size; it launches through {@link RailgunLauncher} once its 3 × 6 × 3
 * structure is formed, and shows a launch flash and streak and an arrival flash as block events.
 */
public final class RailgunBlockEntity extends CargoEndpointBlockEntity implements MenuProvider {
    public static final ResourceLocation KIND = ModIdentity.id("railgun");
    public static final int ROOT_SCHEMA = 1;
    public static final String PATTERN_ID = "advancedrocketrycommunity:railgun";
    public static final int ENERGY_CAPACITY = 1_000_000;
    public static final int MAX_INPUT_PER_TICK = 50_000;
    public static final int EVENT_LAUNCH = 1;
    public static final int EVENT_ARRIVAL = 2;
    public static final int MUZZLE_HEIGHT = 5;
    private static final int EFFECT_TICKS = 12;

    private final EndgameStructure structure = new EndgameStructure(PATTERN_ID, ModBlocks.RAILGUN.get());
    @Nullable
    private UUID target;
    private boolean operatorTarget;
    private int minStack = RailgunLaunch.MIN_STACK;
    private int effect;
    private long effectUntil;

    public RailgunBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.RAILGUN.get(), position, state, ENERGY_CAPACITY, MAX_INPUT_PER_TICK,
                Set.of("destination", "operator_target", "min_stack"));
    }

    @Override
    public EndgameSystem system() {
        return EndgameSystem.RAILGUN;
    }

    @Override
    public ResourceLocation kind() {
        return KIND;
    }

    @Override
    protected int rootSchema() {
        return ROOT_SCHEMA;
    }

    @Override
    protected void resetSettings() {
        target = null;
        operatorTarget = false;
        minStack = RailgunLaunch.MIN_STACK;
    }

    @Override
    protected void readSettings(CompoundTag root) {
        target = root.contains("destination") ? EndgameNbt.requireUuid(root, "destination") : null;
        operatorTarget = EndgameNbt.requireBoolean(root, "operator_target");
        if (operatorTarget && target == null) {
            throw new IllegalArgumentException("An operator selection without a destination");
        }
        minStack = EndgameNbt.requireInt(root, "min_stack");
        if (minStack < RailgunLaunch.MIN_STACK || minStack > RailgunLaunch.MAX_STACK) {
            throw new IllegalArgumentException("The minimum stack size is outside 1..64");
        }
    }

    @Override
    protected void writeSettings(CompoundTag root) {
        if (target != null) {
            root.put("destination", NbtUtils.createUUID(target));
        }
        root.putBoolean("operator_target", operatorTarget);
        root.putInt("min_stack", minStack);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, RailgunBlockEntity railgun) {
        if (level instanceof ServerLevel server) {
            long start = System.nanoTime();
            try {
                railgun.tickServer(server, state);
            } finally {
                EndgameRuntime.timings().add(EndgameTimings.Place.RAILGUN, System.nanoTime() - start);
            }
        }
    }

    @Override
    protected boolean ready(ServerLevel level, BlockState state, UUID id, EndgameDevices devices, long now) {
        structure.tick(level, worldPosition, state.getValue(RailgunBlock.FACING), id, devices, now);
        return structure.known();
    }

    /** An automatic launch asks for a slot of the server's cap only with a payload and a destination. */
    @Override
    protected boolean autoReady() {
        return target != null && RailgunLaunch.payloadSlot(storage().inputStacks(), minStack) >= 0;
    }

    @Override
    protected RoundRobinBudget launchBudget(EndgameDevices devices) {
        return devices.railgunLaunches();
    }

    @Override
    public EndgameCode launch(ServerLevel level, EndgameService service, EndgameDevices devices, @Nullable UUID actor,
                              boolean escrow) {
        return RailgunLauncher.launch(this, level, service, devices, actor, escrow);
    }

    // ---- Settings (RailgunLauncher, the menu and GameTests) ---------------------------------------------------

    public Optional<UUID> target() {
        return Optional.ofNullable(target);
    }

    /** An operator's selection of another owner's railgun passes the route rule's owner check (section 3). */
    public boolean operatorTarget() {
        return operatorTarget;
    }

    void target(@Nullable UUID value, boolean operator) {
        target = value;
        operatorTarget = value != null && operator;
        setChanged();
    }

    public int minStack() {
        return minStack;
    }

    void minStack(int value) {
        minStack = value;
        setChanged();
    }

    /** GameTests: the menu's selection, after the intent guard a player would pass. */
    public void selectForTest(@Nullable UUID destination, boolean operator) {
        target(destination, operator);
    }

    void escrowed(long now) {
        launched(now);
    }

    public EndgameCode structureCode() {
        return structure.code();
    }

    /** An operator gave the railgun to another owner: the old owner's destination is no longer selected. */
    @Override
    protected void ownerChanged(UUID previous) {
        target = null;
        operatorTarget = false;
        super.ownerChanged(previous);
    }

    @Override
    protected String describeState() {
        return describeCargo() + " structure=" + structure.code().name() + " destination="
                + (target == null ? "-" : target) + " min_stack=" + minStack;
    }

    @Override
    protected void unloadDevice() {
        EndgameRuntime.devices().ifPresent(structure::untrack);
    }

    // ---- Visuals (ADR-056 section 6): block events, at most 8 concurrent effects per client --------------------

    @Override
    protected void launchedEffect() {
        if (level != null) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_LAUNCH, 0);
        }
    }

    @Override
    protected void claimed() {
        if (level != null) {
            level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_ARRIVAL, 0);
        }
    }

    @Override
    public boolean triggerEvent(int id, int parameter) {
        if (level != null && level.isClientSide && (id == EVENT_LAUNCH || id == EVENT_ARRIVAL)
                && RailgunEffects.start(level, level.getGameTime(), EFFECT_TICKS)) {
            effect = id;
            effectUntil = level.getGameTime() + EFFECT_TICKS;
            level.addParticle(ParticleTypes.FLASH, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.5D,
                    worldPosition.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
            return true;
        }
        return super.triggerEvent(id, parameter);
    }

    public static void clientTick(Level level, BlockPos position, BlockState state, RailgunBlockEntity railgun) {
        if (railgun.effectUntil > level.getGameTime()) {
            RailgunEffects.tick(level, position, state, railgun.effect);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.advancedrocketrycommunity.railgun");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RailgunMenu(id, inventory, this);
    }
}

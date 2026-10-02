package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameTimings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo.CargoEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.RoundRobinBudget;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ADR-059 section 2: an elevator endpoint, the anchor or the terminal. Both are cargo endpoints of the ledger with a
 * 500,000 FE buffer taking at most 20,000 FE per tick; their destination is always the other end of their pair, and
 * their platform is the 3 × 3 area directly on top of the endpoint block. Every 20 ticks the pair's validity is
 * re-derived from live state for the menu and the tether's render flag; it is never trusted for an action, which
 * re-derives it again.
 */
public abstract class ElevatorEndpointBlockEntity extends CargoEndpointBlockEntity implements MenuProvider {
    public static final int ROOT_SCHEMA = 1;
    public static final int ENERGY_CAPACITY = 500_000;
    public static final int MAX_INPUT_PER_TICK = 20_000;
    private static final int PAIR_CHECK_TICKS = 20;

    private ElevatorRules.Check pairCheck = ElevatorRules.Check.of(EndgameCode.NOT_BOUND);
    private long nextPairCheck;
    private boolean tether;

    protected ElevatorEndpointBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state, ENERGY_CAPACITY, MAX_INPUT_PER_TICK, Set.of());
    }

    /** The endpoint's own placement state ({@code OK}, or why it cannot be used: structure, region, landing pad). */
    public abstract EndgameCode placement();

    /** Updates the placement each tick; false while it is not known yet. */
    protected abstract boolean placementReady(ServerLevel level, BlockState state, UUID id, EndgameDevices devices,
                                              long now);

    public abstract boolean anchor();

    @Override
    public EndgameSystem system() {
        return EndgameSystem.SPACE_ELEVATOR;
    }

    @Override
    protected int rootSchema() {
        return ROOT_SCHEMA;
    }

    @Override
    protected void resetSettings() {
    }

    @Override
    protected void readSettings(CompoundTag root) {
    }

    @Override
    protected void writeSettings(CompoundTag root) {
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
                                  ElevatorEndpointBlockEntity endpoint) {
        if (level instanceof ServerLevel server) {
            long start = System.nanoTime();
            try {
                endpoint.tickServer(server, state);
            } finally {
                EndgameRuntime.timings().add(EndgameTimings.Place.SPACE_ELEVATOR, System.nanoTime() - start);
            }
        }
    }

    @Override
    protected boolean ready(ServerLevel level, BlockState state, UUID id, EndgameDevices devices, long now) {
        boolean known = placementReady(level, state, id, devices, now);
        if (now >= nextPairCheck) {
            nextPairCheck = now + PAIR_CHECK_TICKS;
            refreshPair(level, devices);
        }
        return known;
    }

    private void refreshPair(ServerLevel level, EndgameDevices devices) {
        Optional<EndgameRoot> root = EndgameRuntime.operational().flatMap(EndgameService::root);
        Optional<ElevatorPair> pair = root.flatMap(found -> deviceId().flatMap(found.pairs()::forEndpoint));
        pairCheck = pair.isEmpty() ? ElevatorRules.Check.of(EndgameCode.NOT_BOUND)
                : ElevatorPairs.validity(level.getServer(), root.get(), devices, pair.get());
        boolean valid = pairCheck.ok();
        if (valid != tether) {
            tether = valid;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The pair state the last check found, for the menu. */
    public ElevatorRules.Check pairCheck() {
        return pairCheck;
    }

    @Override
    protected boolean autoReady() {
        return pairCheck.ok() && storage().inputStacks().stream().anyMatch(stack -> !stack.isEmpty());
    }

    @Override
    protected RoundRobinBudget launchBudget(EndgameDevices devices) {
        return devices.elevatorLaunches();
    }

    @Override
    public EndgameCode launch(ServerLevel level, EndgameService service, EndgameDevices devices, @Nullable UUID actor,
                              boolean escrow) {
        return ElevatorCargo.launch(this, level, service, devices, actor, escrow).code();
    }

    void escrowed(long now) {
        launched(now);
    }

    /** Pairs pin an endpoint too: a non-operator cannot break one that a pair names (ADR-054 section 9). */
    public boolean busy(EndgameRoot root) {
        return busy() || deviceId().filter(root.pairs()::names).isPresent();
    }

    // ---- Platform (section 2) ---------------------------------------------------------------------------------

    /** Whether the entity's feet stand in the 3 × 3 area directly on top of the endpoint block. */
    public boolean onPlatform(Entity entity) {
        BlockPos feet = entity.blockPosition();
        return entity.level() == level && feet.getY() == worldPosition.getY() + 1
                && Math.abs(feet.getX() - worldPosition.getX()) <= 1 && Math.abs(feet.getZ() - worldPosition.getZ()) <= 1;
    }

    /** The arrival position at this endpoint: the centre of its platform. */
    public static Vec3 arrival(BlockPos endpoint) {
        return new Vec3(endpoint.getX() + 0.5D, endpoint.getY() + 1.0D, endpoint.getZ() + 0.5D);
    }

    /** The two blocks above the platform centre have no collision shape. */
    public static boolean platformClear(Level level, BlockPos endpoint) {
        for (int dy = 1; dy <= 2; dy++) {
            BlockPos above = endpoint.above(dy);
            if (!level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected String describeState() {
        return describeCargo() + " placement=" + placement().name() + " pair=" + pairCheck.describe();
    }

    // ---- Render state: the tether flag only (section 9) --------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("tether", tether);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        tether = tag.getBoolean("tether");
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean tetherForRender() {
        return tether;
    }

    @Override
    public AABB getRenderBoundingBox() {
        return anchor() ? new AABB(worldPosition).expandTowards(0.0D, ElevatorRules.MAX_TETHER_BLOCKS, 0.0D)
                : new AABB(worldPosition).expandTowards(0.0D, -ElevatorRules.MAX_TETHER_BLOCKS, 0.0D);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ElevatorMenu(id, inventory, this);
    }
}

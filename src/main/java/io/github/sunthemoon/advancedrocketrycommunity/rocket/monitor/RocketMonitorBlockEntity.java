package io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;

/** Metadata/lifecycle adapter. The shared flight service owns all fuel, passenger, landing and escrow mutations. */
public final class RocketMonitorBlockEntity extends BlockEntity {
    private RocketMonitorRoot root = new RocketMonitorRoot(null, UUID.randomUUID(), null, 0, false, false, null, "");
    private Tag preserved;
    private RocketFlightRequestCode lastFlight;
    private long lastCost;
    private RocketMonitorStatus status = RocketMonitorStatus.UNBOUND;
    private boolean submitting;
    private double lastHeight;
    private long sampledTick = -1;
    private int height, velocity, comparator;

    public RocketMonitorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.ROCKET_MONITORING_STATION.get(), pos, state); }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    public RocketMonitorRoot root() { return preserved == null ? root : null; }
    public RocketMonitorStatus status() { return preserved == null ? status : RocketMonitorStatus.UNSUPPORTED_DATA; }
    public RocketFlightRequestCode lastFlight() { return lastFlight; }
    public long lastCost() { return lastCost; }
    public int height() { return height; }
    public int velocity() { return velocity; }
    public int comparator() { return comparator; }
    public boolean live() {
        if (isRemoved() || !(level instanceof ServerLevel server) || !server.getServer().isSameThread()
                || !RocketMonitorBinding.loaded(server, worldPosition)) { return false; }
        return server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4)
                .getBlockEntity(worldPosition) == this;
    }
    public boolean admitted(Player player) {
        return live() && root() != null && root.owner() != null && player instanceof ServerPlayer server
                && !(player instanceof FakePlayer) && player.isAlive() && !player.isSpectator()
                && server.connection != null && server.connection.connection.isConnected() && !server.hasDisconnected() && player.level() == level
                && (root.owner().equals(player.getUUID()) || player.hasPermissions(2))
                && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
    }
    public void claim(Player player) {
        if (live() && root() != null && root.owner() == null && player instanceof ServerPlayer server && !(player instanceof FakePlayer)
                && server.connection != null && server.connection.connection.isConnected() && player.isAlive() && !player.isSpectator()) {
            replace(new RocketMonitorRoot(player.getUUID(), root.device(), null, 0, level.hasNeighborSignal(worldPosition), false, null, ""));
        }
    }
    public boolean bind(LoaderBinding binding) {
        if (!live() || root() == null || submitting) { return false; }
        replace(root.bind(binding)); lastFlight = null; lastCost = 0; return true;
    }
    public void disarm() { if (live() && root() != null && !submitting) { replace(root.disarm()); } }
    public boolean configure(RocketMonitorQuote quote, boolean arm, int mode) {
        if (!live() || root() == null || submitting || quote == null || quote.authority().isEmpty() || arm && !quote.ready()) { return false; }
        var service = RocketRuntime.monitorFlights().orElse(null);
        if (service == null || !quote.same(service.quote(this, quote.target()))
                || service.targets(this).stream().noneMatch(target -> target.target().equals(quote.target()))) { return false; }
        replace(root.configure(mode, arm, quote.target(), quote.authority())); return true;
    }
    public RocketFlightRequestResult launch(RocketMonitorQuote quote, ServerPlayer initiator) {
        if (!live() || root() == null || submitting) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.INVALID_STATE); }
        submitting = true;
        try {
            var result = RocketRuntime.monitorFlights().map(service -> service.launch(this, quote, initiator))
                    .orElseGet(() -> RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE));
            lastFlight = result.code(); lastCost = result.requiredFuel();
            if (result.success() || result.code() == RocketFlightRequestCode.JOURNAL_PENDING) { status = RocketMonitorStatus.REQUESTED; }
            return result;
        } finally { submitting = false; }
    }
    private void replace(RocketMonitorRoot next) { root = next; setChanged(); }

    public void signal(boolean signal) {
        if (!live() || root() == null || submitting || signal == root.powered()) { return; }
        boolean rising = RocketMonitorRules.rising(root.powered(), signal);
        replace(root.signal(signal)); // Publish the edge before any launch callback can reenter.
        if (!rising || !root.armed()) { return; }
        if (RocketMonitorBinding.resolve(this, false).status() == RocketMonitorStatus.WAITING_UNLOADED) {
            lastFlight = RocketFlightRequestCode.ENTITY_UNAVAILABLE; lastCost = 0; return;
        }
        var service = RocketRuntime.monitorFlights().orElse(null);
        if (service == null || !root.authority().equals(service.authority(this, root.destination()))) {
            disarm(); return;
        }
        launch(service.quote(this, root.destination()), null);
    }

    public void tick() {
        if (!live()) { return; }
        var resolved = RocketMonitorBinding.resolve(this, false);
        status = resolved.status();
        long now = level.getGameTime();
        int output = 0;
        if (resolved.rocket() != null) {
            var rocket = resolved.rocket();
            double relative = rocket.getY() - root.binding().locator().getY();
            height = RocketMonitorRules.milli(relative, 4096000);
            velocity = sampledTick >= 0 && now > sampledTick
                    ? RocketMonitorRules.milli((relative - lastHeight) / (now - sampledTick), 64000) : 0;
            lastHeight = relative; sampledTick = now;
            var fuel = rocket.flightData().orElseThrow().fuel();
            long capacity = fuel.propulsion().map(value -> value.descriptor().primaryCapacity() + value.descriptor().oxidizerCapacity()).orElse(fuel.capacity());
            output = RocketMonitorRules.comparator(root.mode(), relative, RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS, fuel.totalAmount(), capacity);
        } else { height = 0; velocity = 0; sampledTick = -1; }
        if (root() != null && !root.authority().isEmpty() && now % 20 == 0
                && resolved.status() != RocketMonitorStatus.WAITING_UNLOADED) {
            var service = RocketRuntime.monitorFlights().orElse(null);
            if (service == null || !root.authority().equals(service.authority(this, root.destination()))) { disarm(); output = 0; }
        }
        if (comparator != output) { comparator = output; level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); }
        signal(level.hasNeighborSignal(worldPosition));
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag); preserved = null;
        root = new RocketMonitorRoot(null, UUID.randomUUID(), null, 0, false, false, null, "");
        if (tag.contains(RocketMonitorRoot.KEY)) {
            var raw = tag.get(RocketMonitorRoot.KEY);
            try { root = RocketMonitorRoot.decode(raw); }
            catch (IllegalArgumentException invalid) { preserved = raw; }
        }
        sampledTick = -1; height = 0; velocity = 0; comparator = 0; lastFlight = null; lastCost = 0;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.put(RocketMonitorRoot.KEY, preserved == null ? root.encode() : preserved);
    }
}

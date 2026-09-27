package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightPlanPacket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.travel.network.TravelTargetWireCodec;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** No-inventory flight console backed only by synchronized server-computed fields. */
public final class RocketFlightMenu extends AbstractContainerMenu {
    private final RocketEntity rocket;
    private final int rocketEntityId;
    private final ContainerData data;
    private final List<StationDestinationSummary> accessibleStations;
    private final UUID currentStationId;
    private final UUID plannedStationId;
    private final TravelTarget openingCurrentTarget;
    private final Player viewer;
    private RocketFlightPlanSnapshot receivedPlan = RocketFlightPlanSnapshot.empty();
    private RocketNavigation receivedNavigation = RocketNavigation.empty();
    private RocketNavigation serverNavigation = RocketNavigation.empty();
    private RocketFlightData quotedFlight;
    private long lastQuoteTick = -RocketNavigation.REFRESH_TICKS;
    private final RocketCatalogSync catalogSync = new RocketCatalogSync();
    private RocketFlightPlanPacket lastSentSnapshot;
    private boolean planReceived;

    public RocketFlightMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readPayload(buffer));
    }

    private RocketFlightMenu(int containerId, Inventory playerInventory, ClientPayload payload) {
        this(
                containerId,
                payload.rocketEntityId(),
                resolveRocket(playerInventory, payload.rocketEntityId()),
                new SimpleContainerData(RocketFlightMenuData.COUNT),
                payload.stations(),
                payload.currentStationId(),
                payload.plannedStationId(),
                payload.currentTarget(),
                playerInventory.player
        );
    }

    public RocketFlightMenu(int containerId, Inventory playerInventory, RocketEntity rocket) {
        this(
                containerId,
                rocket.getId(),
                rocket,
                new RocketFlightMenuData(rocket),
                List.of(),
                null,
                null,
                rocket.flightData().flatMap(RocketFlightData::currentTarget).orElse(null),
                playerInventory.player
        );
    }

    private RocketFlightMenu(
            int containerId,
            int rocketEntityId,
            RocketEntity rocket,
            ContainerData data,
            List<StationDestinationSummary> accessibleStations,
            UUID currentStationId,
            UUID plannedStationId,
            TravelTarget openingCurrentTarget,
            Player viewer
    ) {
        super(ModMenuTypes.ROCKET_FLIGHT.get(), containerId);
        checkContainerDataCount(data, RocketFlightMenuData.COUNT);
        this.rocket = rocket;
        this.rocketEntityId = rocketEntityId;
        this.data = data;
        this.accessibleStations = List.copyOf(accessibleStations);
        this.currentStationId = currentStationId;
        this.plannedStationId = plannedStationId;
        this.openingCurrentTarget = openingCurrentTarget;
        this.viewer = viewer;
        addDataSlots(data);
    }

    private static ClientPayload readPayload(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
        TravelTarget currentTarget = TravelTargetWireCodec.decode(buffer);
        int count = buffer.readVarInt();
        if (count < 0 || count > StationLimits.MAX_ACCESSIBLE_DESTINATIONS) {
            throw new IllegalArgumentException("Station destination list exceeds the fixed bound");
        }
        ArrayList<StationDestinationSummary> stations = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            stations.add(new StationDestinationSummary(
                    buffer.readUUID(),
                    buffer.readUtf(StationLimits.MAX_NAME_LENGTH)
            ));
        }
        UUID currentStation = buffer.readBoolean() ? buffer.readUUID() : null;
        UUID plannedStation = buffer.readBoolean() ? buffer.readUUID() : null;
        return new ClientPayload(entityId, List.copyOf(stations), currentStation, plannedStation, currentTarget);
    }

    private static RocketEntity resolveRocket(Inventory inventory, int entityId) {
        Entity entity = inventory.player.level().getEntity(entityId);
        return entity instanceof RocketEntity rocket ? rocket : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return rocket != null
                && player == viewer && player.isAlive()
                && rocket.isAlive()
                && rocket.operational()
                && rocket.level() == player.level()
                && player.level().hasChunkAt(rocket.blockPosition())
                && player.distanceToSqr(rocket) <= 64.0D;
    }

    public int rocketEntityId() {
        return rocketEntityId;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer instanceof ServerPlayer player && player.containerMenu == this && stillValid(player)) {
            var navigation = navigation();
            var snapshot = new RocketFlightPlanPacket(containerId, rocketEntityId, activePlan(), navigation);
            if (!snapshot.equals(lastSentSnapshot)) {
                RocketFlightNetwork.sendPlan(player, snapshot);
                lastSentSnapshot = snapshot;
            }
            if (catalogSync.attempt(player.getServer().overworld().getGameTime(), navigation.generation())) {
                RocketRuntime.navigationCatalog(player, rocket).ifPresent(packet -> {
                    RocketFlightNetwork.sendCatalog(player, packet);
                    catalogSync.delivered(packet.catalogGeneration());
                });
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != RocketNavigation.REFRESH_BUTTON || !(player instanceof ServerPlayer serverPlayer)
                || !serverPlayer.getServer().isSameThread() || player.containerMenu != this || !stillValid(player)) {
            return false;
        }
        catalogSync.request();
        return true;
    }

    public RocketFlightPlanSnapshot activePlan() {
        if (!viewer.level().isClientSide()) {
            return rocket.flightData().flatMap(flight -> flight.plan())
                    .map(active -> new RocketFlightPlanSnapshot(active.destinationTarget()))
                    .orElse(RocketFlightPlanSnapshot.empty());
        }
        return receivedPlan;
    }

    public boolean hasPlanSnapshot() {
        return planReceived;
    }

    public void acceptPlanSnapshot(RocketFlightPlanSnapshot plan, RocketNavigation navigation) {
        if (viewer.level().isClientSide() && navigation.generation() >= receivedNavigation.generation()) {
            receivedPlan = java.util.Objects.requireNonNull(plan, "plan");
            receivedNavigation = java.util.Objects.requireNonNull(navigation, "navigation");
            planReceived = true;
        }
    }

    public RocketFlightState state() {
        try {
            return RocketFlightState.fromNetworkId(data.get(0));
        } catch (IllegalArgumentException exception) {
            return RocketFlightState.FAILED_RECOVERABLE;
        }
    }

    public int fuelAmount() {
        return data.get(1);
    }

    public int fuelCapacity() {
        return data.get(2);
    }

    public int requiredFuel() {
        return data.get(3);
    }

    public RocketFlightQuotes quotes() {
        return navigation().quotes();
    }

    public RocketNavigation navigation() {
        if (viewer.level().isClientSide()) {
            return receivedNavigation;
        }
        if (!(viewer instanceof ServerPlayer player) || rocket == null || !stillValid(player)) {
            return RocketNavigation.empty();
        }
        var flight = rocket.flightData().orElse(null);
        long now = player.getServer().overworld().getGameTime();
        if (flight != quotedFlight || now < lastQuoteTick || now - lastQuoteTick >= RocketNavigation.REFRESH_TICKS) {
            serverNavigation = RocketRuntime.navigation(player, rocket);
            lastQuoteTick = now;
            quotedFlight = flight;
        }
        return serverNavigation;
    }

    public RocketDestination currentDestination() {
        return LegacyTravelTargetAdapter.toLegacy(currentTarget())
                .map(LegacyTravelTargetAdapter.LegacyDestination::destination)
                .orElse(null);
    }

    public TravelTarget currentTarget() {
        if (!viewer.level().isClientSide() && rocket != null) {
            return rocket.flightData().flatMap(RocketFlightData::currentTarget).orElse(null);
        }
        return openingCurrentTarget;
    }

    public RocketDestination plannedDestination() {
        return destination(data.get(5));
    }

    public boolean canLaunch() {
        return data.get(6) != 0;
    }

    public int countdownRemaining() {
        return data.get(7);
    }

    public int passengerCount() {
        return data.get(8);
    }

    public List<StationDestinationSummary> accessibleStations() {
        return viewer.level().isClientSide() && !planReceived ? accessibleStations
                : navigation().stations().stream().map(RocketNavigation.Station::summary).toList();
    }

    public Optional<UUID> currentStationId() {
        return Optional.ofNullable(currentStationId);
    }

    public Optional<UUID> plannedStationId() {
        return Optional.ofNullable(plannedStationId);
    }

    private static RocketDestination destination(int id) {
        try {
            return RocketDestination.fromNetworkId(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record ClientPayload(
            int rocketEntityId,
            List<StationDestinationSummary> stations,
            UUID currentStationId,
            UUID plannedStationId,
            TravelTarget currentTarget
    ) {
    }
}

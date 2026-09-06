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
    private RocketFlightQuotes receivedQuotes = RocketFlightQuotes.empty();
    private RocketFlightPlanPacket lastSentSnapshot;
    private boolean planReceived;

    public RocketFlightMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(containerId, playerInventory, readPayload(buffer));
    }

    private RocketFlightMenu(int containerId, Inventory playerInventory, ClientPayload payload) {
        this(
                containerId,
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
        rocketEntityId = rocket == null ? -1 : rocket.getId();
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
                && rocket.isAlive()
                && rocket.level() == player.level()
                && player.distanceToSqr(rocket) <= 64.0D;
    }

    public int rocketEntityId() {
        return rocketEntityId;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer instanceof ServerPlayer player && player.containerMenu == this && stillValid(player)) {
            var snapshot = new RocketFlightPlanPacket(containerId, rocketEntityId, activePlan(), quotes());
            if (!snapshot.equals(lastSentSnapshot)) {
                RocketFlightNetwork.sendPlan(player, snapshot);
                lastSentSnapshot = snapshot;
            }
        }
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

    public void acceptPlanSnapshot(RocketFlightPlanSnapshot plan, RocketFlightQuotes quotes) {
        if (viewer.level().isClientSide()) {
            receivedPlan = java.util.Objects.requireNonNull(plan, "plan");
            receivedQuotes = java.util.Objects.requireNonNull(quotes, "quotes");
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
        if (viewer.level().isClientSide()) {
            return receivedQuotes;
        }
        var flight = rocket.flightData().orElse(null);
        var snapshot = rocket.snapshot().orElse(null);
        if (flight == null || snapshot == null) {
            return RocketFlightQuotes.empty();
        }
        return viewer instanceof ServerPlayer player
                ? RocketRuntime.flightQuotes(player, rocket)
                : RocketFlightQuotes.empty();
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
        return accessibleStations;
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

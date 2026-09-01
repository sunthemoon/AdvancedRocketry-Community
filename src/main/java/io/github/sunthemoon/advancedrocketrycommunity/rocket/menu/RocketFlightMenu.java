package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModMenuTypes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationDestinationSummary;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
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
                payload.plannedStationId()
        );
    }

    public RocketFlightMenu(int containerId, Inventory playerInventory, RocketEntity rocket) {
        this(containerId, rocket, new RocketFlightMenuData(rocket), List.of(), null, null);
    }

    private RocketFlightMenu(
            int containerId,
            RocketEntity rocket,
            ContainerData data,
            List<StationDestinationSummary> accessibleStations,
            UUID currentStationId,
            UUID plannedStationId
    ) {
        super(ModMenuTypes.ROCKET_FLIGHT.get(), containerId);
        checkContainerDataCount(data, RocketFlightMenuData.COUNT);
        this.rocket = rocket;
        rocketEntityId = rocket == null ? -1 : rocket.getId();
        this.data = data;
        this.accessibleStations = List.copyOf(accessibleStations);
        this.currentStationId = currentStationId;
        this.plannedStationId = plannedStationId;
        addDataSlots(data);
    }

    private static ClientPayload readPayload(FriendlyByteBuf buffer) {
        int entityId = buffer.readVarInt();
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
        return new ClientPayload(entityId, List.copyOf(stations), currentStation, plannedStation);
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

    public RocketDestination currentDestination() {
        return destination(data.get(4));
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
            UUID plannedStationId
    ) {
    }
}

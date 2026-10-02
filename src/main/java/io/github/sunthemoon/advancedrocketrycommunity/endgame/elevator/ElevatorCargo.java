package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.cargo.CargoStorage;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.model.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-059 section 6: cargo between the two endpoints of one pair, through the ADR-054 section 11 ledger. The payload is
 * the whole input buffer (up to 4 stacks), the destination is always the other end of the departing endpoint's pair,
 * which must be valid at escrow, the cost is {@code 20,000 × energyPercent / 100} FE and the travel 200 ticks.
 * Delivery is endpoint-addressed, so it completes even if the pair is unbound or invalid afterwards.
 */
final class ElevatorCargo {
    private ElevatorCargo() {
    }

    /**
     * The first refusal, in order: the root, the switch, the endpoint's own state, a pair, its validity, ship access
     * (as the actor, or for an automatic launch as the endpoint's owner, never an operator), a payload within the
     * stack bound, the destination's durable registration, the energy, a source rollback, a free outbox slot and the
     * ledger's admission. A dry check ({@code escrow} false) changes nothing.
     */
    static ElevatorRules.Check launch(ElevatorEndpointBlockEntity endpoint, ServerLevel level, EndgameService service,
                                      EndgameDevices devices, @Nullable UUID actor, boolean escrow) {
        long now = level.getGameTime();
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.ROOT_UNAVAILABLE);
        }
        if (!devices.settings().enabled(EndgameSystem.SPACE_ELEVATOR)) {
            return ElevatorRules.Check.of(EndgameCode.SYSTEM_DISABLED);
        }
        EndgameCode own = ownState(endpoint);
        if (own != EndgameCode.OK) {
            return ElevatorRules.Check.of(own);
        }
        EndgameRoot root = view.get();
        UUID id = endpoint.endpointId();
        Optional<ElevatorPair> pair = root.pairs().forEndpoint(id);
        if (pair.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.NOT_BOUND);
        }
        MinecraftServer server = level.getServer();
        ElevatorRules.Check validity = ElevatorPairs.validity(server, root, devices, pair.get());
        if (!validity.ok()) {
            return validity;
        }
        UUID shipper = actor != null ? actor : endpoint.ownerId().orElseThrow();
        // Only a player's intent carries the operator exemption; automation never inherits its owner's (ADR-054
        // section 3, review C12R-L7).
        ServerPlayer online = actor == null ? null : server.getPlayerList().getPlayer(shipper);
        EndgameCode access = ElevatorPairs.access(server, root, pair.get(), shipper,
                online != null && online.hasPermissions(2));
        if (access != EndgameCode.OK) {
            return ElevatorRules.Check.of(access);
        }
        CargoStorage storage = endpoint.storage();
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < CargoStorage.INPUT_SLOTS; slot++) {
            ItemStack stack = storage.input().getStackInSlot(slot);
            if (!stack.isEmpty()) {
                slots.add(slot);
                stacks.add(stack.copy());
            }
        }
        if (stacks.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.NO_PAYLOAD);
        }
        Optional<TransitPayload> payload = TransitPayload.of(stacks);
        if (payload.isEmpty()) {
            return ElevatorRules.Check.of(EndgameCode.PAYLOAD_TOO_LARGE);
        }
        UUID destination = pair.get().otherEnd(id);
        if (!root.registrationDurable(destination)) {
            return ElevatorRules.Check.of(EndgameCode.AWAITING_WORLD_SAVE);
        }
        int cost = ElevatorRules.cargoCost(devices.elevatorSettings().energyPercent());
        if (storage.energy().energy() < cost) {
            return ElevatorRules.Check.of(EndgameCode.INSUFFICIENT_ENERGY);
        }
        if (escrow && service.transits().reconcileSource(endpoint, now).escrowBlocked()) {
            return ElevatorRules.Check.of(EndgameCode.ROOT_BUSY);
        }
        if (!endpoint.source().outboxFree()) {
            return ElevatorRules.Check.of(EndgameCode.OUTBOX_FULL);
        }
        EndgameCode admission = service.transits().admitEscrow(id, endpoint.ownerId().orElseThrow());
        if (admission != EndgameCode.OK || !escrow) {
            return ElevatorRules.Check.of(admission);
        }
        for (int i = 0; i < slots.size(); i++) {
            ItemStack taken = storage.input().extractItem(slots.get(i), stacks.get(i).getCount(), false);
            if (!ItemStack.matches(taken, stacks.get(i))) {
                throw new IllegalStateException("The selected payload changed during its escrow");
            }
        }
        storage.energy().spend(cost);
        OutboxEntry entry = endpoint.source().escrow(destination, payload.get(), cost,
                ElevatorRules.CARGO_TRAVEL_TICKS, EndgameSystem.SPACE_ELEVATOR);
        endpoint.setChanged();
        endpoint.escrowed(now);
        endpoint.audit(service, now, "escrow", EndgameCode.OK, actor, "seq=" + entry.seq() + " to=" + destination
                + " pair=" + pair.get().pairId() + " paid_fe=" + cost + " payload=" + payload.get().hash());
        return ElevatorRules.Check.OK;
    }

    /** The endpoint's own state: owned, registered here, not frozen, its placement usable. */
    static EndgameCode ownState(ElevatorEndpointBlockEntity endpoint) {
        if (endpoint.ownerId().isEmpty()) {
            return EndgameCode.UNOWNED;
        }
        if (endpoint.frozen()) {
            return EndgameCode.ENDPOINT_RETIRED;
        }
        if (endpoint.endpointStatus() != EndgameCode.OK) {
            return endpoint.endpointStatus();
        }
        return endpoint.placement();
    }
}

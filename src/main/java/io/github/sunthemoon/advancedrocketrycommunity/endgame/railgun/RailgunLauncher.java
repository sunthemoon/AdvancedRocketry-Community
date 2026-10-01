package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.authority.EndgameAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-056 section 4 on a live server: gathers the {@link RailgunLaunch.Facts} of one launch and, when nothing refuses
 * it, performs the ADR-054 section 11 escrow in this tick: the whole selected stack leaves the input, the quoted cost
 * leaves the energy buffer, and both become outbox entry {@code next_seq} with the class's travel time. The source
 * reconciles right before (section 11 "before each escrow"). A dry check (a launch intent) changes nothing.
 */
final class RailgunLauncher {
    private RailgunLauncher() {
    }

    /** What the selected destination looks like from this railgun now. */
    record Route(EndgameCode code, Optional<UUID> destination, Optional<RailgunRoute.Quote> quote, boolean durable) {
    }

    /**
     * @param escrow false for the dry check of a launch intent: no reconciliation and no change
     */
    static EndgameCode launch(RailgunBlockEntity railgun, ServerLevel level, EndgameService service,
                              EndgameDevices devices, @Nullable UUID actor, boolean escrow) {
        long now = level.getGameTime();
        Optional<EndgameRoot> view = service.root();
        Optional<UUID> owner = railgun.ownerId();
        boolean authorized = owner.isPresent() && EndgameAuthority.decide(new EndgameAuthority.Request(owner.get(),
                false, owner, EndgameStations.at(level, railgun.getBlockPos()).context(), EndgameAction.OPERATE,
                false)).allowed();
        EndgameCode sourceState = sourceState(railgun);
        boolean blocked = false;
        if (escrow && view.isPresent() && sourceState == EndgameCode.OK) {
            blocked = service.transits().reconcileSource(railgun, now).escrowBlocked();
        }
        RailgunStorage storage = railgun.storage();
        int slot = RailgunLaunch.payloadSlot(storage.inputStacks(), railgun.minStack());
        ItemStack stack = slot < 0 ? ItemStack.EMPTY : storage.input().getStackInSlot(slot).copy();
        Optional<TransitPayload> payload = slot < 0 ? Optional.empty() : TransitPayload.of(List.of(stack));
        Route route = view.map(root -> route(railgun, level, root, devices, railgun.target(),
                railgun.operatorTarget())).orElse(new Route(EndgameCode.ROOT_UNAVAILABLE, Optional.empty(),
                Optional.empty(), false));
        boolean energy = route.quote().filter(quote -> storage.energy().energy() >= quote.cost()).isPresent();
        EndgameCode admission = view.isEmpty() || owner.isEmpty() ? EndgameCode.ROOT_UNAVAILABLE
                : service.transits().admitEscrow(railgun.endpointId(), owner.get());
        EndgameCode code = RailgunLaunch.refusal(new RailgunLaunch.Facts(view.isPresent(),
                devices.settings().enabled(EndgameSystem.RAILGUN), authorized, sourceState, slot, payload.isPresent(),
                route.code(), route.durable(), energy, railgun.source().outboxFree(), blocked, admission));
        if (code != EndgameCode.OK || !escrow) {
            return code;
        }
        RailgunRoute.Quote quote = route.quote().orElseThrow();
        ItemStack taken = storage.input().extractItem(slot, stack.getCount(), false);
        if (!ItemStack.matches(taken, stack)) {
            throw new IllegalStateException("The selected payload changed during its escrow");
        }
        storage.energy().spend(quote.cost());
        OutboxEntry entry = railgun.source().escrow(route.destination().orElseThrow(), payload.get(), quote.cost(),
                quote.travel(), EndgameSystem.RAILGUN);
        railgun.setChanged();
        railgun.launched(now);
        audit(railgun, service, now, "escrow", EndgameCode.OK, actor, "seq=" + entry.seq() + " to="
                + entry.destination() + " class=" + quote.routeClass().name() + " paid_fe=" + quote.cost()
                + " travel=" + quote.travel() + " payload=" + payload.get().hash());
        return EndgameCode.OK;
    }

    /** The railgun's own state before anything else: owned, registered here, not frozen, its structure formed. */
    static EndgameCode sourceState(RailgunBlockEntity railgun) {
        if (railgun.ownerId().isEmpty()) {
            return EndgameCode.UNOWNED;
        }
        if (railgun.frozen()) {
            return EndgameCode.ENDPOINT_RETIRED;
        }
        if (railgun.endpointStatus() != EndgameCode.OK) {
            return railgun.endpointStatus();
        }
        return railgun.structureCode();
    }

    /** The route rule (section 3) and the quote for the selected destination, from the index and the live catalog. */
    static Route route(RailgunBlockEntity railgun, ServerLevel level, EndgameRoot root, EndgameDevices devices,
                       Optional<UUID> selected, boolean operator) {
        Optional<EndpointRecord> to = selected.flatMap(root::endpoint);
        if (to.isEmpty() || railgun.ownerId().isEmpty()) {
            return new Route(EndgameCode.NO_TARGET, Optional.empty(), Optional.empty(), false);
        }
        MinecraftServer server = level.getServer();
        RailgunRoute.Place from = place(devices, server, level.dimension().location(),
                railgun.getBlockPos().asLong());
        RailgunRoute.Place toPlace = place(devices, server, to.get().level(), to.get().pos());
        EndgameCode code = RailgunRoute.check(railgun.endpointId(), railgun.ownerId().get(), from,
                candidate(to.get()), toPlace, operator, false);
        if (code != EndgameCode.OK) {
            return new Route(code, to.map(EndpointRecord::id), Optional.empty(), false);
        }
        int percent = devices.railgunSettings().energyPercent();
        return new Route(code, to.map(EndpointRecord::id), Optional.of(RailgunRoute.quote(from, toPlace, percent)),
                root.registrationDurable(to.get().id()));
    }

    static RailgunRoute.Candidate candidate(EndpointRecord record) {
        return new RailgunRoute.Candidate(record.id(), record.kind().equals(RailgunBlockEntity.KIND),
                record.state() == EndpointRecord.State.ACTIVE, record.owner());
    }

    static RailgunRoute.Place place(EndgameDevices devices, MinecraftServer server, ResourceLocation level, long pos) {
        Optional<EndgameStations.Body> body = devices.body(server, level, pos);
        return new RailgunRoute.Place(level, pos, body.map(EndgameStations.Body::body),
                body.map(EndgameStations.Body::system));
    }

    static void audit(RailgunBlockEntity railgun, EndgameService service, long now, String action, EndgameCode code,
                      @Nullable UUID actor, String fields) {
        service.audit().line(now, EndgameSystem.RAILGUN.id(), action, code.name(), railgun.deviceId().orElse(null),
                railgun.ownerId().orElse(null), actor, fields);
    }
}

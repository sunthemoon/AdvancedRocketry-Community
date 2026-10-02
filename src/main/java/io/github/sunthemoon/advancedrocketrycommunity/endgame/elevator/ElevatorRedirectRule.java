package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.TransitOperations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * ADR-054 section 11 and ADR-059 section 6 (review R3-L4): elevator cargo whose destination is gone goes back to its
 * source, or to the endpoint of the original destination's kind in a currently valid pair, so a pair rebuilt after both
 * ends were removed can still receive it. The original destination's kind is the opposite of the source's while the
 * source is indexed; without it, either kind in a valid pair is accepted.
 */
public final class ElevatorRedirectRule implements TransitOperations.Route {
    private final EndgameService service;
    private final Supplier<Optional<EndgameDevices>> devices;

    public ElevatorRedirectRule(EndgameService service, Supplier<Optional<EndgameDevices>> devices) {
        this.service = Objects.requireNonNull(service, "service");
        this.devices = Objects.requireNonNull(devices, "devices");
    }

    @Override
    public EndgameCode check(EndgameRoot root, TransitRecord record, EndpointRecord target, boolean operator) {
        boolean anchor = target.kind().equals(ElevatorAnchorBlockEntity.KIND);
        if (!anchor && !target.kind().equals(ElevatorTerminalBlockEntity.KIND)) {
            return EndgameCode.ROUTE_REFUSED;
        }
        if (target.id().equals(record.key().source())) {
            return EndgameCode.OK;
        }
        Optional<ResourceLocation> sourceKind = root.endpoint(record.key().source()).map(EndpointRecord::kind);
        if (sourceKind.filter(kind -> kind.equals(target.kind())).isPresent()) {
            return EndgameCode.ROUTE_REFUSED; // The original destination was of the other kind.
        }
        Optional<ElevatorPair> pair = root.pairs().forEndpoint(target.id());
        if (pair.isEmpty()) {
            return EndgameCode.NOT_BOUND;
        }
        Optional<MinecraftServer> server = service.server();
        Optional<EndgameDevices> current = devices.get();
        if (server.isEmpty() || current.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        return ElevatorPairs.validity(server.get(), root, current.get(), pair.get()).code();
    }
}

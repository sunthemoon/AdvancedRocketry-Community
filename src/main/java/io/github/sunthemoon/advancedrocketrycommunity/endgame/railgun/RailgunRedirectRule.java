package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

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
import net.minecraft.server.MinecraftServer;

/**
 * ADR-054 section 11 and ADR-056 section 5: railgun cargo is redirected to another of its owner's {@code ACTIVE}
 * railgun endpoints in the source's star system; back to the source itself is allowed. The source's star system comes
 * from its index record, so cargo whose source is gone too needs an operator purge ({@code BODY_UNAVAILABLE}).
 */
public final class RailgunRedirectRule implements TransitOperations.Route {
    private final EndgameService service;
    private final Supplier<Optional<EndgameDevices>> devices;

    public RailgunRedirectRule(EndgameService service, Supplier<Optional<EndgameDevices>> devices) {
        this.service = Objects.requireNonNull(service, "service");
        this.devices = Objects.requireNonNull(devices, "devices");
    }

    @Override
    public EndgameCode check(EndgameRoot root, TransitRecord record, EndpointRecord target, boolean operator) {
        Optional<MinecraftServer> server = service.server();
        Optional<EndgameDevices> current = devices.get();
        if (server.isEmpty() || current.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        Optional<EndpointRecord> source = root.endpoint(record.key().source());
        if (source.isEmpty()) {
            return EndgameCode.BODY_UNAVAILABLE;
        }
        RailgunRoute.Place from = RailgunLauncher.place(current.get(), server.get(), source.get().level(),
                source.get().pos());
        RailgunRoute.Place to = RailgunLauncher.place(current.get(), server.get(), target.level(), target.pos());
        return RailgunRoute.check(record.key().source(), record.owner(), from, RailgunLauncher.candidate(target), to,
                operator, true);
    }
}

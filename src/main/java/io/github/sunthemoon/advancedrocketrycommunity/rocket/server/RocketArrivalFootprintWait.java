package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * W83b (Root decision R32 item 1): the existing PREPARED wait also holds a due arrival until every column of its
 * destination ticket set ({@link RocketTransferEntities#destinationTicketSet}, at most 16) has its entity sections
 * loaded, so the pad recheck that follows can see a rocket parked there before {@code DESTINATION_SPAWNED}. The check
 * reads only. The destination flight tickets load those columns, so the wait ends without a timeout of its own. RAM
 * only and never saved: one INFO line and one passenger action-bar message when a transfer starts waiting, one INFO line
 * when it is ready again. At most {@link RocketFlightLimits#MAX_ACTIVE_TRANSFERS} entries, pruned to the live transfers.
 */
final class RocketArrivalFootprintWait {
    static final String REASON = RocketLandingPadSelector.ENTITIES_LOADING;

    private final Set<UUID> live;
    private final Consumer<String> info;
    private final Map<UUID, Long> waiting = new HashMap<>();

    RocketArrivalFootprintWait(Set<UUID> live) { this(live, message -> AdvancedRocketryCommunity.LOGGER.info(message)); }

    RocketArrivalFootprintWait(Set<UUID> live, Consumer<String> info) {
        this.live = Objects.requireNonNull(live, "live");
        this.info = Objects.requireNonNull(info, "info");
    }

    /** True when the arrival may proceed to the pad recheck; false leaves the record PREPARED for the next visit. */
    boolean ready(ServerLevel destination, RocketTransferRecord record, RocketEntity source) {
        var columns = RocketTransferEntities.destinationTicketSet(record.destinationSnapshot());
        int loaded = RocketTransferEntities.entitiesLoadedColumns(columns, destination::areEntitiesLoaded);
        if (observe(record.transferId(), destination.getGameTime(), loaded, columns.size(), () -> "logical="
                + record.logicalRocketId() + " level=" + destination.dimension().location())) {
            for (var passenger : RocketTransferEntities.aboard(source)) {
                if (passenger.connection != null) { passenger.displayClientMessage(Component.translatable(REASON), true); }
            }
        }
        return loaded >= columns.size();
    }

    /** Server-free transition state. True only on the visit that starts a wait; {@code where} is read only then. */
    boolean observe(UUID transfer, long tick, int loaded, int columns, Supplier<String> where) {
        if (loaded >= columns) {
            Long since = waiting.remove(transfer);
            if (since != null) { info.accept("ARCE_ARRIVAL_ENTITIES_READY transfer=" + transfer + " waited=" + (tick - since)); }
            return false;
        }
        if (waiting.containsKey(transfer)) { return false; }
        waiting.keySet().retainAll(live);
        // Diagnostics never fail a tick; past the transfer bound a wait is simply not reported.
        if (waiting.size() >= RocketFlightLimits.MAX_ACTIVE_TRANSFERS) { return false; }
        waiting.put(transfer, tick);
        info.accept("ARCE_ARRIVAL_ENTITIES_LOADING transfer=" + transfer + " " + where.get() + " columns=" + loaded + "/"
                + columns + " reason=entities_loading");
        return true;
    }

    boolean waiting(UUID transfer) { return waiting.containsKey(transfer); }
    int size() { return waiting.size(); }
    void clear() { waiting.clear(); }
}

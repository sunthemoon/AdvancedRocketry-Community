package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Runtime-lifetime diagnostics only; a hold never supplies action or recovery authority. */
public final class EscrowGovernedExecutorHolds {
    public enum Reason { PERSISTENT_PLACEMENT, SOURCE_ENVELOPE, DESTINATION_ENVELOPE, PHASE_ENVELOPE,
        ARRIVAL_ENVELOPE, RETURN_ENVELOPE, TERMINAL_ENVELOPE, NATIVE_ENDPOINT }
    record Notice(UUID transfer, Reason reason) { }
    private final Map<UUID, State> flights = new HashMap<>();
    private final Consumer<Notice> logger;

    EscrowGovernedExecutorHolds() {
        this(notice -> AdvancedRocketryCommunity.LOGGER.warn("ARCE_GOVERNED_EXECUTOR_HOLD transfer={} reason={}",
                notice.transfer(), notice.reason()));
    }
    EscrowGovernedExecutorHolds(Consumer<Notice> logger) { this.logger = logger; }

    public void hold(MinecraftServer server, RocketTransferRecord record, Reason reason) {
        EscrowTags.require(server.isSameThread(), "Executor diagnostics require the owner thread");
        retain(RocketTransferSavedData.get(server).entries()); hold(record.transferId(), reason);
    }
    void hold(UUID transfer, Reason reason) {
        EscrowTags.require(flights.containsKey(transfer) || flights.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                "Executor diagnostic flight limit exceeded");
        var state = flights.computeIfAbsent(transfer, ignored -> new State());
        if (state.active != null) { return; }
        state.active = reason;
        if (state.reported.add(reason)) { logger.accept(new Notice(transfer, reason)); }
    }
    public boolean held(RocketTransferRecord record) { return held(record.transferId()); }
    boolean held(UUID transfer) { var state = flights.get(transfer); return state != null && state.active != null; }
    void healthy(RocketTransferRecord record) { healthy(record.transferId()); }
    void healthy(UUID transfer) { var state = flights.get(transfer); if (state != null) { state.active = null; } }
    public void retain(java.util.List<RocketTransferRecord> records) {
        var present = records.stream().map(RocketTransferRecord::transferId).collect(java.util.stream.Collectors.toSet());
        flights.keySet().retainAll(present);
    }
    void clear() { flights.clear(); }
    private static final class State {
        final EnumSet<Reason> reported = EnumSet.noneOf(Reason.class);
        Reason active;
    }
}

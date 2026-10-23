package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Test-artifact-only access to the installed return coordinator, never a substitute coordinator. */
public final class W151NativeReturns {
    private W151NativeReturns() { }

    public static RocketEntity create(ServerLevel level, BlockPos position, UUID logical, UUID owner) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        var fixture = RocketSourceRetirementFixtures.countdown(level, position, position.east(2), List.of(), 0);
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initialize(fixture.record().sourceSnapshot(), logical, owner);
        var initial = rocket.flightData().orElseThrow();
        rocket.updateFlightData(initial.withFuel(fixture.countdown().fuel(), level.getGameTime()));
        require(level.addFreshEntity(rocket), "Ordinary source publication refused");
        return rocket;
    }

    public static Map<String, Object> observation(RocketEntity rocket) {
        var retirement = RocketSourceRetirementFixtures.recovery(Objects.requireNonNull(RocketRuntime.installedTransfers())).retirements();
        UUID logical = rocket.assemblyTransactionId().orElseThrow();
        Map<UUID, Object> entries = RocketSourceRetirementFixtures.field(retirement, "pending");
        Object entry = entries.get(logical);
        if (entry == null) { return Map.of("pending", false); }
        return Map.of("pending", true, "attempts", retirement.attempts(logical), "reason", retirement.reason(logical),
                "cursor", RocketSourceRetirementFixtures.<Integer>field(entry, "cursor"),
                "not_before", RocketSourceRetirementFixtures.<Long>field(entry, "notBefore"),
                "state", RocketSourceRetirementFixtures.field(entry, "state").toString());
    }

    public static void request(ServerPlayer player, RocketEntity rocket, RocketFlightAction action, UUID request) {
        player.teleportTo((ServerLevel) rocket.level(), rocket.getX(), rocket.getY() + 3, rocket.getZ(), 0, 0);
        RocketRuntime.requestFlightIntent(player, rocket.getId(), action, RocketDestination.MOON, request);
    }

    /** Read the normal player replay ledger; a pending warm-up must not be mistaken for a discarded request. */
    public static io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger.Outcome requestOutcome(
            RocketEntity rocket, UUID request) {
        EscrowTestEndpoints.requireEnabled(rocket.getServer());
        try {
            var field = RocketRuntime.class.getDeclaredField("service"); field.setAccessible(true);
            Object manager = field.get(null);
            Object flights = RocketSourceRetirementFixtures.field(manager, "flights");
            io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger requests =
                    RocketSourceRetirementFixtures.field(flights, "requests");
            return requests.outcome(request);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    public static final class Producer {
        public final RocketEntity rocket;
        public int launches, cancellations, completed, polls;
        private UUID request;
        private boolean cancelling;
        private long next;
        private final Consumer<Map<String, Object>> events;
        public Producer(RocketEntity rocket, Consumer<Map<String, Object>> events) { this.rocket = rocket; this.events = events; }
        public boolean countdown() { return rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN; }
        public void tick(ServerPlayer player, boolean producing) {
            var server = player.getServer(); long now = server.getTickCount();
            var journal = RocketTransferSavedData.get(server); UUID logical = rocket.assemblyTransactionId().orElseThrow();
            var record = journal.findByLogicalRocket(logical).orElse(null);
            var observation = observation(rocket);
            if (Boolean.TRUE.equals(observation.get("pending"))) {
                polls++; var row = new LinkedHashMap<String, Object>(observation); row.put("event", "ordinary_pending_tick");
                row.put("logical", logical.toString()); row.put("tick", now); events.accept(row);
            }
            if (cancelling && record == null && journal.actionAuthorityAvailable(logical)) {
                require(!Boolean.TRUE.equals(observation.get("pending")), "T absent before return acknowledgement");
                require(rocket.isAlive() && rocket.flightData().orElseThrow().fuel().totalAmount() == 1000,
                        "Ordinary cycle lost rocket/fuel");
                completed++; cancelling = false; request = null;
                events.accept(Map.of("event", "ordinary_ack", "logical", logical.toString(), "tick", now, "completed", completed));
            }
            if (now < next) { return; }
            if (!cancelling && countdown() && record != null && journal.actionAuthorityAvailable(logical)) {
                UUID cancel = UUID.randomUUID(); request(player, rocket, RocketFlightAction.CANCEL, cancel);
                cancellations++; cancelling = true; next = now + 10;
                events.accept(Map.of("event", "ordinary_cancel", "logical", logical.toString(), "tick", now, "request", cancel.toString()));
            } else if (producing && !cancelling && request == null && record == null && journal.actionAuthorityAvailable(logical)) {
                request = UUID.randomUUID(); request(player, rocket, RocketFlightAction.LAUNCH, request);
                launches++; next = now + 10;
                events.accept(Map.of("event", "ordinary_launch", "logical", logical.toString(), "tick", now, "request", request.toString()));
            }
        }
    }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
}

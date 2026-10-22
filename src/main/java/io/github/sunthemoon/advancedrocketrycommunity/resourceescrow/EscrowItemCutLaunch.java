package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * W143: one uncrewed governed launch of a cargo rocket to a same-Level pad through the production F1 entry
 * ({@code EscrowGovernedPreparation.begin}), with EscrowTerminalProbe's plan shape and a 300 mB debit. A preparation
 * discarded by a superseding save is begun again only after the product's own retry backoff, at most three times.
 */
final class EscrowItemCutLaunch {
    private final ServerLevel level;
    private final BlockPos pad;
    private final BooleanSupplier loaded;
    private UUID logical;
    private boolean begun, accepted, discarded;
    private int attempts;

    EscrowItemCutLaunch(ServerLevel level, BlockPos pad, BooleanSupplier loaded) { this.level = level; this.pad = pad; this.loaded = loaded; }

    boolean accepted() { return accepted; }

    /** Begins, or begins again after a discarded F1, a launch of {@code rocket}; true once F1 accepted it. */
    boolean tick(RocketEntity rocket) {
        if (accepted) { return true; }
        var server = level.getServer();
        if (begun && !discarded) { return false; }
        if (discarded && !EscrowRuntime.of(server).flightCheckpoint().retryReady(logical, server.getTickCount())) { return false; }
        long now = level.getGameTime(); var flight = rocket.flightData().orElseThrow(); var fuel = flight.fuel();
        var from = flight.currentBody();
        var target = RocketFlightPlanner.MOON.bodyId().equals(from) ? RocketFlightPlanner.EARTH.bodyId() : RocketFlightPlanner.MOON.bodyId();
        var plan = new RocketFlightPlan(3, UUID.randomUUID(), from, target, level.dimension().location(), level.dimension().location(), 300, now)
                .withPropulsionDebit(new RocketPropulsionDebit(300, 0, fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(),
                        RocketNativeResources.hash("W143 cargo cut launch")));
        var countdown = flight.withPlan(plan).startCountdown(now);
        if (!EscrowGovernedPreparation.available(rocket, countdown)) { return false; }
        var to = rocket.snapshot().orElseThrow().relocated(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(pad.getX(), pad.getY(), pad.getZ()), now);
        EscrowTags.require(++attempts <= 3, "W143 launch exceeded three F1 attempts");
        discarded = false; logical = flight.logicalRocketId();
        begun = EscrowGovernedPreparation.begin(rocket, countdown, to, record -> loaded.getAsBoolean(), record -> accepted = true, () -> discarded = true);
        EscrowTags.require(begun, "W143 F1 preparation was refused");
        return false;
    }
}


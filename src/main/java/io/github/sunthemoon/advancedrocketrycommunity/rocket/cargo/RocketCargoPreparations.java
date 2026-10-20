package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Optional;
import java.util.UUID;

/** Session-owned single reconstruction slot. C-E owns queue ordering; an admitted continuation cannot be displaced. */
public final class RocketCargoPreparations implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final RocketCargoWorkBudget budget = new RocketCargoWorkBudget();
    private Ticket active;
    private boolean closed;

    public Optional<Ticket> begin(RocketEntity rocket, EscrowCargoPayload delta, boolean load) {
        return begin(rocket, delta, load, false);
    }
    /** Read-only replay construction still needs the transfer/bay boundary; publication needs a separate permit. */
    public Optional<Ticket> beginReplay(RocketEntity rocket, EscrowCargoPayload delta, boolean load) {
        return begin(rocket, delta, load, true);
    }
    private Optional<Ticket> begin(RocketEntity rocket, EscrowCargoPayload delta, boolean load, boolean replay) {
        owner();
        discardFinished();
        if (closed || active != null || rocket.getServer() == null || !rocket.getServer().isSameThread()
                || rocket.flightData().isEmpty() || rocket.getDeltaMovement().lengthSqr() != 0
                || !replay && !io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.idle(rocket)
                || !java.util.Set.of(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState.ASSEMBLED,
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState.FUELED,
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState.LANDED)
                        .contains(rocket.flightData().orElseThrow().state())
                || !(replay ? RocketRuntime.escrowReconciliationBoundary(rocket) : RocketRuntime.resourceMutationAllowed(rocket))) {
            return Optional.empty();
        }
        active = new Ticket(rocket, delta, load); return Optional.of(active);
    }
    public boolean locked(UUID logical) { owner(); discardFinished(); return active != null && active.cursor.logical().equals(logical); }
    private void discardFinished() {
        if (active != null && !active.valid()) { active.close(); }
    }
    public void removed(RocketEntity rocket) { owner(); if (active != null && active.rocket == rocket) { active.close(); } }
    public RocketCargoWorkBudget budget() { owner(); return budget; }
    private void owner() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Cargo preparation requires server owner thread"); }
    }
    @Override public void close() { owner(); closed = true; if (active != null) { active.close(); } }

    public final class Ticket implements AutoCloseable {
        private final RocketEntity rocket;
        private final RocketFlightData flight;
        private final RocketEscrowSection wrapper;
        private final UUID ownerId;
        private final net.minecraft.world.level.Level level;
        private final net.minecraft.world.phys.Vec3 position;
        private final RocketCargoPreparation cursor;
        private boolean discarded;
        private Ticket(RocketEntity rocket, EscrowCargoPayload delta, boolean load) {
            this.rocket = rocket; flight = rocket.flightData().orElseThrow(); wrapper = rocket.escrowSection();
            ownerId = rocket.ownerId().orElseThrow(); level = rocket.level(); position = rocket.position();
            cursor = new RocketCargoPreparation(rocket.assemblyTransactionId().orElseThrow(), rocket.snapshot().orElseThrow(), delta, load);
        }
        public RocketEntity rocket() { owner(); return rocket; }
        public RocketCargoPreparation cursor() { owner(); return cursor; }
        public boolean valid() {
            owner();
            return !closed && !discarded && active == this && rocket.isAlive() && rocket.level() == level
                    && cursor.state() != RocketCargoPreparation.State.DISCARDED
                    && rocket.level().hasChunkAt(rocket.blockPosition()) && position.equals(rocket.position())
                    && rocket.getDeltaMovement().lengthSqr() == 0 && rocket.snapshot().orElse(null) == cursor.before()
                    && rocket.flightData().orElse(null) == flight && rocket.escrowSection().equals(wrapper)
                    && rocket.ownerId().filter(ownerId::equals).isPresent()
                    && rocket.assemblyTransactionId().filter(cursor.logical()::equals).isPresent();
        }
        public boolean advance() {
            owner();
            if (!valid()) { close(); return false; }
            try { return cursor.advance(rocket.getServer().getTickCount(), budget); }
            catch (RuntimeException failure) { close(); throw failure; }
        }
        @Override public void close() {
            owner(); if (!discarded) { discarded = true; cursor.close(); if (active == this) { active = null; } }
        }
    }
}

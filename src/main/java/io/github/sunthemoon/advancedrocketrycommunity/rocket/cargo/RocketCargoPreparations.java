package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Optional;
import java.util.UUID;

/** Session-owned single reconstruction slot. C-E owns queue ordering; an admitted continuation cannot be displaced. */
public final class RocketCargoPreparations implements AutoCloseable {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private final Thread owner = Thread.currentThread();
    private final RocketCargoWorkBudget budget = new RocketCargoWorkBudget();
    private Ticket active;
    private boolean closed;

    public Optional<Ticket> begin(RocketEntity rocket, EscrowCargoPayload delta, boolean load) {
        return begin(rocket, false, () -> new Ticket(rocket, delta, load));
    }
    /** Read-only replay construction still needs the transfer/bay boundary; publication needs a separate permit. */
    public Optional<Ticket> beginReplay(RocketEntity rocket, EscrowCargoPayload delta, boolean load) {
        return begin(rocket, true, () -> new Ticket(rocket, delta, load));
    }
    /** Item deltas share the same slot, gates, budget, lifetime and publication as fluid deltas. */
    public Optional<Ticket> begin(RocketEntity rocket, EscrowItemCargoPayload delta, boolean load) {
        return begin(rocket, false, () -> new Ticket(rocket, delta, load));
    }
    public Optional<Ticket> beginReplay(RocketEntity rocket, EscrowItemCargoPayload delta, boolean load) {
        return begin(rocket, true, () -> new Ticket(rocket, delta, load));
    }
    private Optional<Ticket> begin(RocketEntity rocket, boolean replay, java.util.function.Supplier<Ticket> ticket) {
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
        active = ticket.get(); return Optional.of(active);
    }
    public boolean locked(UUID logical) { owner(); discardFinished(); return active != null && active.cursor.logical().equals(logical); }
    public void tick() { owner(); discardFinished(); }
    private void discardFinished() {
        if (active == null) { return; }
        String expired = active.lifetime.expired(active.rocket.getServer().getTickCount());
        if (expired != null) {
            LOGGER.info("ARCE_CARGO_PREPARATION_CLOSED logical={} stage={} reason={}",
                    active.cursor.logical(), active.cursor.state(), expired);
            active.close();
        } else if (!active.valid()) { active.close(); }
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
        private final RocketCargoTicketLifetime lifetime;
        private boolean discarded;
        private Ticket(RocketEntity rocket, EscrowCargoPayload delta, boolean load) {
            this(rocket, logical -> new RocketCargoPreparation(logical, rocket.snapshot().orElseThrow(), delta, load));
        }
        private Ticket(RocketEntity rocket, EscrowItemCargoPayload delta, boolean load) {
            this(rocket, logical -> new RocketCargoPreparation(logical, rocket.snapshot().orElseThrow(), delta, load));
        }
        private Ticket(RocketEntity rocket, java.util.function.Function<UUID, RocketCargoPreparation> preparation) {
            this.rocket = rocket; flight = rocket.flightData().orElseThrow(); wrapper = rocket.escrowSection();
            ownerId = rocket.ownerId().orElseThrow(); level = rocket.level(); position = rocket.position();
            cursor = preparation.apply(rocket.assemblyTransactionId().orElseThrow());
            lifetime = new RocketCargoTicketLifetime(rocket.getServer().getTickCount());
        }
        public RocketEntity rocket() { owner(); return rocket; }
        public RocketCargoPreparation cursor() { owner(); return cursor; }
        /** Capture/admission/publication share the same runtime reservation and actual server clock as reconstruction. */
        public boolean performWork(long bytes, int visits,
                java.util.function.Supplier<io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work> work) {
            owner(); discardFinished();
            if (!valid()) { close(); return false; }
            long tick = rocket.getServer().getTickCount();
            return budget.claim(tick, cursor) && budget.perform(tick, bytes, visits, false, work);
        }
        public boolean valid() {
            owner();
            return !closed && !discarded && active == this && rocket.isAlive() && rocket.level() == level
                    && lifetime.expired(rocket.getServer().getTickCount()) == null
                    && cursor.state() != RocketCargoPreparation.State.DISCARDED
                    && rocket.level().hasChunkAt(rocket.blockPosition()) && position.equals(rocket.position())
                    && rocket.getDeltaMovement().lengthSqr() == 0 && rocket.snapshot().orElse(null) == cursor.before()
                    && rocket.flightData().orElse(null) == flight && rocket.escrowSection().equals(wrapper)
                    && rocket.ownerId().filter(ownerId::equals).isPresent()
                    && rocket.assemblyTransactionId().filter(cursor.logical()::equals).isPresent();
        }
        public boolean advance() {
            owner();
            discardFinished();
            if (!valid()) { close(); return false; }
            long bytes = cursor.chargedBytes(), visits = cursor.chargedVisits();
            try {
                boolean ready = cursor.advance(rocket.getServer().getTickCount(), budget);
                lifetime.progress(rocket.getServer().getTickCount(), cursor.chargedBytes() != bytes || cursor.chargedVisits() != visits,
                        cursor.state() == RocketCargoPreparation.State.READY);
                if (cursor.state() == RocketCargoPreparation.State.DISCARDED) { close(); }
                return ready;
            }
            catch (RuntimeException failure) { close(); throw failure; }
        }
        @Override public void close() {
            owner(); if (!discarded) { discarded = true; cursor.close(); if (active == this) { active = null; } }
        }
    }
}

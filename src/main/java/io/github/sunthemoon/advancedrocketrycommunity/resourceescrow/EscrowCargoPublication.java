package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotPreparation.Work;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/** Rocket-side cargo publication only. No public issuer, proof, lease or production admission switch. */
public final class EscrowCargoPublication {
    private EscrowCargoPublication() { }
    public record Change(RocketStructureSnapshot snapshot, RocketFlightData flight, RocketEscrowSection wrapper) { }

    /** The pure one-use/thread/tick portion, shared with A0 boundary tests. */
    static final class Use {
        private final Thread owner = Thread.currentThread();
        private final int tick;
        private boolean used;
        Use(int tick) { this.tick = tick; }
        void consume(int now, BooleanSupplier exactBefore) {
            EscrowTags.require(Thread.currentThread() == owner && !used, "Cargo permit is off-thread or already consumed");
            used = true;
            EscrowTags.require(now == tick && exactBefore.getAsBoolean(), "Cargo permit lost its exact before authority");
        }
    }

    public static final class Permit {
        private final Thread owner = Thread.currentThread();
        private final Use use;
        private final RocketCargoPreparations.Ticket ticket;
        private final BooleanSupplier authority;
        private final net.minecraft.nbt.CompoundTag frame;
        private final Change change;
        private Permit(RocketCargoPreparations.Ticket ticket, BooleanSupplier authority, Change change) {
            this.ticket = ticket; this.authority = authority; this.change = change;
            frame = EscrowNativeEnvelope.frame(ticket.rocket()); use = new Use(ticket.rocket().getServer().getTickCount());
        }
        public Change consume(RocketEntity rocket) {
            EscrowTags.require(Thread.currentThread() == owner, "Cargo permit is off-thread");
            try {
                use.consume(rocket.getServer().getTickCount(), () -> rocket == ticket.rocket() && rocket.getServer().isSameThread()
                        && ticket.valid() && authority.getAsBoolean() && frame.equals(EscrowNativeEnvelope.frame(rocket)));
                return change;
            } finally { ticket.close(); }
        }
    }

    /** Apply re-prepares from BEFORE, then checks the two native envelopes in separate budgeted steps. */
    static final class Preparation {
        private final EscrowLedger root;
        private final EscrowOperation operation;
        private final RocketCargoPreparations.Ticket ticket;
        private final BooleanSupplier authority;
        private net.minecraft.nbt.CompoundTag frame;
        private Change change;
        private int phase;
        private boolean issued;
        Preparation(EscrowLedger root, EscrowOperation operation, RocketCargoPreparations.Ticket ticket, BooleanSupplier authority) {
            this.root = root; this.operation = operation; this.ticket = ticket; this.authority = authority;
        }
        /** Null means defer; a completed verifier never issues a second permit. */
        Permit issue() {
            EscrowTags.require(!issued && ticket.valid() && root.records().contains(operation) && !operation.reason().manual()
                    && operation.settlement().isEmpty() && operation.kind().cargo() && operation.nativeBinding().complete()
                    && authority.getAsBoolean(), "Cargo publication needs an acknowledged ordinary member");
            var rocket = ticket.rocket(); var cursor = ticket.cursor(); var after = cursor.after();
            var budget = RocketRuntime.cargoPreparations(rocket.getServer()).budget();
            long tick = rocket.getServer().getTickCount();
            if (!budget.claim(tick, cursor)) { return null; }
            while (phase < 3) {
                int visits = phase == 0 ? 0 : 8 * after.blocks().size();
                long bytes = phase == 0 ? 1_048_576 : 16L * 1_048_576;
                if (!budget.perform(tick, bytes, visits, false, () -> {
                    validateStep(); return new Work(bytes, visits);
                })) { return null; }
            }
            EscrowTags.require(ticket.valid() && frame.equals(EscrowNativeEnvelope.frame(rocket)) && authority.getAsBoolean(),
                    "Publication before state changed during envelope verification");
            issued = true;
            return new Permit(ticket, authority, change);
        }
        private void validateStep() {
            var rocket = ticket.rocket(); var cursor = ticket.cursor(); var endpoint = operation.rocket();
            var after = cursor.after(); var binding = operation.nativeBinding();
            boolean loading = operation.kind() == EscrowOperation.Kind.FLUID_CARGO_LOAD;
            if (phase == 0) {
                EscrowTags.require(cursor.matches(EscrowCargoPayload.decode(operation.payload()), loading), "Publication delta changed");
                EscrowTags.require(endpoint.identity().equals(rocket.getUUID()) && endpoint.logicalRocket().equals(cursor.logical())
                        && binding.snapshot().equals(after.snapshotId()) && binding.snapshotHash().equals(after.contentHash())
                        && binding.invariant().equals(EscrowInvariant.rocket(endpoint, operation.owner(), after)), "Frozen cargo digest changed");
                var before = rocket.escrowSection(); var markers = new ArrayList<>(before.markers());
                markers.add(new EscrowPrefix.Link(operation, endpoint, loading ? "DESTINATION" : "SOURCE").marker());
                var wrapper = new RocketEscrowSection(root.id(), endpoint.afterEpoch(), markers);
                var flight = rocket.flightData().orElseThrow();
                EscrowTags.require(before.epoch() == endpoint.beforeEpoch()
                        && EscrowNativeEnvelope.digest(wrapper.encode()).equals(binding.afterWrapper())
                        && endpoint.beforeSignature().equals(EscrowSignatureKind.composite(cursor.logical(), cursor.beforeHash(), flight.fuel().resourceSignature()))
                        && endpoint.afterSignature().equals(EscrowSignatureKind.composite(cursor.logical(), after.contentHash(), flight.fuel().resourceSignature())),
                        "Publication resources or revision changed");
                change = new Change(after, flight, wrapper); frame = EscrowNativeEnvelope.frame(rocket);
            } else {
                EscrowTags.require(frame.equals(EscrowNativeEnvelope.frame(rocket)), "Publication frame changed");
                var envelope = phase == 1 ? EscrowNativeEnvelope.rocket(rocket)
                        : EscrowNativeEnvelope.rocket(rocket, after, change.flight(), change.wrapper());
                String expected = phase == 1 ? (loading ? binding.destinationBefore() : binding.sourceBefore())
                        : (loading ? binding.destinationAfter() : binding.sourceAfter());
                EscrowTags.require(EscrowNativeEnvelope.digest(envelope).equals(expected), "Frozen native cargo envelope changed");
            }
            phase++;
        }
    }
}

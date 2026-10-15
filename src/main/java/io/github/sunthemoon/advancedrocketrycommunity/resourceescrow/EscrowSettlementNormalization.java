package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** One-use O1 authority for the prescribed neutral-to-bound wrapper change, and nothing else. */
public final class EscrowSettlementNormalization {
    private EscrowSettlementNormalization() { }
    public static final class Permit {
        private final EscrowSettlementIntent.Observation observed;
        private final BooleanSupplier lease;
        private boolean used;
        private Permit(EscrowSettlementIntent.Observation observed, BooleanSupplier lease) {
            this.observed = observed; this.lease = lease;
        }
        public RocketEscrowSection consume(RocketEntity rocket) {
            EscrowTags.require(!used && !rocket.level().isClientSide && rocket.getServer().isSameThread()
                    && lease.getAsBoolean(), "Expired settlement normalization permit");
            used = true;
            EscrowTags.require(RocketEscrowAudit.placement(observed.endpoint(), rocket) == EscrowReason.NONE
                    && !observed.bound() && !observed.missing() && rocket.escrowSection().equals(RocketEscrowSection.neutral())
                    && rocket.flightData().orElseThrow().fuel().resourceSignature().equals(observed.state().signature())
                    && rocket.flightData().orElseThrow().fuel().totalAmount() == observed.quantity()
                    && EscrowInvariant.rocket(rocket).equals(observed.invariantDigest()), "Changed neutral chosen survivor");
            return observed.normalizedWrapper();
        }
    }
    static boolean publish(MinecraftServer server, EscrowCoordinator coordinator, EscrowSettlementIntent intent,
            BooleanSupplier journalLease, EscrowRuntime runtime) {
        var acknowledged = coordinator.ledger();
        EscrowTags.require(server.isSameThread() && intent.ledger().equals(acknowledged.id())
                && intent.ids().stream().allMatch(id -> acknowledged.records().stream()
                .anyMatch(op -> op.id().equals(id) && op.settlement().equals(intent.encode()))), "Normalization requires acknowledged O1");
        for (int i = 0; i < intent.observations().size(); i++) {
            var chosen = intent.observations().get(i);
            if (chosen.missing() || chosen.bound() || chosen.signatureKind() != EscrowSignatureKind.FUEL_VECTOR_V1) { continue; }
            var current = EscrowSettlementObserver.observe(server, intent.ledger(), List.of(chosen.endpoint())).get(0);
            if (current.equals(chosen.checkpointed())) { continue; }
            EscrowTags.require(current.equals(chosen), "Chosen survivor changed before normalization");
            var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(chosen.endpoint().level())));
            EscrowTags.require(level != null && level.getEntity(chosen.endpoint().identity()) instanceof RocketEntity, "Chosen survivor unavailable");
            var rocket = (RocketEntity) level.getEntity(chosen.endpoint().identity());
            var permit = new Permit(chosen, () -> coordinator.ledger() == acknowledged && journalLease.getAsBoolean());
            runtime.checkpoint("O_BIND_BEFORE"); runtime.checkpoint("O_BIND_BEFORE_" + i);
            rocket.normalizeSettlementEscrow(permit);
            runtime.monitor().observed(chosen.endpoint().level(), true, rocket.chunkPosition());
            runtime.checkpoint("O_BIND_AFTER_" + i); runtime.checkpoint("O_BIND_AFTER");
            return true; // At most one wrapper publication per tick; restart repeats idempotently.
        }
        return false;
    }
}

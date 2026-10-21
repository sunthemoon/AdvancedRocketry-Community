package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Ordinary prefix projection beside, never substituted for, the settlement fuel observation. */
record EscrowReconciliationObservation(EscrowSettlementIntent.Observation settlement, EscrowPrefix.State state) {
    static EscrowReconciliationObservation of(EscrowSettlementIntent.Observation observation, String contentHash) {
        var state = observation.state(); var endpoint = observation.endpoint();
        if (state != null && EscrowSignatureKind.endpoint(endpoint) == EscrowSignatureKind.ROCKET_COMPOSITE_V1) {
            state = new EscrowPrefix.State(state.ledger(), state.epoch(),
                    EscrowSignatureKind.composite(endpoint.logicalRocket(), contentHash, state.signature()), state.markers());
        }
        return new EscrowReconciliationObservation(observation, state);
    }

    static EscrowReconciliationObservation observe(MinecraftServer server, UUID ledger, EscrowEndpoint endpoint) {
        var observation = EscrowSettlementObserver.observe(server, ledger, java.util.List.of(endpoint)).get(0);
        String hash = null;
        if (!observation.missing() && EscrowSignatureKind.endpoint(endpoint) == EscrowSignatureKind.ROCKET_COMPOSITE_V1) {
            var level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.tryParse(endpoint.level())));
            hash = ((RocketEntity) level.getEntity(endpoint.identity())).snapshot().orElseThrow().contentHash();
        }
        return of(observation, hash);
    }
}

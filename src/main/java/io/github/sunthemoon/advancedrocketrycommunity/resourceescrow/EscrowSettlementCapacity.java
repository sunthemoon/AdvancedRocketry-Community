package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Pre-L1 reservations are encoded with the real codecs, including O1 in every connected member. */
final class EscrowSettlementCapacity {
    private EscrowSettlementCapacity() { }
    static void preflight(EscrowLedger prospective) {
        long reservedBytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.encode(prospective));
        int reservedReceipts = prospective.tombstones().size();
        var visited = new java.util.HashSet<UUID>();
        for (var selected : prospective.records()) {
            if (!visited.add(selected.id())) { continue; }
            var component = EscrowSettlementIntent.component(prospective.records(), selected.id());
            component.forEach(op -> visited.add(op.id()));
            if (component.stream().anyMatch(op -> !op.settlement().isEmpty())) { continue; }
            var references = new LinkedHashMap<EscrowEndpoint.Key, EscrowEndpoint>();
            component.forEach(op -> { references.put(op.source().key(), op.source()); references.put(op.destination().key(), op.destination()); });
            var chains = EscrowPrefix.chains(component); var observations = new ArrayList<EscrowSettlementIntent.Observation>();
            for (var endpoint : references.values()) {
                var chain = chains.get(endpoint.key()); var last = chain.get(chain.size() - 1);
                var markers = chain.stream().map(EscrowPrefix.Link::marker).toList();
                String invariant = component.stream().filter(op -> op.destination().key().equals(endpoint.key())).map(op -> op.nativeBinding().invariant())
                        .findFirst().orElseGet(() -> EscrowInvariant.reference(endpoint));
                observations.add(new EscrowSettlementIntent.Observation(endpoint,
                        new EscrowPrefix.State(prospective.id(), last.endpoint().afterEpoch(), last.endpoint().afterSignature(), markers), true,
                        Long.MAX_VALUE, EscrowSignatureKind.endpoint(endpoint), invariant));
            }
            // NUL uses two modified-UTF bytes per one UTF-8 byte: the worst allowed actor-name encoding.
            var hypothetical = prospective.replace(prospective.records().stream().map(op -> component.contains(op)
                    && !op.reason().manual() ? op.withReason(EscrowReason.OTHER_STATE) : op).toList(), prospective.floors(), prospective.tombstones());
            var preview = EscrowSettlementIntent.preview(hypothetical, selected.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    new UUID(0, 1), "\0".repeat(128), 0, observations);
            var intent = preview.confirm(preview.actor(), preview.digest(), 0, observations);
            for (var member : hypothetical.records().stream().filter(op -> intent.ids().contains(op.id())).toList()) {
                var encoded = EscrowCodec.operation(member.settle(intent));
                reservedBytes += io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(encoded)
                        - io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.operation(member));
                EscrowSettlementTombstones.create(member, intent);
                if (prospective.tombstones().stream().noneMatch(tag -> tag.getUUID("op_id").equals(member.id()))) {
                    reservedReceipts++; reservedBytes += 2_048;
                }
            }
        }
        // All components, both full tables, and terminal cleanup entries remain charged together.
        EscrowTags.require(reservedReceipts <= 64 && reservedBytes + (64 - prospective.armedFlights().size()) * 1_024L
                + (64 - prospective.rocketAuthorities().size()) * 1_024L <= EscrowLimits.ROOT_BYTES,
                "Settlement/flight root reservation exhausted");
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Pre-L1 reservations are encoded with the real codecs, including O1 in every connected member. */
final class EscrowSettlementCapacity {
    // Admission only. Persisted sections/chains retain the eight-marker decode bound.
    static final int ADMISSION_PER_ROCKET = 5;
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
                var markers = new ArrayList<>(chain.stream().map(EscrowPrefix.Link::marker).toList());
                long epoch = last.endpoint().afterEpoch();
                if (EscrowSignatureKind.endpoint(endpoint) == EscrowSignatureKind.FUEL_VECTOR_V1) {
                    // Charge the whole admission allowance, including retired markers not yet pruned.
                    // UUIDs/longs have fixed encoded widths; these are sizing observations, never proofs.
                    for (int i = markers.size(); i < ADMISSION_PER_ROCKET; i++) {
                        UUID id = new UUID(-1, i);
                        while (contains(markers, id)) { id = new UUID(id.getMostSignificantBits() - 1, i); }
                        markers.add(new EscrowPrefix.Marker(id, i + 1, "DESTINATION", i + 1));
                    }
                    epoch = Math.max(epoch, markers.size());
                    for (int i = 0; i < markers.size(); i++) {
                        markers.set(i, new EscrowPrefix.Marker(markers.get(i).operation(), i + 1, "DESTINATION", epoch - markers.size() + i + 1));
                    }
                }
                String invariant = component.stream().filter(op -> op.destination().key().equals(endpoint.key())).map(op -> op.nativeBinding().invariant())
                        .findFirst().orElseGet(() -> EscrowInvariant.reference(endpoint));
                observations.add(new EscrowSettlementIntent.Observation(endpoint,
                        new EscrowPrefix.State(prospective.id(), epoch, last.endpoint().afterSignature(), markers), true,
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
    static void requireAdmission(EscrowLedger prospective, EscrowOperation candidate, RocketEscrowSection observed) {
        var markers = new java.util.HashSet<UUID>();
        prospective.records().stream().filter(op -> op.guard().rocket().equals(candidate.guard().rocket()))
                .map(EscrowOperation::id).forEach(markers::add);
        if (candidate.nativeBinding().kind() == EscrowSignatureKind.FUEL_VECTOR_V1) {
            EscrowTags.require(observed != null && (observed.ledger() == null || prospective.id().equals(observed.ledger())),
                    "Native admission needs the current endpoint marker section");
            observed.markers().stream().map(EscrowPrefix.Marker::operation).forEach(markers::add);
        }
        EscrowTags.require(markers.size() <= ADMISSION_PER_ROCKET, "Rocket settlement admission limit exceeded");
    }
    private static boolean contains(List<EscrowPrefix.Marker> markers, UUID id) {
        return markers.stream().anyMatch(marker -> marker.operation().equals(id));
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Admission proves its component with the real codecs, then reserves every unsettled member's ceiling. */
final class EscrowSettlementCapacity {
    // Admission only. Persisted sections/chains retain the eight-marker decode bound.
    static final int ADMISSION_PER_ROCKET = 5;
    // One new floor-retirement operation, its receipt and the named-floor top-up.
    static final long FLOOR_RETIREMENT_BYTES = EscrowLimits.OP_BYTES + 2_048L + 1_024L;
    static final String FLOOR_FULL = "Escrow is full; retry floor retirement after other settlements or pruning.";
    private EscrowSettlementCapacity() { }
    static void preflight(EscrowLedger prospective) {
        preflight(prospective, null);
    }
    static void preflight(EscrowLedger prospective, UUID candidate) {
        EscrowFloors.preflight(prospective);
        long reservedBytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.encode(prospective));
        reservedBytes += EscrowFloors.reservedGrowth(prospective);
        int reservedReceipts = prospective.tombstones().size();
        var visited = new java.util.HashSet<UUID>();
        for (var selected : prospective.records()) {
            if (!visited.add(selected.id())) { continue; }
            var component = EscrowSettlementIntent.component(prospective.records(), selected.id());
            component.forEach(op -> visited.add(op.id()));
            if (component.stream().anyMatch(op -> !op.settlement().isEmpty())) {
                for (var op : component) {
                    if (prospective.tombstones().stream().noneMatch(tag -> tag.getUUID("op_id").equals(op.id()))) {
                        reservedReceipts++; reservedBytes += 2_048;
                    }
                }
                continue;
            }
            if (candidate != null && component.stream().anyMatch(op -> op.id().equals(candidate))) {
                preview(prospective, component, selected.id(), true);
            } else if (candidate == null && component.stream().noneMatch(op -> op.reason() == EscrowReason.RELOCATED_ENDPOINT
                    || op.reason() == EscrowReason.FLOOR_CONFLICT)) {
                // Retain the existing non-admission codec checks when a preview is eligible.
                preview(prospective, component, selected.id(), false);
            }
            // A later sticky repair reason must not increase the charge. Preview is an
            // admission eligibility check, never a discount on this component's ceiling.
            var fixed = fixed(prospective, component);
            reservedBytes += fixed.bytes(); reservedReceipts += fixed.receipts();
        }
        // Fill admission always leaves room for one new retirement, even with no retained floor.
        // Ordinary writes may consume that headroom; it is not an autosave/load restriction.
        int headroom = candidate == null ? 0 : 1;
        if (prospective.records().size() + headroom > EscrowLimits.OPERATIONS
                || reservedReceipts + headroom > EscrowLimits.TOMBSTONES
                || reservedBytes + headroom * FLOOR_RETIREMENT_BYTES
                + (64 - prospective.armedFlights().size()) * 1_024L
                + (64 - prospective.rocketAuthorities().size()) * 1_024L > EscrowLimits.ROOT_BYTES) {
            throw new ReservationFull("Settlement/flight root reservation exhausted");
        }
    }
    /** Shared by operator preview and O1; all checks precede admission/writer mutation. */
    static EscrowLedger floorRetirement(EscrowLedger root, EscrowSettlementIntent intent, long sequence, long tick) {
        var tombstones = root.tombstones();
        long receipts = tombstones.size() + root.records().stream().filter(op -> tombstones.stream()
                .noneMatch(tag -> tag.getUUID("op_id").equals(op.id()))).count();
        if (root.records().size() >= EscrowLimits.OPERATIONS || receipts >= EscrowLimits.TOMBSTONES) {
            throw new ReservationFull(FLOOR_FULL);
        }
        var candidate = root.append(List.of(intent.floorOperation(sequence, tick)));
        try { preflight(candidate); }
        catch (ReservationFull full) { throw new ReservationFull(FLOOR_FULL); }
        return candidate;
    }
    private static final class ReservationFull extends IllegalArgumentException {
        private ReservationFull(String message) { super(message); }
    }
    private static void preview(EscrowLedger prospective, List<EscrowOperation> component, UUID selected, boolean admission) {
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
        EscrowSettlementIntent preview;
        try {
            preview = EscrowSettlementIntent.preview(hypothetical, selected, EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    new UUID(0, 1), "\0".repeat(128), 0, observations);
        } catch (IllegalArgumentException ineligible) {
            if (admission) { throw ineligible; }
            return; // Existing ineligible components still receive the fixed charge, never a discount.
        }
        var intent = preview.confirm(preview.actor(), preview.digest(), 0, observations);
        for (var member : hypothetical.records().stream().filter(op -> intent.ids().contains(op.id())).toList()) {
            EscrowCodec.operation(member.settle(intent));
            EscrowSettlementTombstones.create(member, intent);
        }
    }
    static void requireAdmission(EscrowLedger prospective, EscrowOperation candidate, RocketEscrowSection observed) {
        var markers = new java.util.HashSet<UUID>();
        prospective.records().stream().filter(op -> op.guard().rocket().equals(candidate.guard().rocket()))
                .map(EscrowOperation::id).forEach(markers::add);
        if (candidate.nativeBinding().kind() == EscrowSignatureKind.FUEL_VECTOR_V1) {
            EscrowTags.require(observed != null && (observed.ledger() == null || prospective.id().equals(observed.ledger())),
                    "Native admission needs the current endpoint marker section");
            EscrowTags.require(EscrowNativeEnvelope.digest(observed.encode()).equals(candidate.nativeBinding().beforeWrapper()),
                    "Native admission marker section differs from the captured before wrapper");
            observed.markers().stream().map(EscrowPrefix.Marker::operation).forEach(markers::add);
        }
        EscrowTags.require(markers.size() <= ADMISSION_PER_ROCKET, "Rocket settlement admission limit exceeded");
    }
    private record Reservation(long bytes, int receipts) { }
    private static Reservation fixed(EscrowLedger root, List<EscrowOperation> component) {
        long growth = 0; int receipts = 0;
        for (var op : component) {
            growth += EscrowLimits.OP_BYTES - bytes(EscrowCodec.operation(op));
            if (root.tombstones().stream().noneMatch(tag -> tag.getUUID("op_id").equals(op.id()))) {
                receipts++; growth += 2_048;
            }
        }
        return new Reservation(growth, receipts);
    }
    private static boolean contains(List<EscrowPrefix.Marker> markers, UUID id) {
        return markers.stream().anyMatch(marker -> marker.operation().equals(id));
    }
    private static int bytes(net.minecraft.nbt.CompoundTag tag) {
        return io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(tag);
    }
}

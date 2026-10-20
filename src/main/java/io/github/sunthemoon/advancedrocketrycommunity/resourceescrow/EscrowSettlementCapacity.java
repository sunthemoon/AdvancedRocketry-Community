package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/** Admission proves its component with the real codecs, then reserves every unsettled member's ceiling. */
final class EscrowSettlementCapacity {
    // Admission only. Persisted sections/chains retain the eight-marker decode bound.
    static final int ADMISSION_PER_ROCKET = 5;
    // Cargo excludes retained markers and every other operation on its rocket at admission.
    static final int CARGO_ADMISSION_PER_ROCKET = 1;
    // One new floor-retirement operation, its receipt and the named-floor top-up.
    static final long FLOOR_RETIREMENT_BYTES = EscrowLimits.OP_BYTES + 2_048L + 1_024L;
    static final String FLOOR_FULL = "Escrow is full; retry floor retirement after other settlements or pruning.";
    private EscrowSettlementCapacity() { }
    static void preflight(EscrowLedger prospective) {
        preflight(prospective, null);
    }
    static void preflight(EscrowLedger prospective, UUID candidate) {
        EscrowFloors.preflight(prospective);
        var visited = new java.util.HashSet<UUID>();
        for (var selected : prospective.records()) {
            if (!visited.add(selected.id())) { continue; }
            var component = EscrowSettlementIntent.component(prospective.records(), selected.id());
            component.forEach(op -> visited.add(op.id()));
            if (component.stream().anyMatch(op -> !op.settlement().isEmpty())) {
                continue;
            }
            if (candidate != null && component.stream().anyMatch(op -> op.id().equals(candidate))) {
                preview(prospective, component, selected.id(), true);
            } else if (candidate == null && component.stream().noneMatch(op -> op.reason() == EscrowReason.RELOCATED_ENDPOINT
                    || op.reason() == EscrowReason.FLOOR_CONFLICT)) {
                // Retain the existing non-admission codec checks when a preview is eligible.
                preview(prospective, component, selected.id(), false);
            }
        }
        var reservation = reservation(prospective);
        // Fill admission always leaves room for one new retirement, even with no retained floor.
        // Only new work consumes that headroom; this is not an autosave/load restriction.
        int headroom = candidate == null ? 0 : 1;
        if (prospective.records().size() + headroom > EscrowLimits.OPERATIONS
                || reservation.receipts() + headroom > EscrowLimits.TOMBSTONES
                || reservation.bytes() + headroom * FLOOR_RETIREMENT_BYTES > EscrowLimits.ROOT_BYTES) {
            throw new ReservationFull("Settlement/flight root reservation exhausted");
        }
    }
    /** Shared by operator preview and O1; all checks precede admission/writer mutation. */
    static EscrowLedger floorRetirement(EscrowLedger root, EscrowSettlementIntent intent, long sequence, long tick) {
        EscrowSettlementIntent.requireReceiptRoom(root, intent.ids());
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
            if (EscrowAdapterIds.nativeRocket(endpoint)) {
                int markerLimit = markerLimit(component, endpoint.logicalRocket());
                // Charge the whole admission allowance, including retired markers not yet pruned.
                // UUIDs/longs have fixed encoded widths; these are sizing observations, never proofs.
                for (int i = markers.size(); i < markerLimit; i++) {
                    UUID id = new UUID(-1, i);
                    while (contains(markers, id)) { id = new UUID(id.getMostSignificantBits() - 1, i); }
                    markers.add(new EscrowPrefix.Marker(id, i + 1, "DESTINATION", i + 1));
                }
                epoch = Math.max(epoch, markers.size());
                for (int i = 0; i < markers.size(); i++) {
                    markers.set(i, new EscrowPrefix.Marker(markers.get(i).operation(), i + 1, "DESTINATION", epoch - markers.size() + i + 1));
                }
            }
            String invariant = component.stream().filter(op -> op.rocket().key().equals(endpoint.key())).map(op -> op.nativeBinding().invariant())
                    .findFirst().orElseGet(() -> EscrowInvariant.reference(endpoint));
            observations.add(new EscrowSettlementIntent.Observation(endpoint,
                    new EscrowPrefix.State(prospective.id(), epoch, last.endpoint().afterSignature(), markers), true,
                    EscrowSignatureKind.settlement(endpoint) == EscrowSignatureKind.ITEM_BUFFER_V1 ? 0 : Long.MAX_VALUE,
                    EscrowSignatureKind.settlement(endpoint), invariant));
        }
        // New actor names are printable ASCII: each character uses exactly one modified-UTF byte.
        var hypothetical = prospective.replace(prospective.records().stream().map(op -> component.contains(op)
                && !op.reason().manual() ? op.withReason(op.kind().cargo() ? EscrowReason.JOURNAL_GUARD_MISMATCH : EscrowReason.OTHER_STATE) : op).toList(),
                prospective.floors(), prospective.tombstones());
        EscrowSettlementIntent preview;
        try {
            preview = EscrowSettlementIntent.preview(hypothetical, selected, EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    new UUID(0, 1), "A".repeat(EscrowSettlementIntent.NEW_ACTOR_NAME_LIMIT), 0, observations);
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
        if (EscrowAdapterIds.nativeRocket(candidate.rocket())) {
            EscrowTags.require(observed != null && (observed.ledger() == null || prospective.id().equals(observed.ledger())),
                    "Native admission needs the current endpoint marker section");
            EscrowTags.require(EscrowNativeEnvelope.digest(observed.encode()).equals(candidate.nativeBinding().beforeWrapper()),
                    "Native admission marker section differs from the captured before wrapper");
            observed.markers().stream().map(EscrowPrefix.Marker::operation).forEach(markers::add);
        }
        int markerLimit = markerLimit(EscrowSettlementIntent.component(prospective.records(), candidate.id()), candidate.guard().rocket());
        EscrowTags.require(markers.size() <= markerLimit, "Rocket settlement admission limit exceeded");
    }
    private static int markerLimit(List<EscrowOperation> component, UUID logicalRocket) {
        // Cross-kind endpoint UUID aliases can join records on different logical rockets.
        return component.stream().anyMatch(op -> op.kind().cargo() && op.guard().rocket().equals(logicalRocket))
                ? CARGO_ADMISSION_PER_ROCKET : ADMISSION_PER_ROCKET;
    }
    private record Reservation(long bytes, int receipts) { }
    static long reservedBytes(EscrowLedger root) { return reservation(root).bytes(); }
    private static Reservation reservation(EscrowLedger root) {
        long total = bytes(EscrowCodec.encode(root)) + EscrowFloors.reservedGrowth(root);
        var receipts = root.tombstones();
        var receiptIds = receipts.stream().map(tag -> tag.getUUID("op_id")).collect(java.util.stream.Collectors.toSet());
        for (var receipt : receipts) { total += 2_048L - listBytes(EscrowReceiptCodec.encode(receipt)); }
        for (var op : root.records()) {
            // O1 freezes the complete operation permanently; until then reserve its ceiling.
            if (op.settlement().isEmpty()) { total += EscrowLimits.OP_BYTES - listBytes(EscrowCodec.operation(op)); }
            if (receiptIds.add(op.id())) { total += 2_048; }
        }
        // Occupied bytes are included in, not added to, each pre-reserved table slot.
        total += 128 * 1_024L;
        for (var arm : root.armedFlights()) { total -= listBytes(arm.encode()); }
        for (var head : root.rocketAuthorities()) { total -= listBytes(head.encode()); }
        return new Reservation(total, receiptIds.size());
    }
    private static int listBytes(net.minecraft.nbt.CompoundTag tag) {
        // NbtIo's standalone compound adds a type byte and an empty UTF name;
        // a compound inside a list has neither. Keep the root/list framing once.
        return bytes(tag) - 3;
    }
    private static boolean contains(List<EscrowPrefix.Marker> markers, UUID id) {
        return markers.stream().anyMatch(marker -> marker.operation().equals(id));
    }
    private static int bytes(net.minecraft.nbt.CompoundTag tag) {
        return io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(tag);
    }
}

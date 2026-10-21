package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/** C3-C6: one frozen retirement request, with joint tokens for complete current endpoint frontiers. */
final class EscrowReconciliationCheckpoint implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    final EscrowLedger root;
    final Set<UUID> members;
    final Map<EscrowEndpoint.Key, EscrowSettlementIntent.Observation> ends;
    final Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers;
    private final Map<EscrowEndpoint.Key, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot> witnesses;
    private final Map<EscrowEndpoint.Key, EscrowCargoReadback> cargoReadbacks = new HashMap<>();
    final BooleanSupplier journalLease;
    final Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs = new HashMap<>();
    boolean busy;
    private boolean closed;

    EscrowReconciliationCheckpoint(EscrowLedger root, Set<UUID> members,
            List<EscrowSettlementIntent.Observation> observations, BooleanSupplier journalLease) {
        this(root, members, observations, journalLease, observations.stream().filter(end -> !end.missing())
                .collect(java.util.stream.Collectors.toMap(end -> end.endpoint().key(), EscrowSettlementIntent.Observation::state, (a, b) -> a)));
    }

    EscrowReconciliationCheckpoint(EscrowLedger root, Set<UUID> members,
            List<EscrowSettlementIntent.Observation> observations, BooleanSupplier journalLease,
            Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers) {
        this(root, members, observations, journalLease, frontiers, Map.of());
    }

    EscrowReconciliationCheckpoint(EscrowLedger root, Set<UUID> members,
            List<EscrowSettlementIntent.Observation> observations, BooleanSupplier journalLease,
            Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers,
            Map<EscrowEndpoint.Key, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot> witnesses) {
        this.root = root; this.members = Set.copyOf(members); this.journalLease = journalLease;
        this.witnesses = Map.copyOf(witnesses);
        EscrowTags.require(!members.isEmpty() && journalLease.getAsBoolean(), "Checkpoint needs members and a checked journal lease");
        var affected = root.records().stream().filter(op -> members.contains(op.id()))
                .flatMap(op -> java.util.stream.Stream.of(op.source().key(), op.destination().key()))
                .collect(java.util.stream.Collectors.toSet());
        var collected = new HashMap<EscrowEndpoint.Key, EscrowSettlementIntent.Observation>();
        for (var observation : observations) {
            EscrowTags.require(collected.putIfAbsent(observation.endpoint().key(), observation) == null,
                    "Checkpoint has duplicate physical endpoints");
        }
        ends = Map.copyOf(collected);
        EscrowTags.require(affected.equals(ends.keySet()) && ends.values().stream().allMatch(end -> !end.missing() && end.bound()),
                "Checkpoint must cover every affected native endpoint");
        this.frontiers = Map.copyOf(frontiers);
        EscrowTags.require(ends.keySet().equals(frontiers.keySet())
                && EscrowReconciliationValidation.retirementReady(root, members, frontiers), "Later acknowledged member is not applied");
        var chains = EscrowPrefix.chains(root.records());
        ends.values().forEach(end -> requireBinding(chains.get(end.endpoint().key()), end));
    }

    private static void requireBinding(List<EscrowPrefix.Link> chain, EscrowSettlementIntent.Observation end) {
        for (var link : chain) {
            var op = link.operation(); var binding = op.nativeBinding();
            binding.requireOperation(op.owner(), op.source(), op.destination());
            EscrowTags.require(binding.complete() && (binding.kind() == EscrowSignatureKind.FUEL_VECTOR_V1
                    || binding.kind() == EscrowSignatureKind.ROCKET_COMPOSITE_V1 && op.kind().fluid()),
                    "Checkpoint lacks the native mutation binding");
            if (end.endpoint().kind() == EscrowEndpoint.Kind.ENTITY) {
                // The versioned invariant commits physical/logical/type/owner/snapshot identity.
                EscrowTags.require(end.signatureKind() == EscrowSignatureKind.FUEL_VECTOR_V1
                        && binding.invariant().equals(end.invariantDigest()), "Checkpoint native identity changed");
            } else {
                // The registered loader tank, or the gated chest fixture; the observation already pins kind to endpoint.
                EscrowTags.require(end.signatureKind() == EscrowSignatureKind.FLUID_TANK_V1
                        || end.signatureKind() == EscrowSignatureKind.FIXTURE_RESOURCE_V1, "Unsupported source projection");
            }
        }
    }

    /** Immutable slot predicate; no live world access or historical wrapper/envelope digests. */
    Predicate<CompoundTag> validator(EscrowEndpoint.Key key) {
        var expected = ends.get(key);
        EscrowTags.require(expected != null, "Unknown checkpoint endpoint");
        var chain = List.copyOf(EscrowPrefix.chains(root.records()).get(key));
        if (chain.stream().anyMatch(link -> link.operation().kind().cargo())) {
            return cargoReadbacks.computeIfAbsent(key, ignored -> new EscrowCargoReadback(root.id(),
                    new EscrowReconciliationObservation(expected, frontiers.get(key)), witnesses.get(key)));
        }
        return slot -> {
            try {
                var observed = EscrowSettlementDisk.ordinary(slot, expected.endpoint(), root.id());
                if (!expected.equals(observed.settlement()) || !frontiers.get(key).equals(observed.state())
                        || EscrowPrefix.classify(root.id(), chain, observed.state())
                        != EscrowPrefix.Classification.AFTER) { return false; }
                requireBinding(chain, observed.settlement()); return true;
            } catch (IllegalArgumentException | IllegalStateException | java.util.NoSuchElementException invalid) { return false; }
        };
    }

    List<EscrowBarrier.Target> targets(EscrowEndpoint endpoint) {
        return ends.values().stream().filter(end -> EscrowBarrier.sameSlot(endpoint, end.endpoint()))
                .map(end -> new EscrowBarrier.Target(end.endpoint(), frontiers.get(end.endpoint().key()), validator(end.endpoint().key()))).toList();
    }

    void accept(List<EscrowBarrier.Target> targets, EscrowBarrier.JointResult result) {
        EscrowTags.require(Thread.currentThread() == owner, "Checkpoint confirmation requires owner thread");
        var keys = targets.stream().map(target -> target.endpoint().key()).collect(java.util.stream.Collectors.toSet());
        if (closed || !journalLease.getAsBoolean() || !result.confirmed() || !keys.equals(result.proofs().keySet())
                || keys.stream().anyMatch(key -> cargoReadbacks.containsKey(key) && !cargoReadbacks.get(key).confirmed())
                || result.proofs().entrySet().stream().anyMatch(entry -> !frontiers.get(entry.getKey()).equals(entry.getValue().frontier()))) {
            result.proofs().values().forEach(EscrowBarrier.Proof::release); return;
        }
        result.proofs().forEach((key, proof) -> { var old = proofs.put(key, proof); if (old != null) { old.release(); } });
    }

    EscrowReason readbackReason(List<EscrowBarrier.Target> targets, EscrowBarrier.JointResult result) {
        EscrowTags.require(Thread.currentThread() == owner, "Checkpoint confirmation requires owner thread");
        return result.confirmed() && targets.stream().anyMatch(target -> cargoReadbacks.containsKey(target.endpoint().key())
                && !cargoReadbacks.get(target.endpoint().key()).confirmed()) ? EscrowReason.BARRIER_UNSTABLE : result.reason();
    }

    void discardInvalid() {
        proofs.entrySet().removeIf(entry -> {
            if (!closed && journalLease.getAsBoolean() && entry.getValue().valid()) { return false; }
            entry.getValue().release(); return true;
        });
    }

    boolean proved() {
        return !closed && journalLease.getAsBoolean() && proofs.keySet().equals(ends.keySet())
                && proofs.values().stream().allMatch(EscrowBarrier.Proof::valid)
                && cargoReadbacks.values().stream().allMatch(EscrowCargoReadback::confirmed);
    }

    @Override public void close() { closed = true; proofs.values().forEach(EscrowBarrier.Proof::release); proofs.clear(); }
}

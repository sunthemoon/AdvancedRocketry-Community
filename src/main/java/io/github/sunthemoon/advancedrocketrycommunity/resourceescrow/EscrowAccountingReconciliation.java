package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Ordinary accounting checks supplement native signatures; a failed check never rewrites a baseline. */
final class EscrowAccountingReconciliation {
    private EscrowAccountingReconciliation() { }

    static boolean legacyHeld(EscrowLedger root, EscrowOperation op) { return root.schema() == 3 && op.legacyFluid(); }

    static boolean legacyComponent(EscrowLedger root, Collection<EscrowOperation> pending, EscrowOperation candidate) {
        if (root.schema() != 3 || !candidate.kind().fluid()) { return false; }
        var records = new ArrayList<>(root.records()); records.addAll(pending); records.add(candidate);
        return EscrowSettlementIntent.component(records, candidate.id()).stream().anyMatch(op -> !op.id().equals(candidate.id()) && op.legacyFluid());
    }

    static EscrowFluidAccount.Baseline baseline(EscrowLedger root, EscrowOperation op) {
        return root.fluidAccounts().stream().filter(row -> row.operation().equals(op.id())).findFirst().orElseThrow().baseline();
    }

    static void requireApply(EscrowLedger root, EscrowOperation op, EscrowEndpoint endpoint,
            EscrowFluidAccount.Vector before, EscrowFluidAccount.Vector after) {
        if (root.schema() != 3 || !op.kind().fluid()) { return; }
        if (op.legacyFluid()) { throw new EscrowReconciliationView.Refusal(EscrowReason.UNKNOWN_BASELINE); }
        var baseline = baseline(root, op); boolean source = endpoint.key().equals(op.source().key());
        if (!(source ? baseline.sourceBefore() : baseline.destinationBefore()).equals(before)
                || !(source ? baseline.sourceAfter() : baseline.destinationAfter()).equals(after)) { mismatch(); }
    }

    static void requireApply(EscrowLedger root, EscrowOperation op, EscrowEndpoint endpoint,
            net.minecraft.nbt.CompoundTag before, net.minecraft.nbt.CompoundTag after) {
        if (root.schema() != 3 || !op.kind().fluid()) { return; }
        if (op.legacyFluid()) { throw new EscrowReconciliationView.Refusal(EscrowReason.UNKNOWN_BASELINE); }
        requireApply(root, op, endpoint, EscrowAccountingCapture.nativeVector(op, endpoint, before),
                EscrowAccountingCapture.nativeVector(op, endpoint, after));
    }

    /** The same scope is used on every endpoint chain; fuel covers both banks, cargo only its cursor. */
    private static String scope(EscrowOperation op, EscrowEndpoint endpoint) {
        return endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY ? "tank"
                : op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? "fuel"
                : EscrowCargoPayload.validateStructure(op.payload()).toString();
    }

    static Map<EscrowEndpoint.Key, Map<UUID, EscrowFluidAccount.Vector>> capture(EscrowReconciliationView view) {
        var values = new HashMap<EscrowEndpoint.Key, Map<UUID, EscrowFluidAccount.Vector>>();
        for (var entry : view.ends.entrySet()) {
            var end = entry.getValue(); var operations = view.members.stream().filter(op -> op.source().key().equals(entry.getKey())
                    || op.destination().key().equals(entry.getKey())).toList();
            var tank = end.rocket() != null ? null : EscrowLoaderEndpoint.is(end.reference())
                    ? EscrowLoaderEndpoint.root(end.envelope()).tank().encode()
                    : EscrowFluidCargoEndpoint.is(end.reference()) ? EscrowFluidCargoEndpoint.root(end.envelope()).tank().encode() : null;
            values.put(entry.getKey(), EscrowAccountingCapture.vectors(operations, end.reference(), tank,
                    end.rocket() == null ? null : end.rocket().flightData().orElseThrow().fuel(),
                    end.rocket() == null ? null : end.rocket().snapshot().orElseThrow()));
        }
        return Map.copyOf(values);
    }

    /** Validate continuity, then normalize the captured frontier back to each member's exact prefix position. */
    static boolean matches(EscrowLedger root, Set<UUID> ids, Map<EscrowEndpoint.Key, EscrowPrefix.State> states,
            Map<EscrowEndpoint.Key, Map<UUID, EscrowFluidAccount.Vector>> vectors, boolean complete) {
        if (root.schema() != 3) { return true; }
        var chains = EscrowPrefix.chains(root.records());
        for (var op : root.records()) {
            if (!ids.contains(op.id()) || !op.kind().fluid()) { continue; }
            if (op.legacyFluid()) { return false; }
            var baseline = baseline(root, op);
            var normalized = new ArrayList<EscrowFluidAccount.Vector>();
            boolean both = true;
            for (var endpoint : List.of(op.source(), op.destination())) {
                var chain = chains.get(endpoint.key()); var state = states.get(endpoint.key());
                if (state == null || EscrowPrefix.classify(root.id(), chain, state) == EscrowPrefix.Classification.OTHER) { return false; }
                var vector = vectors.getOrDefault(endpoint.key(), Map.of()).get(op.id());
                if (vector == null) { return false; }
                boolean source = endpoint.key().equals(op.source().key());
                int own = -1;
                for (int i = 0; i < chain.size(); i++) { if (chain.get(i).operation().id().equals(op.id())) { own = i; break; } }
                boolean applied = own >= 0 && own < state.markers().size(); both &= applied;
                var deltas = new ArrayList<EscrowFluidAccount.Delta>();
                for (int i = 0; i < chain.size(); i++) {
                    var link = chain.get(i); var member = link.operation();
                    if (!member.kind().fluid() || !scope(op, endpoint).equals(scope(member, link.endpoint()))) { continue; }
                    if (member.legacyFluid()) { return false; }
                    var other = baseline(root, member); boolean otherSource = link.role().equals("SOURCE");
                    if (i > own && i < state.markers().size()) {
                        deltas.add(otherSource ? other.sourceDelta() : other.destinationDelta());
                    } else if (i < own && i >= state.markers().size()) {
                        var delta = otherSource ? other.sourceDelta() : other.destinationDelta();
                        deltas.add(new EscrowFluidAccount.Delta(delta.key(), -delta.amount()));
                    }
                    // Numeric continuity is checked independently of the native signature continuity.
                    for (int j = i - 1; j >= 0; j--) {
                        var previous = chain.get(j); var prior = previous.operation();
                        if (!prior.kind().fluid() || !scope(op, endpoint).equals(scope(prior, previous.endpoint()))) { continue; }
                        if (prior.legacyFluid()) { return false; }
                        var preceding = baseline(root, prior);
                        if (!(previous.role().equals("SOURCE") ? preceding.sourceAfter() : preceding.destinationAfter())
                                .equals(otherSource ? other.sourceBefore() : other.destinationBefore())) { return false; }
                        break;
                    }
                }
                var result = EscrowFluidAccount.withoutLater(vector, deltas);
                if (!result.ok() || !(source ? applied ? baseline.sourceAfter() : baseline.sourceBefore()
                        : applied ? baseline.destinationAfter() : baseline.destinationBefore()).equals(result.value())) { return false; }
                normalized.add(result.value());
            }
            var application = EscrowFluidAccount.classify(baseline, normalized.get(0), normalized.get(1));
            if (application.isEmpty() || complete && (!both || application.get() != EscrowFluidAccount.Application.BOTH)) { return false; }
        }
        return true;
    }

    static void mismatch() { throw new EscrowReconciliationView.Refusal(EscrowReason.ACCOUNTING_MISMATCH); }
}

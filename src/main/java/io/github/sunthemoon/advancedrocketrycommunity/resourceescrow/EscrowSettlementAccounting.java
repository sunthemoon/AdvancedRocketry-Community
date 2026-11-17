package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** O1 projections and O2/O4 comparisons. A frozen choice is never recalculated as a new decision. */
final class EscrowSettlementAccounting {
    private EscrowSettlementAccounting() { }

    static EscrowFluidAccount.UnitRef unit(EscrowOperation op, EscrowEndpoint endpoint) {
        String scope = endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY ? "tank"
                : op.kind() == EscrowOperation.Kind.FLUID_PROPULSION_FILL ? "fuel"
                : op.kind().fluid() ? EscrowCargoPayload.validateStructure(op.payload()).toString() : "item";
        return new EscrowFluidAccount.UnitRef(endpoint.key(), scope);
    }

    static List<EscrowFluidAccount.Row> project(EscrowLedger root, List<EscrowOperation> members,
            List<EscrowSettlementIntent.Observation> observations,
            Map<EscrowEndpoint.Key, Map<UUID, EscrowFluidAccount.Vector>> vectors) {
        var accounts = new HashMap<UUID, EscrowFluidAccount.Row>();
        root.fluidAccounts().forEach(row -> accounts.put(row.operation(), row));
        var model = new ArrayList<EscrowFluidAccount.Member>();
        var finals = new HashMap<EscrowFluidAccount.UnitRef, Optional<EscrowFluidAccount.Vector>>();
        for (var op : members) {
            var row = accounts.get(op.id());
            model.add(new EscrowFluidAccount.Member(op.id(), op.sequence(), unit(op, op.source()), unit(op, op.destination()),
                    row == null ? null : row.baseline(), op.legacyFluid()));
            if (row == null) { continue; }
            for (var endpoint : List.of(op.source(), op.destination())) {
                var observation = observations.stream().filter(o -> o.endpoint().key().equals(endpoint.key())).findFirst().orElseThrow();
                var vector = vectors.getOrDefault(endpoint.key(), Map.of()).get(op.id());
                EscrowTags.require(observation.missing() || vector != null, "Missing typed settlement observation");
                var value = observation.missing() ? Optional.<EscrowFluidAccount.Vector>empty() : Optional.of(vector);
                var previous = finals.putIfAbsent(unit(op, endpoint), value);
                EscrowTags.require(previous == null || previous.equals(value), "Split typed settlement observation");
            }
        }
        var result = EscrowFluidAccount.settle(model, finals);
        EscrowTags.require(result.ok(), "Settlement accounting refused: " + result.status());
        return members.stream().filter(op -> result.value().rows().containsKey(op.id())).map(op -> result.value().rows().get(op.id())).toList();
    }

    static List<EscrowFluidAccount.Row> merge(EscrowLedger root, EscrowSettlementIntent intent, List<EscrowFluidAccount.Row> rows) {
        var all = new ArrayList<>(root.fluidAccounts().stream().filter(row -> !intent.ids().contains(row.operation())).toList());
        all.addAll(rows); return List.copyOf(all);
    }

    static void requireFrozen(EscrowLedger root, EscrowSettlementIntent intent, List<EscrowSettlementIntent.Observation> observations,
            Map<EscrowEndpoint.Key, Map<UUID, EscrowFluidAccount.Vector>> vectors) {
        if (intent.accountCommitment() == null) { return; }
        var members = EscrowSettlementIntent.component(root.records(), intent.members().get(0).id());
        var projected = project(root, members, observations, vectors);
        EscrowTags.require(intent.accountCommitment().equals(EscrowFluidAccountCodec.commitment(projected)), "ACCOUNTING_MISMATCH: frozen settlement vector changed");
    }

    static Map<UUID, EscrowFluidAccount.Vector> expected(List<EscrowFluidAccount.Row> rows, List<EscrowOperation> members,
            EscrowEndpoint endpoint) {
        var result = new HashMap<UUID, EscrowFluidAccount.Vector>();
        for (var op : members) {
            if (!op.source().key().equals(endpoint.key()) && !op.destination().key().equals(endpoint.key())) { continue; }
            var row = rows.stream().filter(r -> r.operation().equals(op.id())).findFirst().orElse(null);
            if (row == null) { continue; }
            boolean source = op.source().key().equals(endpoint.key());
            if ((source ? row.frozen().entries().get(0).source() : row.frozen().entries().get(0).destination()).isEmpty()) { continue; }
            var vector = EscrowFluidAccount.Vector.aggregate(row.frozen().entries().stream().map(e -> new EscrowFluidAccount.Unit(
                    e.key(), (source ? e.source() : e.destination()).orElseThrow())).toList());
            EscrowTags.require(vector.ok(), "Invalid frozen vector"); result.put(op.id(), vector.value());
        }
        return Map.copyOf(result);
    }
}

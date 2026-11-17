package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/** Demand-driven bounded metadata only. Key hashes never expose a native tag body. */
final class EscrowAccountingDiagnostics {
    static final int PAGE_SIZE = 16;
    private EscrowAccountingDiagnostics() { }

    static List<String> page(List<EscrowFluidAccount.Row> rows, int page, String durability) {
        return page(rows, page, durability, null);
    }
    static List<String> page(List<EscrowFluidAccount.Row> rows, int page, String durability, EscrowSettlementIntent intent) {
        int total = rows.stream().mapToInt(row -> row.frozen() == null ? row.baseline().entries().size() : row.frozen().entries().size()).sum();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        EscrowTags.require(page >= 0 && page < pages, "Accounting page out of range (zero-based)");
        var result = new ArrayList<String>();
        result.add("fluid_accounting page=" + page + "/" + pages + " key_rows=" + total + " durability=" + durability
                + " endpoint_frontier_deltas=nonadditive shared_adjustments=count-once");
        int index = 0;
        for (var row : rows) {
            if (row.frozen() == null) {
                for (var entry : row.baseline().entries()) {
                    if (index++ / PAGE_SIZE != page) { continue; }
                    result.add("operation=" + row.operation() + " fluid=" + entry.key().fluid() + " tag_digest=" + entry.key().tagDigest()
                            + " knowledge=PLANNED source_before=" + entry.source() + " destination_before=" + entry.destination()
                            + " planned_source=" + entry.sourceDelta() + " planned_destination=" + entry.destinationDelta() + " residual=UNKNOWN");
                }
                continue;
            }
            for (var entry : row.frozen().entries()) {
                if (index++ / PAGE_SIZE != page) { continue; }
                var source = delta(entry.source(), row.baseline().source(entry.key()));
                var destination = delta(entry.destination(), row.baseline().destination(entry.key()));
                String residual = source.isPresent() && destination.isPresent()
                        ? java.math.BigInteger.valueOf(source.getAsLong()).add(java.math.BigInteger.valueOf(destination.getAsLong())).toString() : "UNKNOWN";
                result.add("operation=" + row.operation() + " fluid=" + entry.key().fluid() + " tag_digest=" + entry.key().tagDigest()
                        + " knowledge=" + row.knowledge() + " dS=" + number(source) + " dD=" + number(destination)
                        + " missing_source=" + entry.source().isEmpty() + " missing_destination=" + entry.destination().isEmpty()
                        + " residual=" + residual + " adjustment_owner=" + entry.owner() + " shared=" + entry.shared(row.operation())
                        + " component_residual=" + (entry.adjustment().isPresent() ? java.math.BigInteger.valueOf(entry.adjustment().getAsLong()).negate() : entry.shared(row.operation()) ? "SHARED" : "UNKNOWN")
                        + " proposed_signed_loss=" + number(entry.adjustment()) + " application=" + application(row, intent));
            }
        }
        return List.copyOf(result);
    }

    private static OptionalLong delta(OptionalLong amount, long before) {
        return amount.isPresent() ? OptionalLong.of(Math.subtractExact(amount.getAsLong(), before)) : OptionalLong.empty();
    }
    private static String number(OptionalLong value) { return value.isPresent() ? Long.toString(value.getAsLong()) : "UNKNOWN"; }
    private static String application(EscrowFluidAccount.Row row, EscrowSettlementIntent intent) {
        var entries = row.frozen().entries();
        if (entries.get(0).source().isEmpty() || entries.get(0).destination().isEmpty()) { return "UNKNOWN"; }
        if (intent != null) {
            var markers = intent.observations().stream().filter(o -> !o.missing()).flatMap(o -> o.state().markers().stream())
                    .filter(marker -> marker.operation().equals(row.operation())).toList();
            boolean source = markers.stream().anyMatch(marker -> marker.role().equals("SOURCE"));
            boolean destination = markers.stream().anyMatch(marker -> marker.role().equals("DESTINATION"));
            if (source != destination) { return source ? "HALF_APPLIED_SOURCE_ONLY" : "HALF_APPLIED_DESTINATION_ONLY"; }
        }
        var source = EscrowFluidAccount.Vector.aggregate(entries.stream().map(e -> new EscrowFluidAccount.Unit(e.key(), e.source().orElseThrow())).toList());
        var destination = EscrowFluidAccount.Vector.aggregate(entries.stream().map(e -> new EscrowFluidAccount.Unit(e.key(), e.destination().orElseThrow())).toList());
        var classified = EscrowFluidAccount.classify(row.baseline(), source.value(), destination.value());
        return classified.map(value -> switch (value) {
            case SOURCE_ONLY, DESTINATION_ONLY -> "HALF_APPLIED_" + value;
            default -> value.name();
        }).orElse("OTHER_OR_SHARED_FRONTIER");
    }

    static String audit(EscrowSettlementIntent intent, List<EscrowFluidAccount.Row> rows) {
        EscrowTags.require(intent.members().size() <= 64 && rows.stream().mapToInt(r -> r.frozen().entries().size()).sum() <= 320,
                "Accounting audit bound exceeded");
        var keys = rows.stream().flatMap(row -> row.frozen().entries().stream().map(entry ->
                "operation=" + row.operation() + " key=" + entry.key() + " source=" + number(entry.source())
                        + " destination=" + number(entry.destination()) + " signed_loss=" + number(entry.adjustment())
                        + " owner=" + entry.owner() + " knowledge=" + row.knowledge())).toList();
        return "actor=" + intent.actor() + " choice=" + intent.choice() + " digest=" + intent.digest()
                + " members=" + intent.ids() + " account_commitment=" + intent.accountCommitment() + " keys=" + keys;
    }

    static String knowledge(EscrowLedger root, java.util.Collection<java.util.UUID> ids) {
        if (root.schema() != 3) { return "UNKNOWN"; }
        if (root.records().stream().anyMatch(op -> ids.contains(op.id()) && op.legacyFluid())) { return "UNKNOWN_BASELINE"; }
        var values = root.fluidAccounts().stream().filter(row -> ids.contains(row.operation())).map(EscrowFluidAccount.Row::knowledge).distinct().toList();
        if (values.isEmpty()) { return "UNKNOWN"; }
        return values.size() == 1 ? values.get(0).name() : "UNKNOWN";
    }

    static List<String> inspect(EscrowLedger root, java.util.UUID selected, int page) {
        var op = root.records().stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null);
        java.util.Set<java.util.UUID> ids;
        if (op != null) { ids = EscrowSettlementIntent.component(root.records(), selected).stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet()); }
        else {
            var receipt = root.tombstones().stream().filter(tag -> selected.equals(tag.getUUID("op_id"))).findFirst().orElseThrow();
            ids = root.tombstones().stream().filter(tag -> tag.getString("decision_digest").equals(receipt.getString("decision_digest")))
                    .map(tag -> tag.getUUID("op_id")).collect(java.util.stream.Collectors.toSet());
        }
        var rows = root.fluidAccounts().stream().filter(row -> ids.contains(row.operation())).toList();
        if (rows.isEmpty()) { EscrowTags.require(page == 0, "Accounting page out of range"); return List.of(); }
        // Durable ledger rows are acknowledged; numeric endpoint observations need fresh O2 proofs after restart.
        return page(rows, page, "ledger-acknowledged endpoint-proof=not-claimed");
    }
}

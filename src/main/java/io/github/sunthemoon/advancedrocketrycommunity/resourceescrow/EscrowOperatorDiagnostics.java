package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;

/** Bounded metadata projections. Never opens a writer, observes a native endpoint or claims a proof. */
final class EscrowOperatorDiagnostics {
    static final int DETAILS = 4;
    private EscrowOperatorDiagnostics() { }

    static List<String> status(MinecraftServer server) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return List.of("escrow admission=disabled ledger=absent held=0 released=0 pruning_groups=0 receipts=0/64"); }
        var runtime = EscrowRuntime.of(server);
        var lines = new ArrayList<>(status(data.ledger(), op -> runtimeReason(runtime, op), data.floorUsage()));
        var scheduler = runtime.reconciliationScheduler();
        if (scheduler != null) { lines.add(reconciliationStatus(data.ledger(), scheduler::view)); }
        return List.copyOf(lines);
    }
    static List<String> status(EscrowLedger root, Function<EscrowOperation, String> reason) {
        return status(root, reason, EscrowFloors.usage(root, List.of()));
    }
    static String reconciliationStatus(EscrowLedger root, Function<UUID, EscrowReconciliationScheduler.View> view) {
        var ordinary = root.records().stream().filter(op -> op.settlement().isEmpty()).toList();
        return "reconciliation=" + ordinary.stream().limit(DETAILS).map(op -> {
            var state = view.apply(op.id());
            return "operation=" + op.id() + " persisted_reason=" + op.reason()
                    + " runtime_reason=" + bounded(state.reason()) + " next_retry=" + state.nextTick();
        }).toList() + " omitted=" + Math.max(0, ordinary.size() - DETAILS);
    }
    static List<String> status(EscrowLedger root, Function<EscrowOperation, String> reason, EscrowFloors.Usage usage) {
        var receipts = root.tombstones(); var groups = EscrowSettlementPruning.groups(root);
        var reasons = new java.util.TreeMap<String, Integer>();
        root.records().forEach(op -> reasons.merge(bounded(reason.apply(op)), 1, Integer::sum));
        int settling = (int) root.records().stream().filter(op -> !op.settlement().isEmpty()).count();
        return List.of("escrow admission=disabled operations=" + root.records().size() + "/" + EscrowLimits.OPERATIONS
                + " held=" + root.records().size() + " settling_members=" + settling
                + " released=" + receipts.stream().filter(tag -> tag.getBoolean("released")).count()
                + " pruning_groups=" + groups.size() + " pruning_members=" + groups.stream().mapToInt(group -> group.members().size()).sum()
                + " pruning=scheduled-released-groups-not-completion",
                "receipts=" + receipts.size() + "/64 receipts_reserved=" + EscrowSettlementIntent.reservedReceipts(root)
                + " accept_current_members=" + count(receipts, "ACCEPT_CURRENT") + " abandon_members=" + count(receipts, "ABANDON")
                + " floor_members=" + count(receipts, "TRANSFER_FLOOR_RETIRE")
                + " floors_used=" + usage.used() + " floors_reserved=" + usage.reserved() + "/" + EscrowLimits.FLOORS
                + " ready_heads=" + root.rocketAuthorities().size() + "/64 armed=" + root.armedFlights().size() + "/64",
                "held_reasons=" + reasons.entrySet().stream().limit(DETAILS).toList()
                + " other_reason_kinds=" + Math.max(0, reasons.size() - DETAILS)
                + " accounting=UNKNOWN; all settlement choices consume one permanent receipt per member; at 64, all choices refuse and require stopped-server whole-world offline repair.");
    }
    private static long count(List<net.minecraft.nbt.CompoundTag> receipts, String choice) {
        return receipts.stream().filter(tag -> choice.equals(tag.getString("choice"))).count();
    }
    static List<String> inspect(MinecraftServer server, UUID selected) {
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null, "No escrow ledger; inspect does not create one");
        var runtime = EscrowRuntime.of(server); var scheduler = runtime.reconciliationScheduler();
        var journal = RocketTransferSavedData.existing(server);
        return inspect(data.ledger(), selected, runtime.settlementRecovery()::view,
                id -> scheduler == null ? new EscrowReconciliationScheduler.View("QUEUED", "NONE", -1, 0, false) : scheduler.view(id),
                journal == null ? "absent-unproved" : journal.checkedWriteResult().toString(), runtime.settlementRecovery()::pruningState);
    }
    static List<String> inspect(EscrowLedger root, UUID selected,
            Function<EscrowSettlementIntent, EscrowSettlementRecovery.View> settlement,
            Function<UUID, EscrowReconciliationScheduler.View> reconciliation, String journal) {
        return inspect(root, selected, settlement, reconciliation, journal, (digest, endpoints) -> "UNVISITED");
    }
    /** Released members show the pruning cursor's last visited state: waiting, refused with a reason, or done (F5). */
    static List<String> inspect(EscrowLedger root, UUID selected,
            Function<EscrowSettlementIntent, EscrowSettlementRecovery.View> settlement,
            Function<UUID, EscrowReconciliationScheduler.View> reconciliation, String journal,
            java.util.function.BiFunction<String, List<EscrowEndpoint.Key>, String> pruning) {
        var op = root.records().stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null);
        var receipt = root.tombstones().stream().filter(tag -> selected.equals(tag.getUUID("op_id"))).findFirst().orElse(null);
        EscrowTags.require(op != null || receipt != null, "Unknown operation");
        var result = new ArrayList<String>();
        if (op == null) {
            String digest = receipt.getString("decision_digest");
            var group = root.tombstones().stream().filter(tag -> tag.getString("decision_digest").equals(digest))
                    .sorted(java.util.Comparator.comparing(tag -> tag.getUUID("op_id").toString())).toList();
            var survivors = new java.util.LinkedHashSet<EscrowEndpoint.Key>(); group.forEach(member -> survivors.addAll(survivors(member)));
            result.add("group=" + digest + " members=" + group.size()
                    + " omitted=" + Math.max(0, group.size() - DETAILS) + " pruning=" + pruning(pruning, digest, receipt, List.copyOf(survivors))
                    + " journal=" + bounded(journal) + " accounting=UNKNOWN");
            for (var member : group.stream().limit(DETAILS).toList()) {
                UUID logical = EscrowCodec.guard((net.minecraft.nbt.CompoundTag) EscrowTags.list(member, "guards", 1).get(0)).rocket();
                result.add("operation=" + member.getUUID("op_id") + " persisted=RETIRED runtime=RELEASED reason=NONE O2=not-retained"
                        + " pruning=" + pruning(pruning, digest, member, survivors(member))
                        + " next_retry=none receipt=" + (member.getBoolean("released") ? "RELEASED" : "INSTALLED")
                        + " head=" + head(root, logical) + " accounting=UNKNOWN");
            }
            return List.copyOf(result);
        }
        var group = EscrowSettlementIntent.component(root.records(), selected);
        result.add("group=" + (op.settlement().isEmpty() ? group.get(0).id() : op.settlement().getString("digest"))
                + " members=" + group.size() + " omitted=" + Math.max(0, group.size() - DETAILS)
                + " journal=" + bounded(journal) + " accounting=UNKNOWN");
        for (var member : group.stream().limit(DETAILS).toList()) {
            String runtime;
            if (!member.settlement().isEmpty()) {
                var view = settlement.apply(EscrowSettlementIntent.decode(member.settlement()));
                runtime = view.phase() + " reason=" + bounded(view.reason()) + " O2_proved_slots=" + view.provedSlots()
                        + " O2_waiting_slots=" + view.waitingSlots() + " next_retry=" + view.nextTick() + " attempt_pending=" + view.active();
            } else {
                var view = reconciliation.apply(member.id());
                runtime = view.phase() + " reason=" + bounded(view.reason()) + " O2=not-applicable next_retry=" + view.nextTick()
                        + " failures=" + view.failures() + " attempt_pending=" + view.active();
            }
            var installed = root.tombstones().stream().filter(tag -> member.id().equals(tag.getUUID("op_id"))).findFirst().orElse(null);
            result.add("operation=" + member.id() + " persisted=" + member.phase() + " persisted_reason=" + member.reason()
                    + " runtime=" + runtime + " receipt=" + (installed == null ? "NONE" : "INSTALLED")
                    + " head=" + head(root, member.guard().rocket()) + " accounting=UNKNOWN");
        }
        return List.copyOf(result);
    }
    static String preview(EscrowSettlementIntent intent) {
        return "Settlement preview; release requires acknowledged O2-O4. actor_type=" + EscrowCommandActor.kind(intent.actor()) + " choice=" + intent.choice()
                + " members=" + intent.members().size() + " operations=" + intent.members().stream().limit(DETAILS).map(EscrowSettlementIntent.Member::id).toList()
                + " omitted=" + Math.max(0, intent.members().size() - DETAILS) + " digest=" + intent.digest()
                + " permanent_receipts_consumed=" + intent.members().size()
                + "; all choices count toward 64 permanent receipts; exhaustion requires stopped-server whole-world offline repair."
                + " accounting=UNKNOWN; accepting current quantities proves neither zero loss nor absence of duplication; O1 cannot be rescinded online.";
    }
    private static String runtimeReason(EscrowRuntime runtime, EscrowOperation op) {
        if (!op.settlement().isEmpty()) { return runtime.settlementRecovery().view(EscrowSettlementIntent.decode(op.settlement())).reason(); }
        var scheduler = runtime.reconciliationScheduler();
        if (op.reason() != EscrowReason.NONE || scheduler == null) { return op.reason().name(); }
        return scheduler.view(op.id()).reason();
    }
    private static List<EscrowEndpoint.Key> survivors(net.minecraft.nbt.CompoundTag receipt) {
        return EscrowTags.list(receipt, "survivors", 2).stream()
                .map(raw -> EscrowCodec.endpoint(((net.minecraft.nbt.CompoundTag) raw).getCompound("endpoint")).key()).toList();
    }
    private static String pruning(java.util.function.BiFunction<String, List<EscrowEndpoint.Key>, String> pruning, String digest,
            net.minecraft.nbt.CompoundTag receipt, List<EscrowEndpoint.Key> survivors) {
        // The cursor bounds and cleans each refusal reason to 160 characters.
        return receipt.getBoolean("released") ? pruning.apply(digest, survivors) : "NOT_RELEASED";
    }
    private static String head(EscrowLedger root, UUID logical) {
        var head = root.rocketAuthorities().stream().filter(value -> value.logical().equals(logical)).findFirst().orElse(null);
        var arm = root.armedFlights().stream().filter(value -> value.logical().equals(logical)).findFirst().orElse(null);
        return (head == null ? "NONE" : "READY") + (arm == null ? "" : "/" + arm.outcome());
    }
    private static String bounded(String text) {
        String clean = text.replaceAll("[\\p{Cntrl}]", " ");
        return clean.length() <= 160 ? clean : clean.substring(0, 160);
    }
}

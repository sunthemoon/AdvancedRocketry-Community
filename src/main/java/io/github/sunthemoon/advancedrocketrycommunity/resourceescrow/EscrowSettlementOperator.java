package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Server-lifetime preview cache and O1 publication; the recovery service checkpoints and releases. */
final class EscrowSettlementOperator {
    static final int PREVIEWS = 64;
    static final int PREVIEW_TICKS = 1_200;
    private final PreviewCache previews = new PreviewCache();
    private final EscrowOperatorRetry retries = new EscrowOperatorRetry();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Preview only: does not observe endpoints, reserve a slot or cache a confirm digest. */
    EscrowAdmissionAuthority.Result floorAdmission(MinecraftServer server, UUID selected) {
        var data = existing(server); requireReceiptHeadroom(data.ledger());
        var floor = data.ledger().floors().stream().filter(tag -> tag.hasUUID("transfer_id") && selected.equals(tag.getUUID("transfer_id")))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown transfer floor"));
        var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(server);
        var guard = EscrowCodec.floorGuard(floor);
        if (RocketEscrowAudit.journal(guard, journal.acknowledgedGuard(guard.rocket()).orElse(null)) != EscrowReason.NONE) {
            return EscrowAdmissionAuthority.Result.UNAVAILABLE;
        }
        var record = journal.find(guard.transfer()).orElse(null);
        if (record == null) { return EscrowAdmissionAuthority.Result.UNAVAILABLE; }
        return floorAdmission(data.ledger(), data.bind(server).admissionAuthority(), UUID.randomUUID(), record.ownerId(), guard.rocket());
    }
    /**
     * R258 keeps O1's order: the permanent RECEIPTS_FULL refusal, which waiting cannot clear, precedes the transient
     * pending-capacity result. The receipt check is floorPreview's own, for one new member.
     */
    static EscrowAdmissionAuthority.Result floorAdmission(EscrowLedger root, EscrowAdmissionAuthority authority, UUID operation,
                                                          UUID storedOwner, UUID logical) {
        EscrowSettlementIntent.requireReceiptRoom(root, List.of(operation));
        return authority.checkReserve(operation, storedOwner, logical, EscrowAdmissionAuthority.Consumer.PROPULSION);
    }
    private static void requireReceiptHeadroom(EscrowLedger root) {
        EscrowTags.require(root.tombstones().size() < EscrowLimits.TOMBSTONES, EscrowSettlementIntent.RECEIPTS_FULL);
    }
    private static UUID floorOwner(MinecraftServer server, net.minecraft.nbt.CompoundTag floor) {
        return io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData.get(server)
                .find(EscrowCodec.floorGuard(floor).transfer()).orElseThrow().ownerId();
    }

    EscrowSettlementIntent preview(MinecraftServer server, UUID selected, EscrowSettlementIntent.Choice choice, UUID actor, String name) {
        var data = existing(server); long tick = server.getTickCount();
        requireReceiptHeadroom(data.ledger());
        previews.requireCapacity(actor, tick);
        if (choice == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE) {
            var floor = data.ledger().floors().stream().filter(tag -> tag.hasUUID("transfer_id") && selected.equals(tag.getUUID("transfer_id")))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown transfer floor"));
            var observed = EscrowSettlementObserver.floor(server, floor, data.ledger().id());
            var intent = EscrowSettlementIntent.floorPreview(data.ledger(), UUID.randomUUID(), floor, actor, name, tick, observed);
            var confirmed = intent.confirm(actor, intent.digest(), tick + 200, observed);
            EscrowSettlementCapacity.floorRetirement(data.ledger(), confirmed, data.bind(server).nextSequence(), tick, floorOwner(server, floor));
            previews.put(intent, tick); return intent;
        }
        var observed = EscrowSettlementObserver.component(server, data.ledger(), selected);
        var intent = EscrowSettlementIntent.preview(data.ledger(), selected, choice, actor, name, tick, observed);
        // Validate operation envelopes and reserve capacity before offering a confirm digest.
        intent.confirm(actor, intent.digest(), tick + (observed.stream().anyMatch(EscrowSettlementIntent.Observation::missing) ? 200 : 0), observed)
                .freeze(data.ledger());
        previews.put(intent, tick); return intent;
    }
    boolean confirm(MinecraftServer server, UUID selected, EscrowSettlementIntent.Choice choice, UUID actor,
                    String digest, Consumer<Boolean> completion) {
        var data = existing(server); long tick = server.getTickCount(); var preview = previews.get(actor, tick);
        EscrowTags.require(preview != null && (choice == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE
                ? selected.equals(preview.floor().getUUID("transfer_id")) : preview.ids().contains(selected)) && preview.choice() == choice
                && tick - preview.previewTick() <= PREVIEW_TICKS, "Preview is absent, expired or describes another choice/component");
        List<EscrowSettlementIntent.Observation> observed = choice == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE
                ? EscrowSettlementObserver.floor(server, preview.floor(), data.ledger().id())
                : EscrowSettlementObserver.component(server, data.ledger(), selected);
        var intent = preview.confirm(actor, digest, tick, observed);
        UUID owner = choice == EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE ? floorOwner(server, intent.floor()) : actor;
        boolean queued = data.bind(server).beginSettlement(intent, owner, committed -> {
            if (committed) {
                previews.remove(actor, preview);
                LOGGER.info("ARCE_ESCROW_O1 actor={} choice={} operations={} digest={} quantities={} release=awaiting-checkpoints",
                        intent.actor(), intent.choice(), intent.ids(), intent.digest(), intent.observations().stream().map(EscrowSettlementIntent.Observation::quantity).toList());
            }
            completion.accept(committed);
        });
        return queued;
    }
    private static ResourceEscrowSavedData existing(MinecraftServer server) {
        EscrowTags.require(server.isSameThread(), "Settlement requires the server thread");
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null, "No escrow ledger; settlement does not create one"); return data;
    }
    EscrowOperatorRetry.Result retry(MinecraftServer server, UUID selected) {
        var data = existing(server); var runtime = EscrowRuntime.of(server); long tick = server.getTickCount();
        var result = retries.request(data.ledger(), selected, tick, () -> {
            var op = data.ledger().records().stream().filter(value -> value.id().equals(selected)).findFirst().orElseThrow();
            if (!op.settlement().isEmpty()) {
                return runtime.settlementRecovery().accelerate(EscrowSettlementIntent.decode(op.settlement()), tick);
            }
            if (op.phase() == EscrowOperation.Phase.REPAIR || op.reason().manual() || op.reason() == EscrowReason.BARRIER_VETO) {
                return EscrowOperatorRetry.Outcome.REFUSED;
            }
            var scheduler = runtime.reconciliationScheduler();
            return scheduler == null ? EscrowOperatorRetry.Outcome.PENDING : scheduler.accelerate(selected, tick);
        });
        if (result.outcome() == EscrowOperatorRetry.Outcome.ACCEPTED) {
            LOGGER.info("ARCE_ESCROW_RETRY operation={} next_command_tick={} proof=not-claimed", selected, result.nextTick());
        }
        return result;
    }
    void close() { previews.clear(); retries.clear(); }
    /** A copy for the provenance GameTest; no command reads it. */
    Map<UUID, EscrowSettlementIntent> previewSnapshot() { return previews.snapshot(); }
    static final class PreviewCache {
        private final Map<UUID, EscrowSettlementIntent> entries = new HashMap<>();
        void requireCapacity(UUID actor, long tick) {
            entries.entrySet().removeIf(entry -> tick - entry.getValue().previewTick() > PREVIEW_TICKS);
            EscrowTags.require(entries.containsKey(actor) || entries.size() < PREVIEWS, "Settlement preview capacity exhausted");
        }
        void put(EscrowSettlementIntent intent, long tick) {
            requireCapacity(intent.actor(), tick); entries.put(intent.actor(), intent);
        }
        EscrowSettlementIntent get(UUID actor, long tick) {
            var result = entries.get(actor);
            if (result != null && tick - result.previewTick() > PREVIEW_TICKS) { entries.remove(actor); return null; }
            return result;
        }
        void remove(UUID actor, EscrowSettlementIntent intent) { entries.remove(actor, intent); }
        int size() { return entries.size(); }
        Map<UUID, EscrowSettlementIntent> snapshot() { return Map.copyOf(entries); }
        void clear() { entries.clear(); }
    }
}

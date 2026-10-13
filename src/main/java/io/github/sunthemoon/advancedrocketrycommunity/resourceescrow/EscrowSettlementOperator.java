package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/** Server-lifetime preview cache and O1 publication. O2-O4 are unavailable and records stay locked. */
final class EscrowSettlementOperator {
    static final int PREVIEWS = 64;
    static final int PREVIEW_TICKS = 1_200;
    private final PreviewCache previews = new PreviewCache();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    EscrowSettlementIntent preview(MinecraftServer server, UUID selected, EscrowSettlementIntent.Choice choice, UUID actor, String name) {
        var data = existing(server); long tick = server.getTickCount();
        previews.requireCapacity(actor, tick);
        EscrowTags.require(choice != EscrowSettlementIntent.Choice.TRANSFER_FLOOR_RETIRE,
                "Floor preview adapter is not yet implemented; no intent or record was created");
        var observed = EscrowSettlementObserver.component(server, data.ledger(), selected);
        var intent = EscrowSettlementIntent.preview(data.ledger(), selected, choice, actor, name, tick, observed);
        // Validate operation envelopes and reserve capacity before offering a confirm digest.
        if (choice == EscrowSettlementIntent.Choice.ACCEPT_CURRENT) {
            intent.confirm(actor, intent.digest(), tick, observed).freeze(data.ledger());
        }
        previews.put(intent, tick); return intent;
    }
    boolean confirm(MinecraftServer server, UUID selected, EscrowSettlementIntent.Choice choice, UUID actor,
                    String digest, Consumer<Boolean> completion) {
        var data = existing(server); long tick = server.getTickCount(); var preview = previews.get(actor, tick);
        EscrowTags.require(preview != null && preview.ids().contains(selected) && preview.choice() == choice
                && tick - preview.previewTick() <= PREVIEW_TICKS, "Preview is absent, expired or describes another choice/component");
        List<EscrowSettlementIntent.Observation> observed = EscrowSettlementObserver.component(server, data.ledger(), selected);
        var intent = preview.confirm(actor, digest, tick, observed);
        boolean queued = data.bind(server).beginSettlement(intent, committed -> {
            if (committed) {
                previews.remove(actor, preview);
                LOGGER.info("ARCE_ESCROW_O1 actor={} choice={} operations={} digest={} quantities={} release=unavailable",
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
    void close() { previews.clear(); }
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
        void clear() { entries.clear(); }
    }
}

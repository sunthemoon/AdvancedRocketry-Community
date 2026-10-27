package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * W27d1's restart rows (W27A-DESIGN section 4's restart classification for INTENT; W128's one-page design). Claims are
 * built at ServerStartingEvent, before the first tick, from the loaded receipts; each INTENT row is decided and written
 * by a queued step. REJECTED receipts claim nothing.
 */
final class BayRestartRows {
    /** What one durable INTENT becomes in this session. */
    enum Row {
        /** X absent and T matching: REJECTED (INTERRUPTED), then RELEASED_TO_ORDINARY. */
        REJECT_INTERRUPTED,
        /** X present with a different identity: durable REPAIR (REGISTRY_SIGNATURE_CONFLICT). */
        REPAIR_SIGNATURE,
        /** X present and equal: W27d2 promotes it; here it waits unchanged at runtime. */
        HOLD_EQUAL,
        /** The registry is blocked: runtime REGISTRY_BLOCKED, treated like REPAIR for the wait. */
        HOLD_BLOCKED,
        /** T missing or different: runtime REPAIR (TRANSFER_AUTHORITY_CONFLICT), no write. */
        HOLD_AUTHORITY
    }

    private BayRestartRows() { }

    /** One claim per logical rocket named by an INTENT, REGISTERED, REPAIR or released receipt, members in bay order. */
    static List<BayClaim> claims(List<BayReceipt> receipts) {
        var byRocket = new LinkedHashMap<UUID, List<BayReceipt>>();
        for (BayReceipt receipt : receipts) {
            if (receipt.pending()) { byRocket.computeIfAbsent(receipt.rocket(), rocket -> new ArrayList<>()).add(receipt); }
        }
        var claims = new ArrayList<BayClaim>();
        for (var entry : byRocket.entrySet()) {
            var group = entry.getValue();
            group.sort(Comparator.comparing(BayReceipt::bay));
            UUID transfer = group.get(0).transfer();
            boolean oneTransfer = group.stream().allMatch(receipt -> receipt.transfer().equals(transfer));
            var members = new ArrayList<BayMember>();
            String repair = oneTransfer ? null : "TRANSFER_AUTHORITY_CONFLICT";
            for (BayReceipt receipt : group) {
                if (receipt.released()) {
                    members.add(BayMember.restartHeld(receipt, "RELEASED_FLIGHT")); // W27d4 dispatches released receipts
                } else if (receipt.phase() == BayReceipt.Phase.INTENT) {
                    members.add(oneTransfer ? BayMember.restartIntent(receipt)
                            : BayMember.restartHeld(receipt, "TRANSFER_AUTHORITY_CONFLICT"));
                } else if (receipt.phase() == BayReceipt.Phase.REGISTERED) {
                    members.add(BayMember.restartHeld(receipt, "DEPLOYMENT_PENDING")); // W27d2 resumes it
                } else {
                    members.add(BayMember.restartHeld(receipt, receipt.reason()));
                    if (repair == null) { repair = receipt.reason(); }
                }
            }
            var claim = BayClaim.restart(entry.getKey(), transfer, members);
            if (repair != null) { claim.repair(repair); }
            claims.add(claim);
        }
        return claims;
    }

    /** The row of one durable INTENT, read from the loaded registry and transfer journal. */
    static Row row(MinecraftServer server, BayReceipt intent) {
        var registry = SatelliteMissionSavedData.get(server);
        if (!registry.operational()) { return Row.HOLD_BLOCKED; }
        var journal = RocketTransferSavedData.get(server);
        var record = journal.operational() ? journal.find(intent.transfer()).orElse(null) : null;
        if (record == null || record.phase() != RocketTransferPhase.PREPARED || !record.logicalRocketId().equals(intent.rocket())
                || !record.ownerId().equals(intent.owner())
                || !record.sourceSnapshot().contentHash().equals(intent.srcBefore())
                || !record.destinationSnapshot().contentHash().equals(intent.dstBefore())) {
            return Row.HOLD_AUTHORITY;
        }
        var existing = registry.satellite(intent.satellite()).orElse(null);
        if (existing == null) { return Row.REJECT_INTERRUPTED; }
        boolean equal = existing.ownerId().equals(intent.owner()) && existing.kind() == intent.kind()
                && existing.definitionId().toString().equals(intent.definition());
        return equal ? Row.HOLD_EQUAL : Row.REPAIR_SIGNATURE;
    }
}

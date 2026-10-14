package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryAction;
import java.util.List;
import java.util.UUID;

/** Pure recovery policy. A historical snapshot is never a substitute for credited endpoint authority. */
public final class EscrowRecoveryFence {
    private EscrowRecoveryFence() { }

    public record Rocket(UUID identity, long epoch, boolean marked, boolean valid) {
        public Rocket {
            java.util.Objects.requireNonNull(identity);
            if (epoch < 0) { throw new IllegalArgumentException("Negative resource epoch"); }
        }
        public boolean protectedResources() { return marked || epoch != 0 || !valid; }
    }

    /** Ledger-only classification, before inspecting endpoint readiness or acquiring tickets. */
    public static boolean known(EscrowLedger ledger, UUID logical, UUID transfer) {
        return ledger.records().stream().anyMatch(op -> op.guard().rocket().equals(logical))
                || tombstoned(ledger, logical) || EscrowSettlementTombstones.retiredTransfer(ledger, logical, transfer)
                || ledger.floors().stream().anyMatch(floor -> logical.equals(floor.getUUID("rocket_id"))
                        && floor.hasUUID("transfer_id") && transfer.equals(floor.getUUID("transfer_id")));
    }

    public static boolean tombstoned(EscrowLedger ledger, UUID logical) {
        return EscrowSettlementTombstones.logicalLocked(ledger, logical);
    }

    /** Inputs are an already-loaded, bounded observation in the election order used by recovery. */
    public static String reason(EscrowLedger ledger, UUID logical, UUID transfer,
                                RocketTransferRecoveryAction action, List<Rocket> sources, List<Rocket> destinations) {
        if (ledger != null && ledger.records().stream().anyMatch(op -> op.guard().rocket().equals(logical))) { return "ESCROW_PENDING"; }
        if (ledger != null && (tombstoned(ledger, logical) || EscrowSettlementTombstones.retiredTransfer(ledger, logical, transfer)
                || java.util.stream.Stream.concat(sources.stream(), destinations.stream())
                .anyMatch(rocket -> EscrowSettlementTombstones.identityLocked(ledger, logical, rocket.identity())
                        || rocket.epoch() < EscrowSettlementTombstones.minimumEpoch(ledger, logical, rocket.identity())))) { return "TOMBSTONE"; }
        if (java.util.stream.Stream.concat(sources.stream(), destinations.stream()).anyMatch(rocket -> !rocket.valid())) { return "INVALID_ENDPOINT"; }
        if (java.util.stream.Stream.concat(sources.stream(), destinations.stream()).anyMatch(Rocket::marked)) { return "ESCROW_MARKER"; }
        boolean destinationAuthority = switch (action) {
            case KEEP_DESTINATION, REMOVE_SOURCE_KEEP_DESTINATION, REBUILD_DESTINATION -> true;
            default -> false;
        };
        List<Rocket> keptSide = destinationAuthority ? destinations : sources;
        List<Rocket> discardedSide = destinationAuthority ? sources : destinations;
        if (discardedSide.stream().anyMatch(Rocket::protectedResources)
                || keptSide.stream().skip(1).anyMatch(Rocket::protectedResources)) { return "FLOOR_CONFLICT"; }
        // Source recovery replaces flight/resources from the pre-flight record, unlike destination KEEP.
        if (!destinationAuthority && keptSide.stream().anyMatch(Rocket::protectedResources)) { return "FLOOR_CONFLICT"; }
        if (ledger != null) {
            for (var floor : ledger.floors()) {
                if (!logical.equals(floor.getUUID("rocket_id")) || !floor.hasUUID("transfer_id")
                        || !transfer.equals(floor.getUUID("transfer_id"))) { continue; }
                if (keptSide.isEmpty() || !floor.getUUID("entity_id").equals(keptSide.get(0).identity())
                        || keptSide.get(0).epoch() < floor.getLong("resource_epoch")) { return "FLOOR_CONFLICT"; }
            }
        }
        return null;
    }

    /** Assembly credit is not expendable; idle credited DISASSEMBLY still has its normal D1 authority. */
    public static boolean transaction(EscrowLedger ledger, UUID logical, boolean assembly, List<Rocket> candidates) {
        return ledger != null && (ledger.records().stream().anyMatch(op -> op.guard().rocket().equals(logical))
                || tombstoned(ledger, logical) || candidates.stream().anyMatch(rocket -> EscrowSettlementTombstones.identityLocked(ledger, logical, rocket.identity())
                        || rocket.epoch() < EscrowSettlementTombstones.minimumEpoch(ledger, logical, rocket.identity()))
                || assembly && ledger.tombstones().stream().flatMap(tag -> EscrowTags.list(tag, "guards", 64).stream())
                .anyMatch(raw -> logical.equals(EscrowCodec.guard((net.minecraft.nbt.CompoundTag) raw).rocket()))
                || assembly && ledger.floors().stream().anyMatch(floor -> logical.equals(floor.getUUID("rocket_id"))))
                || candidates.stream().anyMatch(rocket -> !rocket.valid() || rocket.marked() || assembly && rocket.epoch() != 0);
    }
}

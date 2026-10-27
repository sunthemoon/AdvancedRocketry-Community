package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowAdmissionAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** W27d1's reason mapping for D1's store refusals and D2's reject-only revalidation (W128's one-page design). */
public final class BayDeploymentReasons {
    private BayDeploymentReasons() { }

    /** D1: each refusal of the real store and authority maps to its own runtime reason; nothing durable changed. */
    public static String admission(BayReceiptStore.Admission admission) {
        return switch (admission.status()) {
            case QUEUED -> throw new IllegalArgumentException("A queued admission is not a refusal");
            case STORE_UNAVAILABLE -> "BAY_RECEIPTS_UNAVAILABLE";
            case BUSY -> "RECEIPT_PENDING";
            case INVALID, RELEASED_LOCKED -> "BAY_CONFLICT";
            case RETAIN_REFUSED -> retention(admission.retention());
            case RESERVE_REFUSED -> reservation(admission.reservation());
        };
    }

    private static String retention(EscrowAdmissionAuthority.Retention retention) {
        if (retention == null) { return "BAY_RECEIPTS_UNAVAILABLE"; }
        return switch (retention) {
            case SERVER_FULL -> "RECEIPTS_FULL";
            case OWNER_FULL -> "OWNER_RECEIPTS_FULL";
            case IDENTITY_CONFLICT -> "RECEIPT_IDENTITY_CONFLICT";
            case UNAVAILABLE, RESERVED, ALREADY_HELD -> "BAY_RECEIPTS_UNAVAILABLE";
        };
    }

    private static String reservation(EscrowAdmissionAuthority.Result result) {
        if (result == null) { return "BAY_RECEIPTS_UNAVAILABLE"; }
        return switch (result) {
            case SERVER_FULL -> "DEPLOYMENT_CAPACITY";
            case ROCKET_FULL -> "ROCKET_PENDING_FULL";
            case IDENTITY_CONFLICT -> "RECEIPT_IDENTITY_CONFLICT";
            case UNAVAILABLE, RESERVED, ALREADY_HELD -> "BAY_RECEIPTS_UNAVAILABLE";
        };
    }

    /**
     * D2 after the exact INTENT acknowledgement: replay-first validation once more, through a registry permit for this
     * operation and satellite only. W27d1 registers nothing: a refusal keeps its code (B9), and a validation that would
     * succeed becomes DEPLOYMENT_UNAVAILABLE (R49 Q4), which W27d2 re-arms with the same operation.
     */
    static String revalidate(MinecraftServer server, BayDeploymentSession session, BayDeploymentChecks checks, BayClaim claim,
            BayReceipt intent) {
        var record = RocketTransferSavedData.get(server).find(claim.transfer()).orElse(null);
        var bay = record == null ? null : checks.bay(record.sourceSnapshot(), intent.bay()).orElse(null);
        var identity = bay == null ? null : bay.identity().orElse(null);
        if (identity == null || !identity.satelliteId().equals(intent.satellite())) { return "INVALID_PACKAGE"; }
        var service = SatelliteRuntime.launchService().orElse(null);
        if (service == null) { return "SERVER_ERROR"; }
        var registry = SatelliteMissionSavedData.get(server);
        if (!registry.operational()) { return "REGISTRY_BLOCKED"; }
        var permit = session.permit(intent.op(), intent.satellite());
        var validation = registry.validateWithPermit(permit,
                () -> service.validate(server, identity.ownerId(), identity, ResourceLocation.tryParse(intent.orbit())));
        return validation.request().isPresent() ? "DEPLOYMENT_UNAVAILABLE" : validation.result().code().name();
    }
}

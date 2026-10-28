package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowAdmissionAuthority;

/** W27d1's reason mapping for D1's store refusals (W128's one-page design); W170's D2 registers in {@link BayRegistrationSteps}. */
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
}

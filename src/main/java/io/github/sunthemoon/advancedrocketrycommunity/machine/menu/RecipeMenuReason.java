package io.github.sunthemoon.advancedrocketrycommunity.machine.menu;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;

/** Display-only whitelist. Neither subjects nor localized text travel through menu data. */
public enum RecipeMenuReason {
    NONE(0, ""),
    RECIPE_MISSING(1, "recipe_missing"),
    RECIPE_CHANGED(2, "recipe_changed"),
    RECIPE_TAGS_INVALID(3, "recipe_tags_invalid"),
    SIGNATURE_MIGRATION_PENDING(4, "signature_migration_pending"),
    SIGNATURE_MIGRATION_UNPROVEN(5, "signature_migration_unproven"),
    RETAINED_PLAN_INVALID(6, "retained_plan_invalid");

    private final int networkId;
    private final String subject;

    RecipeMenuReason(int networkId, String subject) {
        this.networkId = networkId;
        this.subject = subject;
    }

    public int networkId() {
        return networkId;
    }

    public String translationKey() {
        return "status.advancedrocketrycommunity.machine_recipe."
                + (this == NONE ? "generic" : subject);
    }

    public static RecipeMenuReason fromFailure(ProcessFailure failure) {
        if (failure.code() == ProcessFailureCode.NONE) {
            return NONE;
        }
        return switch (failure.subject()) {
            case "recipe_missing" -> RECIPE_MISSING;
            case "recipe_changed" -> RECIPE_CHANGED;
            case "recipe_tags_invalid" -> RECIPE_TAGS_INVALID;
            case "signature_migration_pending" -> SIGNATURE_MIGRATION_PENDING;
            case "signature_migration_unproven" -> SIGNATURE_MIGRATION_UNPROVEN;
            case "retained_plan_invalid" -> RETAINED_PLAN_INVALID;
            default -> NONE;
        };
    }

    public static RecipeMenuReason fromNetworkId(int id) {
        return switch (id) {
            case 1 -> RECIPE_MISSING;
            case 2 -> RECIPE_CHANGED;
            case 3 -> RECIPE_TAGS_INVALID;
            case 4 -> SIGNATURE_MIGRATION_PENDING;
            case 5 -> SIGNATURE_MIGRATION_UNPROVEN;
            case 6 -> RETAINED_PLAN_INVALID;
            default -> NONE;
        };
    }
}

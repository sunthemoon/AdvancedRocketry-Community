package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Objects;
import java.util.Set;

/** Persisted bounded reason, not an exception message or localized sentence. */
public record ClassicRefusal(String code, String subject) {
    static final Set<String> CODES = Set.of("none", "not_formed", "waiting_unloaded", "binding_conflict",
            "unsupported_data", "repair_required", "busy", "missing_item_input", "missing_fluid_input",
            "output_blocked", "insufficient_energy", "redstone_disabled", "invalid_recipe", "recipe_changed",
            "recipe_missing", "recipe_catalog_invalid", "recipe_tag_unavailable", "plan_budget_exceeded",
            "recovery_diverged", "journal_conflict", "arithmetic_overflow", "counter_exhausted",
            "formation_rollback_failed", "drop_spawn_failed", "drop_uncertain");

    public ClassicRefusal {
        Objects.requireNonNull(subject, "subject");
        ClassicValueChecks.require(CODES.contains(code) && subject.length() <= 128, "Invalid refusal");
        ClassicValueChecks.require(!"none".equals(code) || subject.isEmpty(), "NONE subject must be empty");
    }
}

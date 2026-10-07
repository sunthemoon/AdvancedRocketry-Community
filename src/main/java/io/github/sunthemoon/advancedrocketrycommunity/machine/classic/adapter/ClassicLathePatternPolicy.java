package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Same-ID family audit layered over the unchanged immutable kernel definition. */
final class ClassicLathePatternPolicy {
    private static final String ID = "advancedrocketrycommunity:lathe";
    private static final Set<String> ROLES = Set.of("item_input", "item_output", "fluid_input", "fluid_output", "energy_input");

    Optional<ClassicPatternSelection> select(MultiblockPatternCatalogManager manager) {
        Objects.requireNonNull(manager, "manager");
        long generation = manager.status().generation();
        var catalog = manager.current().orElse(null);
        if (catalog == null) { return Optional.empty(); }
        var selected = catalog.get(ID);
        if (selected.isEmpty()) { return Optional.empty(); }
        var definition = selected.orElseThrow();
        if (definition.allowMirrorLocalX() || !definition.allowedRotations().equals(EnumSet.allOf(PatternRotation.class))) {
            return Optional.empty();
        }
        // The kernel constructor already enforces complete axes <=16, cells <=4096,
        // one direct controller anchor and <=64 (including optional) port cells.
        int controllers = 0;
        for (PatternMatcher matcher : definition.cells().values()) {
            PatternMatcher actual = matcher instanceof PatternMatcher.OptionalCell optional ? optional.matcher() : matcher;
            if (actual instanceof PatternMatcher.Controller && ++controllers > 1) { return Optional.empty(); }
            if (actual instanceof PatternMatcher.Port port && !ROLES.contains(port.channel())) { return Optional.empty(); }
        }
        if (manager.current().orElse(null) != catalog || manager.status().generation() != generation) {
            return Optional.empty();
        }
        // World positions, <=4 chunks and actual hatches occupying typed ports are
        // checked later under the real retained-owner guard, never by this value.
        return Optional.of(new ClassicPatternSelection(generation, definition));
    }
}

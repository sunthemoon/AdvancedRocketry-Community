package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClassicLathePatternPolicyTest {
    private final ClassicLathePatternPolicy policy = new ClassicLathePatternPolicy();

    @Test void missingSameIdNeverFallsBackToAnotherRealKernelPattern() {
        var manager = new MultiblockPatternCatalogManager();
        assertTrue(policy.select(manager).isEmpty());
        manager.accept(MultiblockPatternCatalog.create(List.of(pattern("test:other", new PatternMatcher.Port("item_input"), false,
                EnumSet.allOf(PatternRotation.class)))));
        assertTrue(policy.select(manager).isEmpty());
    }

    @Test void allFiveRolesAndOneOptionalWrapperAreAcceptedWithoutWorldAdmission() {
        for (String role : List.of("item_input", "item_output", "fluid_input", "fluid_output", "energy_input")) {
            for (PatternMatcher matcher : List.of(new PatternMatcher.Port(role), new PatternMatcher.OptionalCell(new PatternMatcher.Port(role)))) {
                var definition = pattern("advancedrocketrycommunity:lathe", matcher, false, EnumSet.allOf(PatternRotation.class));
                var manager = manager(definition);
                var selected = policy.select(manager).orElseThrow();
                assertSame(definition, selected.definition()); assertEquals(1, selected.catalogGeneration());
            }
        }
    }

    @Test void kernelValidWrongRolesMirrorsRotationsAndExtraOptionalControllerAreRefused() {
        for (var definition : List.of(
                pattern("advancedrocketrycommunity:lathe", new PatternMatcher.Port("old_port"), false, EnumSet.allOf(PatternRotation.class)),
                pattern("advancedrocketrycommunity:lathe", new PatternMatcher.Port("item_input"), true, EnumSet.allOf(PatternRotation.class)),
                pattern("advancedrocketrycommunity:lathe", new PatternMatcher.Port("item_input"), false, Set.of(PatternRotation.ZERO)),
                pattern("advancedrocketrycommunity:lathe", new PatternMatcher.OptionalCell(new PatternMatcher.Controller()), false,
                        EnumSet.allOf(PatternRotation.class)))) {
            var manager = manager(definition);
            assertTrue(policy.select(manager).isEmpty()); assertSame(definition, manager.current().orElseThrow().get(definition.id()).orElseThrow());
        }
    }

    @Test void selectionRetainsImmutableDefinitionAndGenerationWithoutForkingReloadState() {
        var first = pattern("advancedrocketrycommunity:lathe", new PatternMatcher.Port("item_input"), false, EnumSet.allOf(PatternRotation.class));
        var manager = manager(first); var captured = policy.select(manager).orElseThrow();
        manager.reject("rejected kernel reload");
        assertSame(first, policy.select(manager).orElseThrow().definition());
        assertEquals(1, policy.select(manager).orElseThrow().catalogGeneration());
        var next = pattern("advancedrocketrycommunity:lathe", new PatternMatcher.Port("fluid_input"), false, EnumSet.allOf(PatternRotation.class));
        manager.accept(MultiblockPatternCatalog.create(List.of(next)));
        assertSame(first, captured.definition()); assertEquals(1, captured.catalogGeneration());
        assertSame(next, policy.select(manager).orElseThrow().definition()); assertEquals(2, policy.select(manager).orElseThrow().catalogGeneration());
    }

    private static MultiblockPatternCatalogManager manager(MultiblockPatternDefinition definition) {
        var result = new MultiblockPatternCatalogManager(); result.accept(MultiblockPatternCatalog.create(List.of(definition))); return result;
    }

    private static MultiblockPatternDefinition pattern(String id, PatternMatcher second, boolean mirror, Set<PatternRotation> rotations) {
        var anchor = new PatternPosition(0, 0, 0);
        return new MultiblockPatternDefinition(id, 1, 256, new PatternSize(2, 1, 1), anchor, rotations, mirror,
                Map.of(anchor, new PatternMatcher.Controller(), new PatternPosition(1, 0, 0), second));
    }
}

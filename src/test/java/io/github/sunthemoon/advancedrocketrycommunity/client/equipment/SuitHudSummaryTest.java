package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.client.equipment.SuitHudClient.Summary;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The bounded display summary behind the hydrogen and suit HUD panels (ADR-066 section 7.1). */
class SuitHudSummaryTest {
    @Test
    void emptySummaryShowsNeitherPanel() {
        assertFalse(Summary.EMPTY.showsHydrogen());
        assertFalse(Summary.EMPTY.showsSuit());
        assertEquals(List.of(), Summary.EMPTY.modules());
    }

    @Test
    void aHydrogenTankOrAJetpackShowsTheHydrogenPanel() {
        Summary tank = new Summary(250, 2_000, List.of(ModuleKind.PRESSURE_TANK));
        assertTrue(tank.showsHydrogen());
        assertTrue(tank.showsSuit());
        Summary jetpackWithoutHydrogen = new Summary(0, 0, List.of(ModuleKind.JETPACK));
        assertTrue(jetpackWithoutHydrogen.showsHydrogen());
        Summary visorOnly = new Summary(0, 0, List.of(ModuleKind.ANTI_FOG));
        assertFalse(visorOnly.showsHydrogen());
        assertTrue(visorOnly.showsSuit());
    }

    @Test
    void reservesAndModuleListsStayWithinTheirFiniteRange() {
        assertThrows(IllegalArgumentException.class, () -> new Summary(-1, 10, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Summary(11, 10, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Summary(0, -1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new Summary(0, 32_001, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Summary(0, 0, List.of(ModuleKind.HOVER, ModuleKind.HOVER)));
        assertThrows(NullPointerException.class, () -> new Summary(0, 0, null));
        assertThrows(NullPointerException.class, () -> new Summary(0, 0, Arrays.asList(ModuleKind.HOVER, null)));
        Summary full = new Summary(32_000, 32_000, List.of(ModuleKind.values()));
        assertEquals(ModuleKind.values().length, full.modules().size());
    }

    @Test
    void theModuleListIsAnImmutableCopy() {
        List<ModuleKind> kinds = new ArrayList<>(List.of(ModuleKind.JETPACK));
        Summary summary = new Summary(0, 0, kinds);
        kinds.add(ModuleKind.HOVER);
        assertEquals(List.of(ModuleKind.JETPACK), summary.modules());
        assertThrows(UnsupportedOperationException.class, () -> summary.modules().add(ModuleKind.HOVER));
    }
}

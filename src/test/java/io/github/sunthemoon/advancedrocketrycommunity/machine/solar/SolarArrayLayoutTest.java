package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SolarArrayLayoutTest {
    private static final PatternPosition ORIGIN = new PatternPosition(100, 64, 100);
    private static final PatternBlock AIR = new PatternBlock("minecraft:air", Set.of(), true, false, Optional.empty());
    private static final PatternBlock PANEL = new PatternBlock(SolarArrayLayout.PANEL, Set.of(SolarArrayLayout.CELL_TAG), false, false, Optional.empty());
    private static final PatternBlock CASING = new PatternBlock("advancedrocketrycommunity:machine_casing", Set.of(SolarArrayLayout.CELL_TAG), false, false, Optional.empty());
    private static Map<PatternPosition, PatternBlock> world(PatternTransform transform) {
        Map<PatternPosition, PatternBlock> world = new HashMap<>();
        for (var cell : SolarArrayLayout.cells()) {
            var block = cell.local().equals(SolarArrayLayout.ANCHOR)
                    ? new PatternBlock("advancedrocketrycommunity:solar_array", Set.of(), false, true, Optional.empty())
                    : cell.collector() ? PANEL : new PatternBlock("advancedrocketrycommunity:power_output_plug", Set.of(), false, false, Optional.of(SolarArrayLayout.OUTPUT_CHANNEL));
            world.put(transform.localToWorld(cell.local(), SolarArrayLayout.ANCHOR, ORIGIN), block);
        }
        return world;
    }
    @Test void allFourRotationsInspectExactly66AndCount63() {
        for (var rotation : PatternRotation.values()) {
            var transform = new PatternTransform(rotation, false); var world = world(transform);
            var result = SolarArrayLayout.evaluate(transform, ORIGIN, pos -> PatternObservation.loaded(world.get(pos)));
            assertTrue(result.formed()); assertEquals(66, result.inspected()); assertEquals(63, result.collectors());
            assertEquals(Set.copyOf(SolarArrayLayout.cells().stream().filter(SolarArrayLayout.Cell::collector)
                    .map(cell -> transform.localToWorld(cell.local(), SolarArrayLayout.ANCHOR, ORIGIN)).toList()), Set.copyOf(result.panels()));
            assertEquals(2, result.outputs().size()); assertEquals(66, Set.copyOf(SolarArrayLayout.footprint(transform, ORIGIN)).size());
        }
        assertThrows(IllegalArgumentException.class, () -> SolarArrayLayout.footprint(new PatternTransform(PatternRotation.ZERO, true), ORIGIN));
    }
    @Test void optionalCellsNeedAtLeastOnePanelAndNeverCountCasing() {
        var transform = new PatternTransform(PatternRotation.ZERO, false); var world = world(transform);
        for (var cell : SolarArrayLayout.cells()) {
            if (cell.collector()) { world.put(transform.localToWorld(cell.local(), SolarArrayLayout.ANCHOR, ORIGIN), cell.local().x() == 0 ? CASING : AIR); }
        }
        assertEquals(SolarArrayLayout.Status.NO_COLLECTORS, SolarArrayLayout.evaluate(transform, ORIGIN, pos -> PatternObservation.loaded(world.get(pos))).status());
        world.put(ORIGIN.add(new PatternPosition(0, 0, 1)), PANEL);
        var result = SolarArrayLayout.evaluate(transform, ORIGIN, pos -> PatternObservation.loaded(world.get(pos)));
        assertEquals(1, result.collectors()); assertEquals(java.util.List.of(ORIGIN.add(new PatternPosition(0, 0, 1))), result.panels());
    }
    @Test void mismatchesAndUnloadedCellsFailClosedWithUnloadedPrecedence() {
        var transform = new PatternTransform(PatternRotation.ZERO, false); var world = world(transform);
        var bad = ORIGIN.add(new PatternPosition(0, 0, 2));
        world.put(bad, new PatternBlock("minecraft:stone", Set.of(), false, false, Optional.empty()));
        assertEquals(SolarArrayLayout.Status.MISMATCH, SolarArrayLayout.evaluate(transform, ORIGIN, pos -> PatternObservation.loaded(world.get(pos))).status());
        var unloaded = ORIGIN.add(new PatternPosition(0, 0, 21));
        var result = SolarArrayLayout.evaluate(transform, ORIGIN, pos -> pos.equals(unloaded) ? PatternObservation.unloaded() : PatternObservation.loaded(world.get(pos)));
        assertEquals(SolarArrayLayout.Status.UNLOADED, result.status()); assertEquals(0, result.collectors());
        assertTrue(result.panels().isEmpty()); assertTrue(result.outputs().isEmpty());
    }
}

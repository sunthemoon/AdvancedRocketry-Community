package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.EquipmentSummary;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleLayoutTest {
    private static ModuleLayout.Entry entry(int position, ModuleKind kind, TankData.Gas gas) { return new ModuleLayout.Entry(position, kind, gas); }
    @Test void tableHasFixedDistinctPositionsAndNoForeignItemAliases() {
        assertEquals(2, SuitPart.HEAD.positions()); assertEquals(3, SuitPart.CHEST.positions());
        assertEquals(2, SuitPart.LEGS.positions()); assertEquals(1, SuitPart.FEET.positions());
        for (ModuleKind kind : ModuleKind.values()) {
            for (SuitPart part : SuitPart.values()) {
                for (int i = -1; i <= 3; i++) {
                    boolean expected = kind.part() == part && i >= 0 && i < part.positions()
                            && (part != SuitPart.CHEST || (kind == ModuleKind.JETPACK ? i == 0 : i > 0));
                    assertEquals(expected, kind.fits(part, i));
                }
            }
            for (String id : kind.itemPaths()) { assertEquals(kind, ModuleKind.forItem("advancedrocketrycommunity:" + id)); }
            assertEquals(kind, ModuleKind.forStableId(kind.stableId()));
        }
        assertNull(ModuleKind.forItem("other:jetpack")); assertNull(ModuleKind.forStableId("future"));
    }
    @Test void duplicatesOverflowAndIncompatiblePiecesRefuse() {
        assertTrue(ModuleLayout.valid(SuitPart.HEAD, List.of(entry(0, ModuleKind.HOVER, TankData.Gas.EMPTY), entry(1, ModuleKind.BEACON_FINDER, TankData.Gas.EMPTY))));
        assertFalse(ModuleLayout.valid(SuitPart.HEAD, List.of(entry(0, ModuleKind.HOVER, TankData.Gas.EMPTY), entry(1, ModuleKind.HOVER, TankData.Gas.EMPTY))));
        assertFalse(ModuleLayout.valid(SuitPart.HEAD, List.of(entry(0, ModuleKind.HOVER, TankData.Gas.EMPTY), entry(0, ModuleKind.ANTI_FOG, TankData.Gas.EMPTY))));
        assertFalse(ModuleLayout.valid(SuitPart.FEET, List.of(entry(0, ModuleKind.PADDED, TankData.Gas.EMPTY), entry(1, ModuleKind.PADDED, TankData.Gas.EMPTY))));
        assertFalse(ModuleLayout.valid(SuitPart.HEAD, List.of(entry(0, ModuleKind.FLIGHT_SPEED, TankData.Gas.EMPTY))));
        assertFalse(ModuleLayout.valid(SuitPart.LEGS, List.of(entry(0, ModuleKind.BIONIC, TankData.Gas.OXYGEN))));
    }
    @Test void chestAllowsOnlyOneOfEachGasAndTwoTanksAlongsideJetpack() {
        var jet = entry(0, ModuleKind.JETPACK, TankData.Gas.EMPTY);
        var oxygen = entry(1, ModuleKind.PRESSURE_TANK, TankData.Gas.OXYGEN);
        var hydrogen = entry(2, ModuleKind.PRESSURE_TANK, TankData.Gas.HYDROGEN);
        assertTrue(ModuleLayout.valid(SuitPart.CHEST, List.of(jet, oxygen, hydrogen)));
        assertFalse(ModuleLayout.valid(SuitPart.CHEST, List.of(jet, oxygen, entry(2, ModuleKind.PRESSURE_TANK, TankData.Gas.OXYGEN))));
        assertFalse(ModuleLayout.valid(SuitPart.CHEST, List.of(entry(0, ModuleKind.PRESSURE_TANK, TankData.Gas.EMPTY))));
        assertFalse(ModuleLayout.valid(SuitPart.CHEST, List.of(entry(1, ModuleKind.JETPACK, TankData.Gas.EMPTY))));
        assertTrue(ModuleLayout.valid(SuitPart.CHEST, List.of(entry(1, ModuleKind.PRESSURE_TANK, TankData.Gas.EMPTY), entry(2, ModuleKind.PRESSURE_TANK, TankData.Gas.EMPTY))));
    }
    @Test void immutableSummaryPublishesOnlyEquipmentFacts() {
        Set<ModuleKind> source = new HashSet<>(Set.of(ModuleKind.BEACON_FINDER));
        EquipmentSummary summary = new EquipmentSummary(source); source.clear();
        assertTrue(summary.beaconFinderEnabled()); assertFalse(EquipmentSummary.EMPTY.beaconFinderEnabled());
        assertThrows(UnsupportedOperationException.class, () -> summary.installed().clear());
    }
}

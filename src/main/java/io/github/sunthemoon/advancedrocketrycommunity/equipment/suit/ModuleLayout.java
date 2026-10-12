package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import java.util.HashSet;
import java.util.List;

/** Small pure validation shared by persistence and all workstation operations. */
public final class ModuleLayout {
    public record Entry(int position, ModuleKind kind, TankData.Gas gas) { }
    public static boolean valid(SuitPart part, List<Entry> entries) {
        if (part == null || entries.size() > part.positions()) { return false; }
        var positions = new HashSet<Integer>();
        var upgrades = new HashSet<ModuleKind>();
        var gases = new HashSet<TankData.Gas>();
        int tanks = 0;
        for (Entry entry : entries) {
            if (entry == null || entry.kind() == null || entry.gas() == null
                    || !entry.kind().fits(part, entry.position()) || !positions.add(entry.position())) { return false; }
            if (entry.kind() == ModuleKind.PRESSURE_TANK) {
                if (++tanks > 2 || entry.gas() != TankData.Gas.EMPTY && !gases.add(entry.gas())) { return false; }
            } else if (entry.gas() != TankData.Gas.EMPTY || !upgrades.add(entry.kind())) { return false; }
        }
        return true;
    }
    private ModuleLayout() { }
}

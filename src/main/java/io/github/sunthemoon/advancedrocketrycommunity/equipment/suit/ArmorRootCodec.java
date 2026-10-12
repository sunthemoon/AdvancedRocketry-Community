package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** The only persisted module/gas owner; legacy active oxygen stays on its original key. */
public final class ArmorRootCodec {
    public static final String KEY = "arce_suit_equipment";
    private static final Set<String> ROOT_KEYS = Set.of("schema_version", "modules");
    private static final Set<String> ENTRY_KEYS = Set.of("position", "kind", "stack");
    public record Module(int position, ModuleKind kind, CompoundTag stack) { }

    /** Null means refused, empty means a valid old suit with no modules. Returned trees are detached. */
    public static List<Module> read(CompoundTag armor, SuitPart part) {
        if (armor == null || !armor.contains(KEY)) { return List.of(); }
        Tag raw = armor.get(KEY);
        if (!(raw instanceof CompoundTag root) || !EquipmentNbt.bounded(root)
                || !root.getAllKeys().equals(ROOT_KEYS) || !root.contains("schema_version", Tag.TAG_INT)
                || root.getInt("schema_version") != 1 || !root.contains("modules", Tag.TAG_LIST)) { return null; }
        ListTag list = (ListTag) root.get("modules");
        if (list.size() > part.positions() || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) { return null; }
        List<Module> result = new ArrayList<>();
        List<ModuleLayout.Entry> layout = new ArrayList<>();
        for (Tag value : list) {
            CompoundTag entry = (CompoundTag) value;
            if (!entry.getAllKeys().equals(ENTRY_KEYS) || !entry.contains("position", Tag.TAG_INT)
                    || !entry.contains("kind", Tag.TAG_STRING) || !entry.contains("stack", Tag.TAG_COMPOUND)) { return null; }
            ModuleKind kind = ModuleKind.forStableId(entry.getString("kind"));
            CompoundTag stack = entry.getCompound("stack");
            if (kind == null || !validStack(stack) || kind != ModuleKind.forItem(stack.getString("id"))) { return null; }
            TankData.Gas gas = TankData.Gas.EMPTY;
            if (kind == ModuleKind.PRESSURE_TANK) {
                TankData tank = TankData.read(stack.getCompound("tag").get(TankData.KEY),
                        TankData.baseCapacity(stack.getString("id")), 1);
                // Installed tanks must already carry their creation snapshot.
                if (tank == null || !stack.getCompound("tag").contains(TankData.KEY)) { return null; }
                gas = tank.gas();
            }
            int position = entry.getInt("position");
            layout.add(new ModuleLayout.Entry(position, kind, gas));
            result.add(new Module(position, kind, stack));
        }
        if (!ModuleLayout.valid(part, layout)) { return null; }
        return result.stream().map(m -> new Module(m.position(), m.kind(), m.stack().copy())).toList();
    }

    public static boolean validStack(CompoundTag stack) {
        return EquipmentNbt.bounded(stack) && Set.of("id", "Count", "tag", "ForgeCaps").containsAll(stack.getAllKeys())
                && stack.contains("id", Tag.TAG_STRING) && ModuleKind.forItem(stack.getString("id")) != null
                && stack.contains("Count", Tag.TAG_BYTE) && stack.getByte("Count") == 1
                && (!stack.contains("tag") || stack.contains("tag", Tag.TAG_COMPOUND))
                && (!stack.contains("ForgeCaps") || stack.contains("ForgeCaps", Tag.TAG_COMPOUND));
    }

    public static CompoundTag encode(List<Module> modules) {
        if (modules.size() > 3) { return null; }
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", 1);
        ListTag list = new ListTag();
        for (Module module : modules) {
            if (!EquipmentNbt.bounded(module.stack())) { return null; }
            CompoundTag entry = new CompoundTag();
            entry.putInt("position", module.position()); entry.putString("kind", module.kind().stableId());
            entry.put("stack", module.stack().copy()); list.add(entry);
        }
        root.put("modules", list);
        return EquipmentNbt.bounded(root) ? root : null;
    }
    private ArmorRootCodec() { }
}

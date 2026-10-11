package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ArmorRootCodecTest {
    private static CompoundTag component(String id) {
        CompoundTag stack = new CompoundTag(); stack.putString("id", "advancedrocketrycommunity:" + id); stack.putByte("Count", (byte) 1);
        CompoundTag tag = new CompoundTag(); tag.putInt("Damage", 7); tag.putString("unrelated", "keep");
        CompoundTag other = new CompoundTag(); other.putString("owner", "some-mod"); tag.put("other", other); stack.put("tag", tag);
        return stack;
    }
    private static CompoundTag outer(List<ArmorRootCodec.Module> modules) {
        CompoundTag outer = new CompoundTag(); outer.put(ArmorRootCodec.KEY, ArmorRootCodec.encode(modules)); return outer;
    }
    @Test void oldSuitKeepsItsActiveKeyAndUnrelatedArmorDataWithoutModules() {
        CompoundTag armor = new CompoundTag(); armor.putString("name", "old-suit");
        CompoundTag oxygen = new CompoundTag(); oxygen.putInt("schema_version", 1); oxygen.putInt("oxygen_units", 1234);
        armor.put("arce_space_suit_oxygen", oxygen); CompoundTag before = armor.copy();
        assertEquals(List.of(), ArmorRootCodec.read(armor, SuitPart.CHEST)); assertEquals(before, armor);
        assertEquals(List.of(), ArmorRootCodec.read(null, SuitPart.HEAD));
    }
    @Test void moduleRoundTripPreservesDurabilityGasAndForeignTagsWithoutAliasing() {
        CompoundTag tank = component("super_high_pressure_tank");
        TankData gas = new TankData(TankData.Gas.HYDROGEN, 4321, 32_000);
        tank.getCompound("tag").put(TankData.KEY, gas.encode());
        CompoundTag armor = outer(List.of(new ArmorRootCodec.Module(1, ModuleKind.PRESSURE_TANK, tank)));
        armor.putString("customName", "astronaut"); CompoundTag before = armor.copy();
        var loaded = ArmorRootCodec.read(armor, SuitPart.CHEST); assertNotNull(loaded);
        assertEquals(tank, loaded.get(0).stack()); assertEquals(before.get(ArmorRootCodec.KEY), ArmorRootCodec.encode(loaded));
        loaded.get(0).stack().getCompound("tag").putInt("Damage", 100);
        assertEquals(before, armor); assertEquals(7, tank.getCompound("tag").getInt("Damage"));
    }
    @Test void wrongTypesFutureVersionsUnknownIdsAndOverlargeRootsStayIntact() {
        CompoundTag armor = outer(List.of(new ArmorRootCodec.Module(0, ModuleKind.HOVER, component("hover_upgrade"))));
        armor.getCompound(ArmorRootCodec.KEY).putInt("schema_version", 2); CompoundTag before = armor.copy();
        assertNull(ArmorRootCodec.read(armor, SuitPart.HEAD)); assertEquals(before, armor);
        armor.getCompound(ArmorRootCodec.KEY).putInt("schema_version", 1);
        armor.getCompound(ArmorRootCodec.KEY).putInt("modules", 1); assertNull(ArmorRootCodec.read(armor, SuitPart.HEAD));
        armor.putString(ArmorRootCodec.KEY, "bad"); assertNull(ArmorRootCodec.read(armor, SuitPart.HEAD));
        CompoundTag future = component("future_module"); assertFalse(ArmorRootCodec.validStack(future));
        CompoundTag huge = component("hover_upgrade"); huge.getCompound("tag").putByteArray("foreign", new byte[16_384]);
        assertNull(ArmorRootCodec.encode(List.of(new ArmorRootCodec.Module(0, ModuleKind.HOVER, huge))));
    }
    @Test void codecRejectsDuplicateKindsGasAndNonunitCounts() {
        assertNull(ArmorRootCodec.read(outer(List.of(new ArmorRootCodec.Module(0, ModuleKind.HOVER, component("hover_upgrade")),
                new ArmorRootCodec.Module(1, ModuleKind.HOVER, component("hover_upgrade")))), SuitPart.HEAD));
        CompoundTag stack = component("low_pressure_tank"); stack.getCompound("tag").put(TankData.KEY, new TankData(TankData.Gas.OXYGEN, 500, 1000).encode());
        assertNull(ArmorRootCodec.read(outer(List.of(new ArmorRootCodec.Module(1, ModuleKind.PRESSURE_TANK, stack),
                new ArmorRootCodec.Module(2, ModuleKind.PRESSURE_TANK, stack))), SuitPart.CHEST));
        stack.putInt("Count", 1); assertFalse(ArmorRootCodec.validStack(stack));
        stack.putByte("Count", (byte) 2); assertFalse(ArmorRootCodec.validStack(stack));
    }
    @Test void enchantedChestHasStrictFiniteDataAndPreservesUnsupportedInput() {
        CompoundTag armor = new CompoundTag(); assertEquals(0, EnchantedSuitOxygen.read(armor));
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putInt("oxygen_units", 2000);
        armor.put(EnchantedSuitOxygen.KEY, root); assertEquals(2000, EnchantedSuitOxygen.read(armor));
        root.putInt("oxygen_units", 2001); CompoundTag before = armor.copy();
        assertEquals(-1, EnchantedSuitOxygen.read(armor)); assertEquals(before, armor);
        root.putInt("schema_version", 2); root.putInt("oxygen_units", 10); assertEquals(-1, EnchantedSuitOxygen.read(armor));
    }
}

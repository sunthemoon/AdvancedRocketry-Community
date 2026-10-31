package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.SuitPart;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TankCanisterRefillTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static ArmorRootCodec.Module tank(int position, TankData.Gas gas, int amount) {
        CompoundTag stack = new CompoundTag(); stack.putString("id", "advancedrocketrycommunity:pressure_tank");
        stack.putByte("Count", (byte) 1); CompoundTag tag = new CompoundTag(); tag.putString("marker", "keep");
        tag.put(TankData.KEY, new TankData(gas, amount, 2000).encode()); stack.put("tag", tag);
        return new ArmorRootCodec.Module(position, ModuleKind.PRESSURE_TANK, stack);
    }
    private static CompoundTag armor(ArmorRootCodec.Module... modules) {
        CompoundTag root = new CompoundTag(); root.put(ArmorRootCodec.KEY, ArmorRootCodec.encode(List.of(modules)));
        root.putString("marker", "chest"); return root;
    }
    @Test void eachGasFillsExactlyToCapacityWithoutEditingTheInput() {
        for (var gas : List.of(TankData.Gas.OXYGEN, TankData.Gas.HYDROGEN)) {
            CompoundTag before = armor(tank(1, gas, 1000)); CompoundTag original = before.copy();
            CompoundTag after = TankCanisterRefill.prepare(before, gas);
            assertNotNull(after); assertEquals(original, before); assertEquals("chest", after.getString("marker"));
            var saved = ArmorRootCodec.read(after, SuitPart.CHEST).get(0).stack().getCompound("tag");
            assertEquals("keep", saved.getString("marker")); assertEquals(2000, saved.getCompound(TankData.KEY).getInt("stored"));
            assertEquals(gas.name(), saved.getCompound(TankData.KEY).getString("gas"));
        }
    }
    @Test void matchingTankWinsOverEmptyAndPartialRoomRefusesWithoutFallback() {
        for (var gas : List.of(TankData.Gas.OXYGEN, TankData.Gas.HYDROGEN)) {
            CompoundTag root = armor(tank(1, TankData.Gas.EMPTY, 0), tank(2, gas, 0));
            CompoundTag prepared = TankCanisterRefill.prepare(root, gas); assertNotNull(prepared);
            var after = ArmorRootCodec.read(prepared, SuitPart.CHEST); assertEquals(2, after.size());
            assertEquals("EMPTY", after.get(0).stack().getCompound("tag").getCompound(TankData.KEY).getString("gas"));
            assertEquals(1000, after.get(1).stack().getCompound("tag").getCompound(TankData.KEY).getInt("stored"));
            root = armor(tank(1, TankData.Gas.EMPTY, 0), tank(2, gas, 1001));
            CompoundTag before = root.copy(); assertNull(TankCanisterRefill.prepare(root, gas)); assertEquals(before, root);
        }
    }
    @Test void emptyTankAcquiresOnlyTheRequestedGasInPositionOrder() {
        CompoundTag root = armor(tank(2, TankData.Gas.EMPTY, 0), tank(1, TankData.Gas.EMPTY, 0));
        CompoundTag prepared = TankCanisterRefill.prepare(root, TankData.Gas.HYDROGEN); assertNotNull(prepared);
        var after = ArmorRootCodec.read(prepared, SuitPart.CHEST); assertEquals(2, after.size());
        for (var module : after) {
            var saved = module.stack().getCompound("tag").getCompound(TankData.KEY);
            assertEquals(module.position() == 1 ? "HYDROGEN" : "EMPTY", saved.getString("gas"));
        }
    }
    @Test void duplicateGasUnsupportedRootsAndAbsentTankRefuseUnchanged() {
        CompoundTag duplicate = armor(tank(1, TankData.Gas.OXYGEN, 0), tank(2, TankData.Gas.OXYGEN, 0));
        CompoundTag future = armor(tank(1, TankData.Gas.EMPTY, 0));
        future.getCompound(ArmorRootCodec.KEY).putInt("schema_version", 2);
        for (CompoundTag root : List.of(duplicate, future, new CompoundTag())) {
            CompoundTag before = root.copy(); assertNull(TankCanisterRefill.prepare(root, TankData.Gas.OXYGEN));
            assertEquals(before, root);
        }
        assertNull(TankCanisterRefill.prepare(armor(tank(1, TankData.Gas.EMPTY, 0)), TankData.Gas.EMPTY));
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CombustionSlotBackgroundTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void fuelAndAllInventorySlotsHaveSeparateFrames() {
        var inventory = new SimpleContainer(37); var slots = new ArrayList<Slot>();
        slots.add(new Slot(inventory, 0, 44, 34));
        for (int i = 0; i < 36; i++) {
            slots.add(new Slot(inventory, i+1, 8 + i%9*18, i < 27 ? 84 + i/9*18 : 142));
        }
        var fills = new ArrayList<int[]>();
        CombustionGeneratorScreen.drawSlots(slots, 20, 30,
                (x,y,r,b,c) -> fills.add(new int[] {x,y,r,b,c}));
        assertEquals(74, fills.size());
        for (int i = 0; i < slots.size(); i++) {
            var s = slots.get(i);
            assertArrayEquals(new int[] {20+s.x-1,30+s.y-1,20+s.x+17,30+s.y+17,0xFFC08A53}, fills.get(2*i));
            assertArrayEquals(new int[] {20+s.x,30+s.y,20+s.x+16,30+s.y+16,0xFF0B1317}, fills.get(2*i+1));
        }
    }
}

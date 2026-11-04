package io.github.sunthemoon.advancedrocketrycommunity.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/** Code-drawn, two-tone frames follow the menu's item coordinates, including the player inventory. */
public final class ContainerSlotBackground {
    public static void draw(GuiGraphics graphics, List<Slot> slots, int left, int top, int edge, int recess) {
        draw(slots, left, top, edge, recess, graphics::fill);
    }

    static void draw(List<Slot> slots, int left, int top, int edge, int recess, Fill fill) {
        for (Slot slot : slots) {
            int x = left + slot.x, y = top + slot.y;
            fill.draw(x - 1, y - 1, x + 17, y + 17, edge);
            fill.draw(x, y, x + 16, y + 16, recess);
        }
    }

    @FunctionalInterface
    interface Fill { void draw(int left, int top, int right, int bottom, int color); }

    private ContainerSlotBackground() { }
}

package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionBurn;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Original steel/copper panel, drawn with primitives rather than a copied GUI sheet. */
public final class CombustionGeneratorScreen extends AbstractContainerScreen<CombustionGeneratorMenu> {
    public CombustionGeneratorScreen(CombustionGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = 73;
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(8, 20, 12, 48, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energy(), CombustionBurn.CAPACITY), mouseX, mouseY);
        }
        if (isHovering(43, 55, 18, 5, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.advancedrocketrycommunity.combustion.burn",
                    menu.remaining(), menu.duration()), mouseX, mouseY);
        }
        if (isHovering(76, 41, 92, 24, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("screen.advancedrocketrycommunity.combustion.status."
                    + menu.status().name().toLowerCase(Locale.ROOT)), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF303C40, 0xFF192226);
        graphics.renderOutline(x, y, imageWidth, imageHeight, 0xFFC08A53);
        graphics.fill(x + 8, y + 20, x + 20, y + 68, 0xFF0B1317);
        int energyHeight = menu.energy() * 48 / CombustionBurn.CAPACITY;
        graphics.fill(x + 8, y + 68 - energyHeight, x + 20, y + 68, 0xFFE5B45B);
        graphics.fill(x + 43, y + 55, x + 61, y + 60, 0xFF0B1317);
        int credit = menu.duration() == 0 ? 0 : (int) Math.min(18L, 18L * menu.remaining() / menu.duration());
        graphics.fill(x + 43, y + 55, x + 43 + credit, y + 60, 0xFFD47743);
        drawSlots(menu.slots, x, y, graphics::fill);
    }

    static void drawSlots(java.util.List<net.minecraft.world.inventory.Slot> slots, int x, int y,
                          ContainerSlotBackground.Fill fill) {
        ContainerSlotBackground.draw(slots, x, y, 0xFFC08A53, 0xFF0B1317, fill);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFF1E7D7, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB4C1C3, false);
        graphics.drawString(font, Component.literal(menu.energy() + " FE"), 76, 25, 0xFFE5B45B, false);
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.combustion.rate"),
                76, 41, 0xFFE5B45B, false);
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.combustion.status."
                + menu.status().name().toLowerCase(Locale.ROOT)),
                76, 55, menu.status() == CombustionBurn.Status.GENERATING ? 0xFF92D3AF : 0xFFB4C1C3, false);
    }
}

package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ElectrolyzerScreen extends AbstractContainerScreen<ElectrolyzerMenu> {
    private static final int PANEL = 0xFF172126;
    private static final int PANEL_EDGE = 0xFF66808A;
    private static final int RECESS = 0xFF0B1114;
    private static final int SLOT_EDGE = 0xFF91A7AE;
    private static final int ENERGY = 0xFFE54F39;
    private static final int WATER = 0xFF3A9BD9;
    private static final int PROGRESS = 0xFF5BD3C7;

    public ElectrolyzerScreen(ElectrolyzerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = 176;
        imageHeight = 166;
        titleLabelX = 8;
        titleLabelY = 7;
        inventoryLabelX = 8;
        inventoryLabelY = 73;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (isHovering(73, 20, 12, 48, mouseX, mouseY)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "tooltip.advancedrocketrycommunity.energy",
                            menu.energyStored(),
                            menu.energyCapacity()
                    ),
                    mouseX,
                    mouseY
            );
        } else if (isHovering(91, 20, 12, 48, mouseX, mouseY)) {
            graphics.renderTooltip(
                    font,
                    Component.translatable(
                            "tooltip.advancedrocketrycommunity.water",
                            menu.waterAmount(),
                            menu.waterCapacity()
                    ),
                    mouseX,
                    mouseY
            );
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF22323A, PANEL);
        graphics.renderOutline(x, y, imageWidth, imageHeight, PANEL_EDGE);
        graphics.fill(x + 4, y + 17, x + imageWidth - 4, y + 71, 0xB50B1114);
        graphics.renderOutline(x + 4, y + 17, imageWidth - 8, 54, 0xFF40545D);

        drawSlot(graphics, x + 43, y + 34);
        drawSlot(graphics, x + 43, y + 58);
        drawSlot(graphics, x + 115, y + 34);
        drawSlot(graphics, x + 139, y + 34);

        drawVerticalGauge(
                graphics,
                x + 73,
                y + 20,
                12,
                48,
                menu.energyStored(),
                menu.energyCapacity(),
                ENERGY
        );
        drawVerticalGauge(
                graphics,
                x + 91,
                y + 20,
                12,
                48,
                menu.waterAmount(),
                menu.waterCapacity(),
                WATER
        );

        int progressWidth = scale(menu.progress(), menu.totalProcessingTicks(), 30);
        graphics.fill(x + 108, y + 59, x + 140, y + 67, RECESS);
        if (progressWidth > 0) {
            graphics.fill(x + 109, y + 60, x + 109 + progressWidth, y + 66, PROGRESS);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFDCE9ED, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFC9D4D7, false);
        ElectrolyzerStatus status = menu.status();
        graphics.drawCenteredString(
                font,
                Component.translatable(status.translationKey()),
                imageWidth / 2,
                72,
                status == ElectrolyzerStatus.INVALID_RECIPE || status == ElectrolyzerStatus.UNSUPPORTED_DATA
                        ? 0xFFFF6B5F
                        : 0xFF9FB4BA
        );
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, RECESS);
    }

    private static void drawVerticalGauge(
            GuiGraphics graphics,
            int x,
            int y,
            int width,
            int height,
            int value,
            int maximum,
            int color
    ) {
        graphics.fill(x, y, x + width, y + height, SLOT_EDGE);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, RECESS);
        int fillHeight = scale(value, maximum, height - 2);
        if (fillHeight > 0) {
            graphics.fill(
                    x + 2,
                    y + height - 1 - fillHeight,
                    x + width - 2,
                    y + height - 1,
                    color
            );
        }
    }

    private static int scale(int value, int maximum, int pixels) {
        if (maximum <= 0 || value <= 0) {
            return 0;
        }
        return Math.min(pixels, (int) ((long) value * pixels / maximum));
    }
}

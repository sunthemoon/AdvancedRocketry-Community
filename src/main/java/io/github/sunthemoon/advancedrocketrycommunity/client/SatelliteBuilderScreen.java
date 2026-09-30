package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Community-authored builder console: components, a server-evaluated blueprint preview and one ASSEMBLE intent. */
public final class SatelliteBuilderScreen extends AbstractContainerScreen<SatelliteBuilderMenu> {
    private static final int PANEL_TOP = 0xFF1B2D24;
    private static final int PANEL_BOTTOM = 0xFF0C1712;
    private static final int EDGE = 0xFF6CB88A;
    private static final int RECESS = 0xFF07100B;
    private static final int TEXT = 0xFFE0F0E6;
    private static final int MUTED = 0xFF8CA897;
    private static final int GREEN = 0xFF6DDB9C;
    private static final int GOLD = 0xFFFFC857;
    private static final int RED = 0xFFFF6B66;

    public SatelliteBuilderScreen(SatelliteBuilderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 224;
        imageHeight = 202;
        titleLabelX = 9;
        titleLabelY = 7;
        inventoryLabelX = 31;
        inventoryLabelY = 108;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable("screen.advancedrocketrycommunity.satellite_builder.assemble"),
                ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SatelliteBuilderMenu.BUTTON_ASSEMBLE);
                    }
                }).bounds(leftPos + 118, topPos + 64, 70, 16).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(197, 18, 18, 44, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), menu.energyCapacity()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 6, y + 17, x + 218, y + 104, 0xC9081109);
        graphics.renderOutline(x + 6, y + 17, 212, 87, 0xFF3B6B50);
        drawSlot(graphics, x + 17, y + 29);
        drawSlot(graphics, x + 17, y + 53);
        for (int index = 0; index < 6; index++) {
            drawSlot(graphics, x + 51 + (index % 3) * 20, y + 29 + (index / 3) * 24);
        }
        drawSlot(graphics, x + 127, y + 29);
        drawSlot(graphics, x + 161, y + 41);
        drawSlot(graphics, x + 197, y + 65);
        int stored = menu.energyStored();
        int capacity = Math.max(1, menu.energyCapacity());
        graphics.fill(x + 199, y + 20, x + 213, y + 60, RECESS);
        int height = Math.min(40, stored * 40 / capacity);
        graphics.fill(x + 199, y + 60 - height, x + 213, y + 60, GOLD);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        SatelliteOperationCode preview = menu.previewCode();
        var kind = menu.previewKind();
        Component kindText = kind.map(value -> Component.translatable("satellite_kind.advancedrocketrycommunity." + value.id()))
                .orElse(Component.translatable("screen.advancedrocketrycommunity.satellite_builder.no_kind"));
        graphics.drawString(font, kindText, 12, 80, TEXT, false);
        SatelliteStats stats = menu.previewStats().orElse(null);
        if (stats != null) {
            graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.satellite_builder.stats",
                    stats.power(), stats.battery(), stats.data(), stats.cargo(), stats.rating()), 12, 91, MUTED, false);
        }
        int color = preview == SatelliteOperationCode.SUCCESS ? GREEN : RED;
        Component status = Component.translatable(menu.status() == SatelliteOperationCode.SUCCESS
                ? preview.translationKey() : menu.status().translationKey());
        graphics.drawString(font, font.plainSubstrByWidth(status.getString(), 108), 110, 88, color, false);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, RECESS);
        graphics.renderOutline(x, y, 18, 18, 0xFF3B6B50);
    }
}

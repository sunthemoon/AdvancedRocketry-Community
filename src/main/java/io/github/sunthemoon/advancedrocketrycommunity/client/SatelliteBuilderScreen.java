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
    private final ScreenText text = new ScreenText();
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
        addRenderableWidget(ScreenText.fitButton(Button.builder(Component.translatable("screen.advancedrocketrycommunity.satellite_builder.assemble"),
                ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SatelliteBuilderMenu.BUTTON_ASSEMBLE);
                    }
                }).bounds(leftPos + 118, topPos + 64, 70, 16).build(), font));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        text.clear();
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(197, 18, 18, 44, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), menu.energyCapacity()), mouseX, mouseY);
        }
        text.renderTooltip(graphics, font, leftPos, topPos, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 6, y + 17, x + 218, y + 104, 0xC9081109);
        graphics.renderOutline(x + 6, y + 17, 212, 87, 0xFF3B6B50);
        ContainerSlotBackground.draw(graphics, menu.slots, x, y, 0xFF709B80, RECESS);
        int stored = menu.energyStored();
        int capacity = Math.max(1, menu.energyCapacity());
        graphics.fill(x + 199, y + 20, x + 213, y + 60, RECESS);
        int height = Math.min(40, stored * 40 / capacity);
        graphics.fill(x + 199, y + 60 - height, x + 213, y + 60, GOLD);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        text.draw(graphics, font, title, titleLabelX, titleLabelY, 206, TEXT);
        text.draw(graphics, font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 184, MUTED);
        SatelliteOperationCode preview = menu.previewCode();
        var kind = menu.previewKind();
        Component kindText = kind.map(value -> Component.translatable("satellite_kind.advancedrocketrycommunity." + value.id()))
                .orElse(Component.translatable("screen.advancedrocketrycommunity.satellite_builder.no_kind"));
        if (kind.isEmpty()) {
            text.wrapped(graphics, font, kindText, 12, SatelliteScreenLayout.BUILDER_HINT_Y,
                    SatelliteScreenLayout.BUILDER_HINT_WIDTH, SatelliteScreenLayout.BUILDER_HINT_ROWS, TEXT);
        } else {
            text.draw(graphics, font, kindText, 12, 80, 180, TEXT);
            SatelliteStats stats = menu.previewStats().orElse(null);
            if (stats != null) {
                text.draw(graphics, font, Component.translatable("screen.advancedrocketrycommunity.satellite_builder.stats",
                        stats.power(), stats.battery(), stats.data(), stats.cargo(), stats.rating()), 12, 91, 202, MUTED);
            }
        }
        SatelliteOperationCode code = SatelliteScreenLabels.builderStatus(menu.status(), preview);
        int color = code == SatelliteOperationCode.SUCCESS ? GREEN : RED;
        Component status = Component.translatable(SatelliteScreenLabels.builderKey(code, preview))
                .append(" [" + code.name() + "]");
        text.draw(graphics, font, status, 12, 19, 180, color);
    }

}

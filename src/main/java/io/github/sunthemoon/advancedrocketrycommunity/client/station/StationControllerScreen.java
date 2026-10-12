package io.github.sunthemoon.advancedrocketrycommunity.client.station;

import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored panel for the four ADR-065 station controllers: the menu's fixed buttons send only button
 * IDs; the lines and the last action's stable code come from server data slots, never colour alone.
 */
public final class StationControllerScreen extends AbstractContainerScreen<StationControllerMenu> {
    private static final int PANEL_TOP = 0xFF1B2430;
    private static final int PANEL_BOTTOM = 0xFF0D1218;
    private static final int EDGE = 0xFF8FB8D8;
    private static final int TEXT = 0xFFE8F0F8;
    private static final int MUTED = 0xFFA6B6C6;
    private static final int GOOD = 0xFF8FD8A8;
    private static final int WARNING = 0xFFFFC857;

    public StationControllerScreen(StationControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 210;
        imageHeight = 170;
    }

    @Override
    protected void init() {
        super.init();
        for (StationControllerMenu.Button spec : menu.buttons()) {
            addRenderableWidget(Button.builder(Component.translatable(spec.key()), ignored -> {
                if (minecraft != null && minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, spec.id());
                }
            }).bounds(leftPos + spec.x(), topPos + spec.y(), spec.width(), 16).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fillGradient(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, EDGE);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        int row = 96;
        for (Component line : menu.lines()) {
            if (row > imageHeight - 34) {
                break;
            }
            graphics.drawString(font, line, 8, row, MUTED, false);
            row += 11;
        }
        Optional<StationManagementCode> status = menu.status();
        status.ifPresent(code -> graphics.drawWordWrap(font, Component.translatable(
                "screen.advancedrocketrycommunity.station_controller.status",
                Component.translatable(code.translationKey()), code.name()), 8, imageHeight - 26, imageWidth - 16,
                code.success() ? GOOD : WARNING));
    }
}

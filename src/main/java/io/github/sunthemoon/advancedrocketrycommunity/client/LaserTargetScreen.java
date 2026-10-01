package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored laser target panel (ADR-055 section 5): the drop buffer, the endpoint state and the drill's
 * last code with their stable codes, the cursor and link as a tooltip, and the reset button.
 */
public final class LaserTargetScreen extends AbstractContainerScreen<LaserTargetMenu> {
    private static final int PANEL_TOP = 0xFF1B1F2A;
    private static final int PANEL_BOTTOM = 0xFF0D1016;
    private static final int EDGE = 0xFF7FD3FF;
    private static final int RECESS = 0xFF07090D;
    private static final int TEXT = 0xFFE8F1FA;
    private static final int MUTED = 0xFF93A4B8;
    private static final int BEAM = 0xFF5FC8FF;
    private static final int WARNING = 0xFFFFC857;

    public LaserTargetScreen(LaserTargetMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 186;
        inventoryLabelY = 93;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(Button.builder(Component.translatable(
                        "screen.advancedrocketrycommunity.laser_target.reset"), ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, LaserTargetMenu.BUTTON_RESET);
                    }
                }).bounds(leftPos + 118, topPos + 4, 50, 12).build());
    }

    @Override
    public void removed() {
        super.removed();
        EndgameDeviceViewCache.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isPresent() && isHovering(8, 74, 160, 18, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            for (EndgameDeviceView.Line line : view.get().lines()) {
                Component value = line.translated() ? Component.translatable(line.value())
                        : Component.literal(line.value());
                lines.add(Component.translatable(line.label()).append(": ").append(value));
            }
            graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        for (int slot = 0; slot < LaserTargetBlockEntity.BUFFER_SLOTS; slot++) {
            int slotX = x + 7 + (slot % 9) * 18;
            int slotY = y + 17 + (slot / 9) * 18;
            graphics.fill(slotX, slotY, slotX + 18, slotY + 18, RECESS);
            graphics.renderOutline(slotX, slotY, 18, 18, 0xFF2E4A60);
        }
        for (int row = 0; row < 4; row++) {
            int rowY = y + (row < 3 ? 103 + row * 18 : 161);
            for (int column = 0; column < 9; column++) {
                graphics.fill(x + 7 + column * 18, rowY, x + 25 + column * 18, rowY + 18, RECESS);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isEmpty()) {
            return;
        }
        EndgameCode status = view.get().status();
        graphics.drawString(font, statusLine(status), 8, 74, status == EndgameCode.OK ? BEAM : WARNING, false);
        if (view.get().lastStop() != EndgameCode.OK) {
            graphics.drawString(font, statusLine(view.get().lastStop()), 8, 84, MUTED, false);
        }
    }

    private static Component statusLine(EndgameCode code) {
        return Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY, Component.translatable(code.translationKey()),
                code.name());
    }
}

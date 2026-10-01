package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored gravity field panel (ADR-058 section 6): energy, start and stop, redstone, radius and
 * multiplier steps, and every view line (settings, upkeep, the clipped field size) with the status and its stable
 * code, never colour alone.
 */
public final class GravityFieldScreen extends AbstractContainerScreen<GravityFieldMenu> {
    private static final int PANEL_TOP = 0xFF221B2E;
    private static final int PANEL_BOTTOM = 0xFF100C16;
    private static final int EDGE = 0xFFC9A2FF;
    private static final int RECESS = 0xFF09070D;
    private static final int TEXT = 0xFFF0E8FA;
    private static final int MUTED = 0xFFA898BC;
    private static final int FIELD = 0xFFB98CFF;
    private static final int WARNING = 0xFFFFC857;

    public GravityFieldScreen(GravityFieldMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 150;
    }

    @Override
    protected void init() {
        super.init();
        String key = "screen.advancedrocketrycommunity.gravity_field_controller.";
        button(GravityFieldMenu.BUTTON_START, key + "start", 24, 18, 46);
        button(GravityFieldMenu.BUTTON_STOP, key + "stop", 72, 18, 46);
        button(GravityFieldMenu.BUTTON_REDSTONE, key + "redstone", 120, 18, 50);
        button(GravityFieldMenu.BUTTON_RADIUS_DOWN, key + "radius_down", 24, 34, 72);
        button(GravityFieldMenu.BUTTON_RADIUS_UP, key + "radius_up", 98, 34, 72);
        button(GravityFieldMenu.BUTTON_MULTIPLIER_DOWN, key + "multiplier_down", 24, 50, 72);
        button(GravityFieldMenu.BUTTON_MULTIPLIER_UP, key + "multiplier_up", 98, 50, 72);
    }

    private void button(int id, String key, int x, int y, int width) {
        addRenderableWidget(Button.builder(Component.translatable(key), ignored -> {
            if (minecraft != null && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
            }
        }).bounds(leftPos + x, topPos + y, width, 14).build());
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
        if (isHovering(8, 18, 10, 46, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), GravityFieldBlockEntity.ENERGY_CAPACITY), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 8, y + 18, x + 18, y + 64, RECESS);
        int height = (int) Math.min(46L, (long) menu.energyStored() * 46L / GravityFieldBlockEntity.ENERGY_CAPACITY);
        graphics.fill(x + 8, y + 64 - height, x + 18, y + 64, FIELD);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isEmpty()) {
            return;
        }
        EndgameCode status = view.get().status();
        graphics.drawString(font, Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY,
                Component.translatable(status.translationKey()), status.name()), 8, 70,
                status == EndgameCode.OK ? FIELD : WARNING, false);
        int row = 82;
        for (EndgameDeviceView.Line line : view.get().lines()) {
            if (row > imageHeight - 10) {
                break;
            }
            Component value = line.translated() ? Component.translatable(line.value()) : Component.literal(line.value());
            graphics.drawString(font, Component.translatable(line.label()).append(": ").append(value), 8, row, MUTED,
                    false);
            row += 10;
        }
    }
}

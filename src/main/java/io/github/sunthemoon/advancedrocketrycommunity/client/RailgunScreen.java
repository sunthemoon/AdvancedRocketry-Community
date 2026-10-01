package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceViewCache;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Community-authored railgun panel (ADR-056 section 6): energy, the 4 input and 9 receive slots, the destination
 * selection, launch, auto, redstone and minimum stack size buttons, and the status with its stable code from the
 * device view (hover for the destination, route, settings and counts), so no state is shown by colour alone.
 */
public final class RailgunScreen extends AbstractContainerScreen<RailgunMenu> {
    private static final int PANEL_TOP = 0xFF1B1F2A;
    private static final int PANEL_BOTTOM = 0xFF0D1016;
    private static final int EDGE = 0xFF7FD3FF;
    private static final int RECESS = 0xFF07090D;
    private static final int TEXT = 0xFFE8F1FA;
    private static final int MUTED = 0xFF93A4B8;
    private static final int CHARGE = 0xFF5FC8FF;
    private static final int WARNING = 0xFFFFC857;

    public RailgunScreen(RailgunMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 222;
        inventoryLabelY = 129;
    }

    @Override
    protected void init() {
        super.init();
        addRenderableWidget(button(RailgunMenu.BUTTON_PREVIOUS, "screen.advancedrocketrycommunity.railgun.previous",
                8, 76, 16));
        addRenderableWidget(button(RailgunMenu.BUTTON_NEXT, "screen.advancedrocketrycommunity.railgun.next",
                26, 76, 16));
        addRenderableWidget(button(RailgunMenu.BUTTON_LAUNCH, "screen.advancedrocketrycommunity.railgun.launch",
                44, 76, 44));
        addRenderableWidget(button(RailgunMenu.BUTTON_AUTO, "screen.advancedrocketrycommunity.railgun.auto",
                90, 76, 36));
        addRenderableWidget(button(RailgunMenu.BUTTON_REDSTONE, "screen.advancedrocketrycommunity.railgun.redstone",
                128, 76, 40));
        addRenderableWidget(button(RailgunMenu.BUTTON_MIN_DOWN_16,
                "screen.advancedrocketrycommunity.railgun.min_down_16",
                8, 92, 38));
        addRenderableWidget(button(RailgunMenu.BUTTON_MIN_DOWN_1, "screen.advancedrocketrycommunity.railgun.min_down_1",
                48, 92, 38));
        addRenderableWidget(button(RailgunMenu.BUTTON_MIN_UP_1, "screen.advancedrocketrycommunity.railgun.min_up_1",
                88, 92, 38));
        addRenderableWidget(button(RailgunMenu.BUTTON_MIN_UP_16, "screen.advancedrocketrycommunity.railgun.min_up_16",
                128, 92, 40));
    }

    private Button button(int id, String key, int x, int y, int width) {
        return Button.builder(Component.translatable(key),
                ignored -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                    }
                }).bounds(leftPos + x, topPos + y, width, 14).build();
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
        if (isHovering(152, 18, 16, 50, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("tooltip.advancedrocketrycommunity.energy",
                    menu.energyStored(), RailgunStorage.ENERGY_CAPACITY), mouseX, mouseY);
        }
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isPresent() && isHovering(8, 108, 160, 20, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>();
            for (EndgameDeviceView.Line line : view.get().lines()) {
                Component value = line.translated() ? Component.translatable(line.value())
                        : Component.literal(line.value());
                lines.add(Component.translatable(line.label()).append(": ").append(value));
            }
            if (!lines.isEmpty()) {
                graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
        graphics.renderOutline(x, y, imageWidth, imageHeight, EDGE);
        graphics.fill(x + 152, y + 18, x + 168, y + 68, RECESS);
        int height = (int) Math.min(50L, (long) menu.energyStored() * 50L / RailgunStorage.ENERGY_CAPACITY);
        graphics.fill(x + 152, y + 68 - height, x + 168, y + 68, CHARGE);
        for (int slot = 0; slot < RailgunStorage.INPUT_SLOTS; slot++) {
            slot(graphics, x + 7 + slot * 18, y + 17);
        }
        for (int slot = 0; slot < RailgunStorage.RECEIVE_SLOTS; slot++) {
            slot(graphics, x + 7 + slot * 18, y + 53);
        }
        for (int row = 0; row < 4; row++) {
            int rowY = y + (row < 3 ? 139 + row * 18 : 197);
            for (int column = 0; column < 9; column++) {
                graphics.fill(x + 7 + column * 18, rowY, x + 25 + column * 18, rowY + 18, RECESS);
            }
        }
    }

    private static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, RECESS);
        graphics.renderOutline(x, y, 18, 18, 0xFF2E4A60);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.railgun.received"), 8, 44,
                MUTED, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        Optional<EndgameDeviceView> view = EndgameDeviceViewCache.view(menu.containerId);
        if (view.isEmpty()) {
            return;
        }
        EndgameCode status = view.get().status();
        graphics.drawString(font, statusLine(status), 8, 110, status == EndgameCode.OK ? CHARGE : WARNING, false);
        if (view.get().lastStop() != EndgameCode.OK && view.get().lastStop() != status) {
            graphics.drawString(font, Component.translatable("screen.advancedrocketrycommunity.railgun.last",
                    statusLine(view.get().lastStop())), 8, 120, MUTED, false);
        }
    }

    /** The translated status with its stable code, never colour alone (ADR-054 section 4). */
    private static Component statusLine(EndgameCode code) {
        return Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY, Component.translatable(code.translationKey()),
                code.name());
    }
}
